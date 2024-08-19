package cn.luck.screenrecord.record3.splicechecker

import cn.luck.screenrecord.record3.utils.HandlerTimer
import cn.luck.screenrecord.record3.manager.RecordFileManager

class FileSizeSpliceChecker(private val fileManager: RecordFileManager) : ISpliceChecker {


    private val timer by lazy { HandlerTimer() }
    override fun checkSplice(callback: (Boolean) -> Unit) {
        timer.startTimer {
            if (fileManager.moreThanFileSize()) {
                callback.invoke(true)
            } else {
                callback.invoke(false)
            }
        }
    }

    override fun stopCheck() {
        timer.stopTimer()
    }


}