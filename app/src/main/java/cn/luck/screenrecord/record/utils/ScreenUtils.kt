package cn.luck.screenrecord.record.utils

import android.content.Context
import android.util.DisplayMetrics
class ScreenUtils {
    companion object {

        fun getScreenDPI(context: Context): Int {
            return context.resources.displayMetrics.densityDpi
        }

    }
}