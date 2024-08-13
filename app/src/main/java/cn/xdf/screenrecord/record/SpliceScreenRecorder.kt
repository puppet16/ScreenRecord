package cn.xdf.screenrecord.record

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.view.Surface
import java.io.File
import java.io.IOException

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/13
 * desc    使用 MediaCodec 和 MediaMuxer 实现屏幕录制和音频合成
 * ============================================================
 **/
class SpliceScreenRecorder(context: Context) {
    private val SEGMENT_DURATION_MS = 10000L // 每段视频的时长，单位为毫秒
    private val VIDEO_BIT_RATE = 6000000 // 视频比特率
    private val VIDEO_FRAME_RATE = 30 // 视频帧率
    private val AUDIO_BIT_RATE = 96000 // 音频比特率
    private val AUDIO_SAMPLE_RATE = 44100 // 音频采样率
    private val DISPLAY_WIDTH = 2176 // 显示宽度
    private val DISPLAY_HEIGHT = 1600 // 显示高度
    private val DISPLAY_DPI = 2 // 显示DPI

    private var mediaProjection: MediaProjection? = null // 用于屏幕录制的 MediaProjection 对象
    private var mediaCodec: MediaCodec? = null // 用于视频编码的 MediaCodec 对象
    private var audioCodec: MediaCodec? = null // 用于音频编码的 MediaCodec 对象
    private var mediaMuxer: MediaMuxer? = null // 用于将编码后的数据写入文件的 MediaMuxer 对象
    private var inputSurface: Surface? = null // 用于 MediaProjection 的输入 Surface
    private var segmentIndex = 0 // 当前视频段的索引
    private var segmentStartTime: Long = 0 // 当前视频段的开始时间
    private val muxerLock = Object() // 用于同步 MediaMuxer 操作的锁

    // 用于创建虚拟显示器的 VirtualDisplay 对象
    private var virtualDisplay: VirtualDisplay? = null

    // 音频录制对象
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    // 用于获取屏幕录制权限和创建 MediaProjection 对象
    private var mediaProjectionManager: MediaProjectionManager? = null

    init {
        // 获取 MediaProjectionManager 实例，用于屏幕录制
        mediaProjectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    fun startScreenRecording(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)

        setupMediaCodec()
        setupMediaProjection()

        segmentStartTime = System.nanoTime() / 1000
        startNewSegment()

        isRecording = true
        Thread {
            recordLoop() // 启动录制循环
        }.start()
    }

    // 初始化并配置MediaCodec的方法
    @SuppressLint("MissingPermission")
    private fun setupMediaCodec() {
        try {
            // 创建并配置视频格式，使用H.264编码，分辨率为DISPLAY_WIDTH x DISPLAY_HEIGHT
            val format =
                MediaFormat.createVideoFormat("video/avc", DISPLAY_WIDTH, DISPLAY_HEIGHT).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
                    setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                }

            // 创建MediaCodec用于视频编码，配置并启动
            mediaCodec = MediaCodec.createEncoderByType("video/avc").apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface() // 创建输入Surface，用于接收屏幕内容
                start() // 启动MediaCodec
            }

            // 配置音频格式
            val audioFormat = MediaFormat.createAudioFormat(
                "audio/mp4a-latm",
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
            }

            // 创建音频编码器并配置
            audioCodec = MediaCodec.createEncoderByType("audio/mp4a-latm").apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // 初始化音频录制
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(AUDIO_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(
                    AudioRecord.getMinBufferSize(
                        AUDIO_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                )
                .build()
        } catch (e: IOException) {
            e.printStackTrace() // 捕获并打印异常
        }
    }

