package com.nixy.markcam

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var panel: ScrollView
    private lateinit var settings: SettingsStore

    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var lastLoc: Location? = null
    private var lastGeoLoc: Location? = null
    @Volatile private var address: String = ""

    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { refreshOverlay(); handler.postDelayed(this, 1000) }
    }

    private val locCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onLocation(it) }
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res[Manifest.permission.CAMERA] == true) startCamera()
        else toast("카메라 권한이 필요해")
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) startLocation()
        else toast("위치 권한이 없어서 좌표/주소가 안 나와")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsStore(this)
        previewView = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        panel = findViewById(R.id.panel)

        findViewById<View>(R.id.shutter).setOnClickListener { takePhoto() }
        findViewById<View>(R.id.btnFlip).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener { togglePanel(true) }
        findViewById<View>(R.id.btnClose).setOnClickListener { togglePanel(false) }

        setupPanel()
        applySettingsToOverlay()

        val needed = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (needed.all { granted(it) }) { startCamera(); startLocation() }
        else permLauncher.launch(needed)
    }

    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(ticker) }
    override fun onDestroy() {
        super.onDestroy()
        fused.removeLocationUpdates(locCallback)
        io.shutdown()
    }

    override fun onBackPressed() {
        if (panel.visibility == View.VISIBLE) togglePanel(false) else super.onBackPressed()
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    // ---------------- 설정 패널 ----------------
    private fun togglePanel(show: Boolean) {
        panel.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupPanel() {
        val memo = findViewById<EditText>(R.id.memo)
        val cbDate = findViewById<CheckBox>(R.id.cbDate)
        val cbAddr = findViewById<CheckBox>(R.id.cbAddr)
        val cbCoord = findViewById<CheckBox>(R.id.cbCoord)
        val sb = findViewById<SeekBar>(R.id.sbSize)

        memo.setText(settings.memo)
        cbDate.isChecked = settings.showDate
        cbAddr.isChecked = settings.showAddress
        cbCoord.isChecked = settings.showCoord
        sb.progress = settings.sizeProgress

        memo.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                settings.memo = s?.toString() ?: ""; applySettingsToOverlay()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        cbDate.setOnCheckedChangeListener { _, v -> settings.showDate = v; applySettingsToOverlay() }
        cbAddr.setOnCheckedChangeListener { _, v -> settings.showAddress = v; applySettingsToOverlay() }
        cbCoord.setOnCheckedChangeListener { _, v -> settings.showCoord = v; applySettingsToOverlay() }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, p: Int, fromUser: Boolean) {
                settings.sizeProgress = p; applySettingsToOverlay()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        buildAccentRow()
        buildPositionRow()
    }

    private fun buildAccentRow() {
        val row = findViewById<LinearLayout>(R.id.accentRow)
        row.removeAllViews()
        SettingsStore.ACCENTS.forEach { color ->
            val size = dp(34f)
            val v = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = dp(10f)
            v.layoutParams = lp
            styleSwatch(v, color, color == settings.accent)
            v.setOnClickListener {
                settings.accent = color
                applySettingsToOverlay()
                for (i in 0 until row.childCount) {
                    styleSwatch(row.getChildAt(i), SettingsStore.ACCENTS[i],
                        SettingsStore.ACCENTS[i] == color)
                }
            }
            row.addView(v)
        }
    }

    private fun styleSwatch(v: View, color: Int, selected: Boolean) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        d.setStroke(dp(if (selected) 3f else 1f),
            if (selected) Color.WHITE else 0x66FFFFFF)
        v.background = d
    }

    private fun buildPositionRow() {
        val row = findViewById<LinearLayout>(R.id.posRow)
        row.removeAllViews()
        SettingsStore.POS_NAMES.forEachIndexed { i, name ->
            val t = TextView(this)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = if (i < 3) dp(8f) else 0
            t.layoutParams = lp
            t.text = name
            t.gravity = android.view.Gravity.CENTER
            t.setPadding(0, dp(12f), 0, dp(12f))
            t.setTextColor(Color.WHITE)
            stylePos(t, i == settings.position)
            t.setOnClickListener {
                settings.position = i
                applySettingsToOverlay()
                for (j in 0 until row.childCount) stylePos(row.getChildAt(j) as TextView, j == i)
            }
            row.addView(t)
        }
    }

    private fun stylePos(t: TextView, selected: Boolean) {
        val d = GradientDrawable()
        d.cornerRadius = dp(30f).toFloat()
        d.setColor(if (selected) settings.accent and 0x55FFFFFF.toInt() or 0x55000000 else 0x33000000)
        d.setStroke(dp(if (selected) 2f else 1f),
            if (selected) settings.accent else 0x44FFFFFF)
        t.background = d
    }

    private fun applySettingsToOverlay() {
        overlay.accent = settings.accent
        overlay.scale = settings.textScale
        overlay.position = settings.position
        refreshOverlay()
    }

    // ---------------- 카메라 ----------------
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, imageCapture)
            } catch (e: Exception) {
                toast("카메라 시작 실패: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ---------------- 위치 / 주소 ----------------
    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L).build()
        fused.requestLocationUpdates(req, locCallback, Looper.getMainLooper())
    }

    private fun onLocation(loc: Location) {
        lastLoc = loc
        val prev = lastGeoLoc
        if (prev == null || loc.distanceTo(prev) > 20f) {
            lastGeoLoc = loc
            io.execute { reverse(loc) }
        }
    }

    @Suppress("DEPRECATION")
    private fun reverse(loc: Location) {
        try {
            val list = Geocoder(this, Locale.KOREA).getFromLocation(loc.latitude, loc.longitude, 1)
            val line = list?.firstOrNull()?.getAddressLine(0)
            if (line != null) address = line.removePrefix("대한민국").trim()
        } catch (e: Exception) {
            address = "(주소 조회 실패)"
            lastGeoLoc = null
        }
    }

    // ---------------- 오버레이 라인 ----------------
    private fun buildLines(): List<OverlayRenderer.Line> {
        val out = ArrayList<OverlayRenderer.Line>()
        if (settings.memo.isNotBlank())
            out.add(OverlayRenderer.Line(settings.memo, true))
        if (settings.showDate)
            out.add(OverlayRenderer.Line(
                SimpleDateFormat("yyyy년MM월dd일 HH:mm EEEE", Locale.KOREA).format(Date()), false))
        if (settings.showAddress && address.isNotBlank())
            out.add(OverlayRenderer.Line(address, false))
        if (settings.showCoord) {
            val l = lastLoc
            val c = if (l == null) "위치 확인 중..." else String.format(
                Locale.US, "%.6f°%s, %.6f°%s",
                abs(l.latitude), if (l.latitude >= 0) "N" else "S",
                abs(l.longitude), if (l.longitude >= 0) "E" else "W")
            out.add(OverlayRenderer.Line(c, false))
        }
        return out
    }

    private fun refreshOverlay() {
        overlay.lines = buildLines()
        overlay.invalidate()
    }

    // ---------------- 촬영 / 합성 / 저장 ----------------
    private fun takePhoto() {
        val ic = imageCapture ?: return
        val lines = buildLines()               // 셔터 순간의 값 고정
        val accent = settings.accent
        val scale = settings.textScale
        val position = settings.position
        ic.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val src = image.toBitmap()
                    image.close()
                    io.execute {
                        try {
                            val out = compose(src, rotation, lines, accent, scale, position)
                            save(out)
                            runOnUiThread { toast("저장됨 · Pictures/MarkCam") }
                        } catch (e: Exception) {
                            runOnUiThread { toast("저장 실패: ${e.message}") }
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    toast("촬영 실패: ${exception.message}")
                }
            }
        )
    }

    private fun compose(
        src: Bitmap, rotation: Int, lines: List<OverlayRenderer.Line>,
        accent: Int, scale: Float, position: Int
    ): Bitmap {
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val bmp = rotated.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(bmp)
        OverlayRenderer.draw(c, bmp.width.toFloat(), bmp.height.toFloat(),
            lines, accent, scale, position)
        return bmp
    }

    private fun save(bmp: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "mark_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MarkCam")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert 실패")
        contentResolver.openOutputStream(uri)?.use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
    }
}
