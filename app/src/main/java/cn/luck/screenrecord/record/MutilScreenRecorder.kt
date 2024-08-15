package cn.luck.screenrecord.record

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaPlayer.OnInfoListener
import android.media.MediaRecorder
import android.media.MediaRecorder.OnErrorListener
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import cn.luck.screenrecord.util.LogUtil
import java.io.File
import java.io.IOException

class MutilScreenRecorder(context: Context) {

    // 用于获取屏幕录制权限和创建 MediaProjection 对象
    private var mediaProjectionManager: MediaProjectionManager? = null

    // 记录屏幕内容的 MediaProjection 对象
    private var mediaProjection: MediaProjection? = null

    // 用于录制音频和视频的 MediaRecorder 对象
    private var mediaRecorder: MediaRecorder? = null

    // 用于创建虚拟显示器的 VirtualDisplay 对象
    private var virtualDisplay: VirtualDisplay? = null


    private var fileUtil = RecordFileUtil(context)



    companion object {
        private const val TAG = "ScreenRecorder"
        private const val VIDEO_BIT_RATE = 6000000 // 视频比特率
        private const val VIDEO_FRAME_RATE = 30 // 视频帧率
        private const val AUDIO_BIT_RATE = 96000 // 音频比特率
        private const val AUDIO_SAMPLE_RATE = 44100 // 音频采样率

        private const val DISPLAY_WIDTH = 1920 // 显示宽度
        private const val DISPLAY_HEIGHT = 1080 // 显示高度
    }

    init {
        // 获取 MediaProjectionManager 实例，用于屏幕录制
        mediaProjectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // 开始录制屏幕
    fun startRecording(resultCode: Int, data: Intent, journeyId: String) {
        // 获取 MediaProjection 对象，用于捕获屏幕内容
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        fileUtil.setJourneyId(journeyId)

        // 启动第一个视频段的录制
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startNewSegment()
        } else {
            LogUtil.e(TAG, "Android 系统不支持该录屏方式，请升级Android系统到9.0及以上")
        }

    }

    // 开始新的录制段
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startNewSegment() {
        mediaRecorder = MediaRecorder().apply {
            // 设置音频源为麦克风
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            setAudioEncodingBitRate(AUDIO_BIT_RATE)
            // 设置视频源为 Surface
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            // 设置输出格式为 MP4
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_2_TS)
            // 设置音频编码器为 AAC
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // 设置视频编码器为 H.264
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            // 设置视频分辨率为 1280x720
            setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            // 设置帧率为 30 帧/秒
            setVideoFrameRate(VIDEO_FRAME_RATE)
            // 设置视频比特率为 5 Mbps
            setVideoEncodingBitRate(VIDEO_BIT_RATE)
            setMaxFileSize(RecordFileUtil.SEGMENT_MAX_SIZE_BYTES)
            // 设置输出文件路径
            setOutputFile(fileUtil.getOutputFilePath(0))

            setOnInfoListener { mr, what, extra ->
                LogUtil.d(TAG, "onInfo() what=$what, extra=$extra")
                when (what) {
                    MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING -> {
                        LogUtil.d(TAG, "当文件大小接近最大值时，准备切换到下一个文件")

                        // 当文件大小接近最大值时，准备下一个文件
                        try {
                            mr.setNextOutputFile(fileUtil.getNextOutputFile())
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }

                    MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
                        // 当前文件已达到最大大小，切换到下一个文件
                        // 当新文件开始使用时会收到 MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED 回调
                        LogUtil.d(TAG, "当前文件已达到最大大小，切换到下一个文件")

                    }

                    MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED -> {
                        // 已切换到下一个文件
                        LogUtil.d(TAG, "Switched to next output file")
                    }
                }
            }

            setOnErrorListener { mr, what, extra ->
                LogUtil.e(
                    TAG,
                    "录屏报错：what=$what, extra=$extra"
                )
            }
            try {
                // 准备 MediaRecorder
                prepare()
            } catch (e: IOException) {
                e.printStackTrace() // 捕获并打印异常
            }
        }

        // 开始录制
        mediaRecorder?.start()

        // 创建虚拟显示器
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder", // 虚拟显示器的名称
            DISPLAY_WIDTH, DISPLAY_HEIGHT, // 虚拟显示器的宽度和高度
            320, // DPI（像素密度）
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, // 自动镜像标志
            mediaRecorder?.surface, // 将录制数据输出到 MediaRecorder 的 Surface
            null, null // 回调接口（这里设置为 null）
        )
    }


    // 停止录制
    fun stopRecording() {
        mediaRecorder?.apply {
            stop() // 停止录制
            reset() // 重置 MediaRecorder
            release() // 释放 MediaRecorder 资源
        }
        // 释放虚拟显示器资源
        virtualDisplay?.release()
    }

    // 停止所有录制并释放资源
    fun release() {
        stopRecording() // 停止录制
        mediaProjection?.stop() // 停止 MediaProjection
    }
}
