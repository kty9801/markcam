package com.nixy.markcam

import android.content.Context

/** 사용자 커스텀 설정을 SharedPreferences에 저장/로드 */
class SettingsStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("markcam", Context.MODE_PRIVATE)

    var memo: String
        get() = p.getString("memo", "") ?: ""
        set(v) { p.edit().putString("memo", v).apply() }

    var showDate: Boolean
        get() = p.getBoolean("showDate", true)
        set(v) { p.edit().putBoolean("showDate", v).apply() }

    var showAddress: Boolean
        get() = p.getBoolean("showAddress", true)
        set(v) { p.edit().putBoolean("showAddress", v).apply() }

    var showCoord: Boolean
        get() = p.getBoolean("showCoord", true)
        set(v) { p.edit().putBoolean("showCoord", v).apply() }

    /** 0..100 → scale 0.6..1.8 */
    var sizeProgress: Int
        get() = p.getInt("sizeProgress", 33)
        set(v) { p.edit().putInt("sizeProgress", v).apply() }

    val textScale: Float get() = 0.6f + sizeProgress / 100f * 1.2f

    var accent: Int
        get() = p.getInt("accent", ACCENTS[0])
        set(v) { p.edit().putInt("accent", v).apply() }

    /** 0 좌하단, 1 우하단, 2 좌상단, 3 우상단 */
    var position: Int
        get() = p.getInt("position", 0)
        set(v) { p.edit().putInt("position", v).apply() }

    companion object {
        val ACCENTS = intArrayOf(
            0xFFF5B800.toInt(), // amber
            0xFFE53935.toInt(), // red
            0xFF43A047.toInt(), // green
            0xFF1E88E5.toInt(), // blue
            0xFF00ACC1.toInt(), // cyan
            0xFFD81B60.toInt(), // magenta
            0xFFFFFFFF.toInt()  // white
        )
        val POS_NAMES = arrayOf("좌하", "우하", "좌상", "우상")
    }
}
