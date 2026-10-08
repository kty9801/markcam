package com.nixy.markcam

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/** 카메라 미리보기 위에 오버레이를 실시간으로 그리는 뷰 */
class OverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var lines: List<OverlayRenderer.Line> = emptyList()
    var accent: Int = SettingsStore.ACCENTS[0]
    var scale: Float = 1f
    var position: Int = 0

    // 미리보기에서만 상단 버튼/하단 셔터 영역을 피해 오버레이를 배치 (사진 저장본엔 미적용)
    private val density = resources.displayMetrics.density
    private val insetTop = 78f * density
    private val insetBottom = 150f * density

    override fun onDraw(canvas: Canvas) {
        OverlayRenderer.draw(
            canvas, width.toFloat(), height.toFloat(),
            lines, accent, scale, position, insetTop, insetBottom
        )
    }
}
