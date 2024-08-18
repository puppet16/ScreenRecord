package cn.luck.screenrecord.record3

import cn.luck.screenrecord.record3.utils.HandlerTimer
import cn.luck.screenrecord.record3.utils.RecordFileManager

class FileSizeSpliceChecker(private val fileManager: RecordFileManager) {


    private val timer by lazy { HandlerTimer() }
    fun checkSplice(callback: (Boolean) -> Unit) {
        timer.startTimer {
            if (fileManager.moreThanFileSize()) {
                callback.invoke(true)
            } else {
                callback.invoke(false)
            }
        }
    }

    fun stopCheck() {
        timer.stopTimer()
    }


}