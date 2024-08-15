package cn.luck.screenrecord.record.utils

import android.util.Log
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

class FileUtils {
    companion object {
        private const val TAG = "FileUtils"

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

        /**
         * 创建目录
         * 可配置是否要删除已存在目录，并创建一个新的
         *
         * @param dirPath
         * @param deleteExit
         */
        fun modifyDir(dirPath: String, deleteExit: Boolean = false) {
            val dirFile = File(dirPath)
            //已有根文件夹
            if (dirFile.exists() && dirFile.isDirectory) {
                Log.i(TAG, "目录已存在:$dirPath, deleteExit=$deleteExit")
                if (deleteExit) {
                    deleteDirectory(dirFile)
                    dirFile.mkdirs()
                }
            } else {
                dirFile.mkdirs()
            }
        }




        /**
         * Delete directory
         *
         * @param directoryPath
         * @return
         */
        fun deleteDirectory(directoryPath: String): Boolean {
            val directoryFile = File(directoryPath)
            return deleteDirectory(directoryFile)
        }


        /**
         *
         * 删除目录及目录下子文件
         * @param directory
         * @return
         */
        private fun deleteDirectory(directory: File): Boolean {
            if (directory.exists()) {
                // 获取文件夹下的所有文件和子文件夹
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        // 如果是文件，则删除
                        if (file.isFile) {
                            file.delete()
                        }
                        // 如果是文件夹，则递归删除
                        else if (file.isDirectory) {
                            deleteDirectory(file)
                        }
                    }
                }
            }
            // 删除空的文件夹
            return directory.delete()
        }
    }
}