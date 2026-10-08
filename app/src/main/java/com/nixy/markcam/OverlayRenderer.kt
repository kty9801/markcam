package com.nixy.markcam

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * 미리보기(OverlayView)와 촬영 사진(Canvas) 양쪽에서 동일하게 쓰는 오버레이 렌더러.
 * 화면/사진 크기에 비례해서 그리므로 미리보기와 저장 결과가 일치한다.
 */
object OverlayRenderer {

    data class Line(val text: String, val accent: Boolean)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8C000000.toInt() }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        c: Canvas, w: Float, h: Float,
        lines: List<Line>, accent: Int, scale: Float, position: Int
    ) {
        if (lines.isEmpty() || w <= 0f || h <= 0f) return

        val fs = min(w, h) * 0.036f * scale
        val pad = fs * 0.55f
        val lh = fs * 1.5f
        val barW = fs * 0.16f
        val gap = fs * 0.5f

        textPaint.textSize = fs
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textPaint.setShadowLayer(fs * 0.12f, 0f, fs * 0.05f, Color.BLACK)

        var maxW = 0f
        for (l in lines) maxW = max(maxW, textPaint.measureText(l.text))

        val blockW = pad + barW + gap + maxW + pad
        val blockH = pad * 2 + lh * lines.size
        val margin = min(w, h) * 0.045f

        val left: Float
        val top: Float
        when (position) {
            1 -> { left = w - margin - blockW; top = h - margin - blockH }   // 우하
            2 -> { left = margin;              top = margin }                 // 좌상
            3 -> { left = w - margin - blockW; top = margin }                 // 우상
            else -> { left = margin;           top = h - margin - blockH }    // 좌하
        }

        val r = fs * 0.28f
        c.drawRoundRect(RectF(left, top, left + blockW, top + blockH), r, r, bgPaint)

        barPaint.color = accent
        val barLeft = left + pad
        c.drawRoundRect(
            RectF(barLeft, top + pad, barLeft + barW, top + blockH - pad),
            barW / 2, barW / 2, barPaint
        )

        val textX = barLeft + barW + gap
        lines.forEachIndexed { i, l ->
            textPaint.color = if (l.accent) accent else Color.WHITE
            c.drawText(l.text, textX, top + pad + fs + lh * i, textPaint)
        }
    }
}
