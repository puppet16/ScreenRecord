package cn.luck.screenrecord.record

import android.content.Context
import android.util.Log
import cn.luck.screenrecord.record.utils.FileUtils
import cn.luck.screenrecord.util.LogUtil
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/14
 * desc    录屏文件管理
 * ============================================================
 **/
class RecordFileManager(context: Context) {
    private var RECORD_ROOT_DIR = ""
    private var journeyId: String = ""

    // 视频段的索引
    private var segmentIndex: Int = 0

    private var nextFile: File? = null

    init {
        RECORD_ROOT_DIR = context.filesDir.absolutePath + "/ScreenRecordings"
    }

    companion object {
        private const val TAG = "RecordFileManager"

        // 每段视频的最大文件大小，单位为字节
        const val SEGMENT_MAX_SIZE_BYTES: Long = 2 * 1024 * 1024
    }

    fun setJourneyId(journeyId: String) {
        this.journeyId = journeyId
    }

    fun getNextOutputFile(): File {
        val result: File?
        if (segmentIndex == 0) {
            result = File(getOutputFilePath())
            segmentIndex++
            nextFile = File(getOutputFilePath())
        } else {
            segmentIndex++
            result = nextFile
            nextFile = File(getOutputFilePath())
        }
        LogUtil.d(TAG, "getNextOutputFile() $result")
        return result!!
    }

    /**
     *
     * @param segmentIndex String 切片文件的下标
     * @param journeyId String 课程唯一标识
     */
    private fun getOutputFilePath(): String {
        // 确保根目录存在
        FileUtils.modifyDir(RECORD_ROOT_DIR)
        val courseDir = "$RECORD_ROOT_DIR/$journeyId"
        // 确保当前课程的目录存在
        // 如果是第一个文件，则把之前的都删除了
        FileUtils.modifyDir(courseDir, segmentIndex == 0)
        // 切片文件完整地址
        val segmentFilePath = courseDir + "/${journeyId}_$segmentIndex.mp4"

        val segmentFile = File(segmentFilePath)
        if (!segmentFile.exists()) {
            segmentFile.createNewFile()
        }
        LogUtil.d(TAG, "当前切片文件路径：$segmentFilePath")
        return segmentFilePath
    }

    // 检查当前文件大小是否超过阈值
    fun moreThanFileSize(filePath: String, maxSize: Long = SEGMENT_MAX_SIZE_BYTES): Boolean {
        val file = File(filePath)
        return file.exists() && file.length() >= maxSize
    }


}