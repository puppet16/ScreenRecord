package cn.xdf.screenrecord.util

import android.util.Log

/**
 * ============================================================
 *
 * @author ltt
 * date    2024/3/11
 * desc    日志打印工具
 * ============================================================
 **/

internal object LogUtil {

    private var ENABLE_LOG: Boolean = true

    private const val TAG = "Splice"
    private const val TAG_DEFAULT = "LOG"
    @JvmStatic
    fun setLogEnable(enable: Boolean) {
        ENABLE_LOG = enable
    }

    @JvmStatic
    @JvmOverloads
    fun d(tag: String = TAG_DEFAULT, msg: String) {
        if (ENABLE_LOG) {
            Log.d("$TAG::$tag", msg)
        }
    }
    @JvmStatic
    @JvmOverloads
    fun i(tag: String = TAG_DEFAULT, msg: String) {
        if (ENABLE_LOG) {
            Log.i("$TAG::$tag", msg)
        }
    }


    @JvmStatic
    @JvmOverloads
    fun e(tag: String = TAG_DEFAULT, msg: String) {
        if (ENABLE_LOG) {
            Log.e("$TAG::$tag", msg)
        }
    }
}