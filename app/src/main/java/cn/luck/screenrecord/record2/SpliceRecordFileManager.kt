package cn.luck.screenrecord.record2

import android.content.Context
import android.util.Log
import cn.luck.screenrecord.utils.LogUtil
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
class SpliceRecordFileManager(context: Context) {
    private var RECORD_ROOT_DIR = ""
    private var journeyId: String = ""

    // 视频段的索引
    private var segmentIndex: Int = 0

    private var nextFile: File? = null

    init {
        RECORD_ROOT_DIR = context.filesDir.absolutePath + "/ScreenRecordings"
    }

    companion object {
        private const val TAG = "RecordFileUtil"

        // 每段视频的最大文件大小，单位为字节
        const val SEGMENT_MAX_SIZE_BYTES: Long = 1 * 1024 * 1024
    }

    fun setJourneyId(journeyId: String) {
        this.journeyId = journeyId
    }

    fun getNextOutputFile(): File {
        segmentIndex++
        val result = nextFile
        nextFile = File(getOutputFilePath(segmentIndex))
        LogUtil.d(TAG, "getNextOutputFile() ${result ?: nextFile!!}")
        return result ?: nextFile!!
    }

    /**
     *
     * @param segmentIndex String 切片文件的下标
     * @param journeyId String 课程唯一标识
     */
    fun getOutputFilePath(segmentIndex: Int): String {
        // 确保根目录存在
        modifyDir(RECORD_ROOT_DIR)
        val courseDir = "$RECORD_ROOT_DIR/$journeyId"
        // 确保当前课程的目录存在
        // 如果是第一个文件，则把之前的都删除了
        modifyDir(courseDir, segmentIndex == 0)
        // 切片文件完整地址
        val segmentFilePath = courseDir + "/${journeyId}_$segmentIndex.mp4"

        val segmentFile = File(segmentFilePath)
        if (!segmentFile.exists()) {
            segmentFile.createNewFile()
        }
        LogUtil.d(TAG, "当前切片文件路径：$segmentFilePath, ${segmentFile.absolutePath}, ${segmentFile.path}")
        return segmentFilePath
    }

    // 检查当前文件大小是否超过阈值
    fun moreThanFileSize(filePath: String, maxSize: Long = SEGMENT_MAX_SIZE_BYTES): Boolean {
        val file = File(filePath)
        return file.exists() && file.length() >= maxSize
    }

    /**
     * 获取文件大小
     * @param filePath String
     * @return String
     */
    fun getFileSize(filePath: String): String {
        val sizeInBytes = File(filePath).length()
        if (sizeInBytes <= 0) {
            return "0 B"
        }
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(sizeInBytes.toDouble()) / log10(1024.0)).toInt()

        val sizeFormatted =
            DecimalFormat("#,##0.#").format(sizeInBytes / 1024.0.pow(digitGroups.toDouble()))
        return "$sizeFormatted ${units[digitGroups]}"
    }

    private fun modifyDir(dirPath: String, deleteExit: Boolean = false) {
        val rootDir = File(dirPath)
        //已有根文件夹
        if (rootDir.exists() && rootDir.isDirectory) {
            Log.i(TAG, "目录已存在:$dirPath, deleteExit=$deleteExit")
            if (deleteExit) {
                rootDir.deleteOnExit()
                rootDir.mkdirs()
            }
        } else {
            rootDir.mkdirs()
        }
    }


}