    // 初始化并配置MediaProjection的方法
    private fun setupMediaProjection() {
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecord", // 虚拟显示的名称
            DISPLAY_WIDTH, DISPLAY_HEIGHT, // 虚拟显示的宽度和高度
            DISPLAY_DPI, // 虚拟显示的DPI（像素密度）
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, // 自动镜像标志
            inputSurface, null, null // 将输入Surface传递给VirtualDisplay，用于捕获屏幕内容
        )
    }

    // 启动新的视频段录制的方法
    private fun startNewSegment() {
        Thread { // 使用线程启动新的视频段录制
            synchronized(muxerLock) { // 同步锁定muxerLock，确保MediaMuxer操作的线程安全
                try {
                    val outputPath = getOutputFilePath(segmentIndex) // 生成输出文件路径
                    // 创建MediaMuxer，用于将编码数据写入新文件
                    mediaMuxer =
                        MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
                            val videoFormat = mediaCodec?.outputFormat // 从MediaCodec获取输出格式
                            videoFormat?.let { addTrack(it) } // 将输出格式添加到MediaMuxer中

                            // 添加音频轨道
                            val audioFormat = audioCodec?.outputFormat
                            audioFormat?.let { addTrack(it) }

                            start() // 启动MediaMuxer，开始写入数据
                        }
                    segmentIndex++ // 增加段索引
                } catch (e: IOException) {
                    e.printStackTrace() // 捕获并打印异常
                }
            }
        }.start()
    }

    // 持续处理编码数据并写入文件的方法
    private fun recordLoop() {
        val bufferInfo = MediaCodec.BufferInfo() // 用于存储编码数据的BufferInfo对象
        val audioBuffer = ByteArray(1024)

        audioRecord?.startRecording() // 启动音频录制

        while (isRecording) { // 无限循环，持续处理编码数据
            // 处理视频编码输出
            val videoOutputBufferIndex =
                mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000)// 获取编码后的输出缓冲区
            videoOutputBufferIndex?.let { index ->
                if (index >= 0) {
                    // 获取输出缓冲区数据
                    val outputBuffer = mediaCodec?.getOutputBuffer(index)
                    // 如果缓冲区不为空且有数据
                    outputBuffer?.let {
                        // 如果缓冲区大小不为零
                        if (bufferInfo.size != 0) {
                            // 更新缓冲区的显示时间戳
                            bufferInfo.presentationTimeUs =
                                System.nanoTime() / 1000 - segmentStartTime
                            synchronized(muxerLock) {
                                // 同步锁定muxerLock，确保MediaMuxer操作的线程安全
                                // 将编码后的数据写入文件
                                mediaMuxer?.writeSampleData(0, it, bufferInfo)
                            }
                        }
                        // 释放缓冲区
                        mediaCodec?.releaseOutputBuffer(index, false)
                    }
                }
            }

            // 处理音频编码输出
            val audioOutputBufferIndex = audioCodec?.dequeueOutputBuffer(bufferInfo, 10000)
            audioOutputBufferIndex?.let { index ->
                if (index >= 0) {
                    // 获取音频数据
                    val outputBuffer = audioCodec?.getOutputBuffer(index)
                    outputBuffer?.let {
                        if (bufferInfo.size != 0) {
                            bufferInfo.presentationTimeUs =
                                System.nanoTime() / 1000 - segmentStartTime
                            synchronized(muxerLock) {
                                mediaMuxer?.writeSampleData(1, it, bufferInfo)
                            }
                        }
                        audioCodec?.releaseOutputBuffer(index, false)
                    }
                }
            }

            // 从麦克风获取音频数据并编码
            val readBytes = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
            if (readBytes > 0) {
                val inputBufferIndex = audioCodec?.dequeueInputBuffer(10000)
                inputBufferIndex?.let { index ->
                    val inputBuffer = audioCodec?.getInputBuffer(index)
                    inputBuffer?.clear()
                    inputBuffer?.put(audioBuffer, 0, readBytes)
                    audioCodec?.queueInputBuffer(index, 0, readBytes, System.nanoTime() / 1000, 0)
                }
            }

            // 检查是否需要切换到下一个段
            if (System.nanoTime() / 1000 - segmentStartTime >= SEGMENT_DURATION_MS * 1000) {
                switchSegment()
            }
        }
    }

    // 切换到下一个视频段的方法
    private fun switchSegment() {
        Thread { // 使用线程切换视频段
            synchronized(muxerLock) { // 同步锁定muxerLock，确保MediaMuxer操作的线程安全
                stopCurrentSegment() // 停止当前视频段的录制
                startNewSegment() // 启动新的视频段录制
                segmentStartTime = System.nanoTime() / 1000 // 更新新段的开始时间
            }
        }.start()
    }

    // 停止当前视频段录制的方法
    private fun stopCurrentSegment() {
        synchronized(muxerLock) { // 同步锁定muxerLock，确保MediaMuxer操作的线程安全
            mediaMuxer?.apply { // 如果MediaMuxer不为空
                stop() // 停止MediaMuxer
                release() // 释放MediaMuxer资源
            }
            mediaMuxer = null // 清空MediaMuxer对象
        }
    }

    // 停止屏幕录制的方法
    fun stopScreenRecording() {
        isRecording = false
        stopCurrentSegment() // 停止并释放当前视频段
        mediaCodec?.apply { // 如果MediaCodec不为空
            stop() // 停止MediaCodec
            release() // 释放MediaCodec资源
        }
        mediaCodec = null // 清空MediaCodec对象
        audioCodec?.apply {
            stop() // 停止音频编码
            release() // 释放音频编码器资源
        }
        audioCodec = null
        audioRecord?.stop() // 停止音频录制
        audioRecord?.release() // 释放音频录制资源
        audioRecord = null
        virtualDisplay?.release() // 释放VirtualDisplay资源
        virtualDisplay = null // 清空VirtualDisplay对象
        mediaProjection?.stop() // 停止MediaProjection
    }

    // 获取输出文件路径的方法
    private fun getOutputFilePath(segmentIndex: Int): String {
        // 获取应用的外部文件目录路径
        val directory = Environment.getExternalStorageDirectory().absolutePath + "/ScreenRecordings"
        val file = File(directory)
        if (!file.exists()) {
            file.mkdirs() // 如果目录不存在，则创建目录
        }
        // 返回视频段的输出文件路径
        return "$directory/segment_$segmentIndex.mp4"
    }
}
