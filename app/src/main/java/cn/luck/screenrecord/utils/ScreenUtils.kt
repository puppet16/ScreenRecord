package cn.luck.screenrecord.utils

import android.content.Context

class ScreenUtils {
    companion object {

        fun getScreenDPI(context: Context): Int {
            return context.resources.displayMetrics.densityDpi
        }

    }
}