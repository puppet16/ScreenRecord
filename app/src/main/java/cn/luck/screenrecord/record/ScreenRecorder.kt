package cn.luck.screenrecord.record

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException

class ScreenRecorder(context: Context) {

    // 用于获取屏幕录制权限和创建 MediaProjection 对象
    private var mediaProjectionManager: MediaProjectionManager? = null
    // 记录屏幕内容的 MediaProjection 对象
    private var mediaProjection: MediaProjection? = null
    // 用于录制音频和视频的 MediaRecorder 对象
    private var mediaRecorder: MediaRecorder? = null
    // 用于创建虚拟显示器的 VirtualDisplay 对象
    private var virtualDisplay: VirtualDisplay? = null

    // 用于定时切换分段的 Handler
    private val handler = Handler(Looper.getMainLooper())
    // 视频段的索引
    private var segmentIndex = 0
    // 每段视频的持续时间，单位为毫秒
    private val segmentDuration = 60_000L // 60秒

    init {
        // 获取 MediaProjectionManager 实例，用于屏幕录制
        mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // 开始录制屏幕
    fun startRecording(resultCode: Int, data: Intent) {
        // 获取 MediaProjection 对象，用于捕获屏幕内容
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)

        // 启动第一个视频段的录制
        startNewSegment()

        // 使用 Handler 定时任务切换录制段
        handler.postDelayed(object : Runnable {
            override fun run() {
                // 停止当前录制段
                stopRecording()
                // 更新视频段索引
                segmentIndex++
                // 启动新的录制段
                startNewSegment()
                // 继续定时任务
                handler.postDelayed(this, segmentDuration)
            }
        }, segmentDuration)
    }

    // 开始新的录制段
    private fun startNewSegment() {
        mediaRecorder = MediaRecorder().apply {
            // 设置音频源为麦克风
            setAudioSource(MediaRecorder.AudioSource.MIC)
            // 设置视频源为 Surface
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            // 设置输出格式为 MP4
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            // 设置音频编码器为 AAC
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // 设置视频编码器为 H.264
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            // 设置视频分辨率为 1280x720
            setVideoSize(1280, 720)
            // 设置帧率为 30 帧/秒
            setVideoFrameRate(30)
            // 设置视频比特率为 5 Mbps
            setVideoEncodingBitRate(5 * 1024 * 1024)
            // 设置输出文件路径
            setOutputFile(getOutputFilePath())
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
            1280, 720, // 虚拟显示器的宽度和高度
            320, // DPI（像素密度）
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, // 自动镜像标志
            mediaRecorder?.surface, // 将录制数据输出到 MediaRecorder 的 Surface
            null, null // 回调接口（这里设置为 null）
        )
    }

    // 获取输出文件路径
    private fun getOutputFilePath(): String {
        // 获取应用的外部文件目录路径
        val directory = Environment.getExternalStorageDirectory().absolutePath + "/ScreenRecordings"
        val file = File(directory)
        if (!file.exists()) {
            file.mkdirs() // 如果目录不存在，则创建目录
        }
        // 返回视频段的输出文件路径
        return "$directory/segment_$segmentIndex.mp4"
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
        handler.removeCallbacksAndMessages(null) // 停止所有定时任务
        stopRecording() // 停止录制
        mediaProjection?.stop() // 停止 MediaProjection
    }
}
