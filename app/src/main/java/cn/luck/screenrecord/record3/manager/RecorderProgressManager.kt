package cn.luck.screenrecord.record3.manager

import android.os.SystemClock

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/19
 * desc    录制进度管理
 * ============================================================
 **/
class RecorderProgressManager {

    private var startTime: Long = 0L

    /**
     * 设置开始时间，在录制开始时调用，时间单位 微秒
     */
    fun setStartTimeMs() {
        startTime = getCurrentTimeMs()
    }

    /**
     * 获取当前时间戳 单位微秒
     * @return Long
     */
    private fun getCurrentTimeMs(): Long {
        return (SystemClock.elapsedRealtimeNanos() / 1000F).toLong()
    }

    /**
     * 返回毫秒级录制进度
     * @return Long
     */
    fun getProgressTime(): Long {
        return getTimeMs(getCurrentTimeMs() - startTime)
    }

    /**
     * 停止录制
     */
    fun stop() {
        startTime = 0
    }


    /**
     * 将微秒转为毫秒
     * @param timeMs Long
     * @return Long
     */
    private fun getTimeMs(timeMs: Long): Long {
        return (timeMs / 1000F).toLong()
    }

}