package cn.luck.screenrecord.record3.utils

import android.os.Handler
import android.os.Looper

class HandlerTimer {
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var isRunning = false

    // 启动定时器，定时发送事件
    fun startTimer(interval: Long = 1000L, onTick: () -> Unit) {
        if (isRunning) return

        isRunning = true
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    onTick()
                    handler?.postDelayed(this, interval)
                }
            }
        }
        handler?.postDelayed(runnable!!, interval)
    }

    // 停止定时器
    fun stopTimer() {
        handler?.removeCallbacks(runnable!!)
        isRunning = false
    }

    /**
     * 设置定时器状态
     *  设置为false 停止定时器，
     */
    var timerRunning: Boolean
        get() = isRunning
        set(value) {
            if (!value) {
                stopTimer()
            }
        }
}