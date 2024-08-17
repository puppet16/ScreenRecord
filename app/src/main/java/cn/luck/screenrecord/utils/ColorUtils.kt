package cn.luck.screenrecord.utils

import kotlin.random.Random

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/17
 * desc    描述
 * ============================================================
 **/
class ColorUtils {
    companion object {

        fun getComplementaryColor(color: Int): Int {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color, hsv)

            // 计算对比色的色调 (hue)
            hsv[0] = (hsv[0] + 180) % 360

            // 返回对比色
            return android.graphics.Color.HSVToColor(hsv)
        }

        /**
         * 随机一个颜色，不含黑色和灰色
         * @return Int
         */
        fun generateRandomColor(): Int {
            val hue = Random.nextInt(0, 360) // 色调范围 0-360
            val saturation = Random.nextFloat() * 0.5f + 0.5f // 饱和度范围 0.5-1.0，避免灰色
            val lightness = Random.nextFloat() * 0.4f + 0.6f // 亮度范围 0.6-1.0，避免黑色

            return android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation, lightness))
        }
    }
}