package cn.luck.screenrecord.utils

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
         * 获取文件列表， 文件大小必须大于0
         * @param dirPath String
         * @return List<String>
         */
        fun getFileListByDirPath(dirPath: String): List<String> {
            LogUtil.d(TAG, "getFileListByDirPath() dirPath=$dirPath")
            if (dirPath.isEmpty()) return emptyList()
            // 根据传入的路径创建 File 对象
            val directory = File(dirPath)

            // 检查目录是否存在并且是一个目录
            if (directory.exists() && directory.isDirectory) {
                // 获取目录下的所有文件和子目录，并过滤出文件
                val filesList = directory.listFiles()?.filter { it.isFile && it.length() > 0 }
                    ?: emptyList()

                // 根据文件名中的序号进行升序排序
                val sortFileList = filesList.sortedBy { file ->
                    // 提取文件名中的序号（假设文件名格式为 "file_1.txt"）
                    val fileName = file.name
                    val number =
                        fileName.substringAfter('_').substringBefore('.').toIntOrNull() ?: 0
                    number
                }.map { it.absolutePath }
                LogUtil.d(TAG, "获取文件列表：并按序号升序排列：$sortFileList")
                return sortFileList
            } else {
                // 如果目录不存在或不是目录，返回空列表
                return emptyList()
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