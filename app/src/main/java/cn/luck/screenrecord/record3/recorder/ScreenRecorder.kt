package cn.luck.screenrecord.record3.recorder

import MicRecorder
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import cn.luck.screenrecord.record3.FileSizeSpliceChecker
import cn.luck.screenrecord.record3.ScreenRecordService3
import cn.luck.screenrecord.record3.config.AudioConfig
import cn.luck.screenrecord.record3.config.VideoConfig
import cn.luck.screenrecord.record3.encoder.BaseRecorderEncoder
import cn.luck.screenrecord.record3.encoder.IRecorderEncoder
import cn.luck.screenrecord.record3.encoder.VideoRecorderEncoder
import cn.luck.screenrecord.record3.utils.RecordFileManager
import cn.luck.screenrecord.utils.LogUtil
import cn.luck.screenrecord.utils.WorkManager
import java.io.IOException
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecorder(
    private val context: Context,
    private val videoConfig: VideoConfig,       // 视频配置对象
    private val audioConfig: AudioConfig?,      // 音频配置对象，可选
) {

    private var videoEncoder: VideoRecorderEncoder? = null  // 视频编码器
    private var audioEncoder: MicRecorder? = null           // 音频编码器

    private var videoOutputFormat: MediaFormat? = null  // 视频输出格式
    private var audioOutputFormat: MediaFormat? = null  // 音频输出格式
    private var videoTrackIndex = INVALID_INDEX         // 视频轨道索引
    private var audioTrackIndex = INVALID_INDEX         // 音频轨道索引
    private var muxer: MediaMuxer? = null               // 媒体复用器，用于将音频和视频合成为一个文件
    private var muxerStarted = AtomicBoolean(false)         // 复用器是否已启动

    private val forceQuit = AtomicBoolean(false)        // 是否强制退出
    private val isRunning = AtomicBoolean(false)        // 录制是否正在进行

    private val pendingVideoEncoderBufferIndices = LinkedList<Int>()        // 待处理的视频编码器缓冲区索引列表
    private val pendingAudioEncoderBufferIndices = LinkedList<Int>()        // 待处理的音频编码器缓冲区索引列表
    private val pendingAudioEncoderBufferInfos =
        LinkedList<MediaCodec.BufferInfo>()  // 待处理的音频编码器缓冲区信息列表
    private val pendingVideoEncoderBufferInfos =
        LinkedList<MediaCodec.BufferInfo>()  // 待处理的视频编码器缓冲区信息列表

    private var callback: Callback? = null              // 回调接口，用于通知录制状态

    private val workManager = WorkManager()             // 工作管理器，用于管理异步任务

    // 用于获取屏幕录制权限和创建 MediaProjection 对象
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null // 用于屏幕录制的 MediaProjection 对象
    private var virtualDisplay: VirtualDisplay? = null

    private val recordFileManager by lazy {
        RecordFileManager(context)
    }

    private val spliceChecker by lazy {
        FileSizeSpliceChecker(recordFileManager)
    }

    // 回调接口定义
    interface Callback {
        fun onStop(error: Throwable?)          // 录制停止时调用
        fun onStart()                          // 录制开始时调用
        fun onRecording(presentationTimeUs: Long) // 录制过程中调用，用于通知当前录制的时间戳
    }

    init {
        // 获取 MediaProjectionManager 实例，用于屏幕录制
        mediaProjectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        recordFileManager.setJourneyId("YYY")
    }

    private fun setupMediaProjection() {
        if (virtualDisplay == null) {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecorder-display0", // 虚拟显示的名称
                ScreenRecordService3.DISPLAY_WIDTH,
                ScreenRecordService3.DISPLAY_HEIGHT, // 虚拟显示的宽度和高度
                360, // 虚拟显示的DPI（像素密度）
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, // 自动镜像标志
                videoEncoder?.getInputSurface(),
                null,
                null // 将输入Surface传递给VirtualDisplay，用于捕获屏幕内容
            )
            LogUtil.d(TAG, "初始化并配置录屏来源 MediaProjection 成功")
        } else {
            val size = Point()
            virtualDisplay!!.display.getSize(size)
            if (size.x != videoConfig.width || size.y != videoConfig.height) {
                virtualDisplay!!.resize(videoConfig.width, videoConfig.height, 1)
            }
        }
    }

    fun startRecord(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        start()
    }


    private fun start() {
        if (isRunning.get()) throw IllegalStateException("ScreenRecorder is already running") // 如果录制已在进行，抛出异常
        workManager.addWork({
            startRecording() // 添加录制任务到工作管理器
        }, object : WorkManager.WorkCallback<Unit> {
            override fun onSuccess(data: Unit) {
                callback?.onStart() // 录制任务成功后调用回调通知开始
                spliceChecker.checkSplice {
                    LogUtil.printThreadInfo(TAG, "spliceChecker() callback splice=$it")
                    if (it) {
                        spliceFile()
                    }
                }
            }

            override fun onError(exception: Throwable) {
                LogUtil.e(TAG, "start() error=${exception.printStackTrace()}")
                stopEncoders()       // 处理错误，停止编码器
                callback?.onStop(exception)
                release()            // 释放资源
            }
        })
    }


    private suspend fun startRecording() {
        if (isRunning.get() || forceQuit.get()) throw IllegalStateException() // 如果正在运行或强制退出，抛出异常
        isRunning.set(true) // 设置录制状态为正在进行
        LogUtil.d(TAG, "startRecording()")
        try {
            setupMuxer()
            prepareVideoEncoder() // 准备视频编码器
            prepareAudioEncoder() // 准备音频编码器
        } catch (e: IOException) {
            throw RuntimeException(e) // 捕获异常并抛出运行时异常
        }
        setupMediaProjection()
    }

    private fun setupMuxer() {
        try {
            val filePath = recordFileManager.getNextOutputFile().absolutePath
            LogUtil.d(TAG, "设置混合器 setupMuxer() filePath=$filePath")
            muxer = MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) // 初始化媒体复用器
        } catch (e: IOException) {
            throw RuntimeException(e) // 捕获异常并抛出运行时异常
        }
    }

    private fun prepareVideoEncoder() {
        videoEncoder = VideoRecorderEncoder(videoConfig).apply { // 使用视频配置初始化视频编码器
            setCallback(object : BaseRecorderEncoder.BaseRecorderEncoderCallback() { // 设置编码器的回调
                override fun onInputBufferAvailable(encoder: BaseRecorderEncoder?, index: Int) {
                    LogUtil.d(TAG, "prepareVideoEncoder - onInputBufferAvailable() index=$index")
                }

                override fun onOutputBufferAvailable(
                    encoder: BaseRecorderEncoder?,
                    index: Int,
                    info: MediaCodec.BufferInfo
                ) {
                    LogUtil.printThreadInfo(
                        TAG,
                        "videoEncoder - onOutputBufferAvailable() index=$index"
                    )
                    workManager.addWork({
                        muxVideo(index, info) // 在工作管理器中处理视频数据的复用
                    }, object : WorkManager.WorkCallback<Unit> {
                        override fun onSuccess(data: Unit) {
                            LogUtil.d(TAG, "执行muxVideo成功")
                        }

                        override fun onError(exception: Throwable) {
                            LogUtil.e(
                                TAG,
                                "prepareVideoEncoder() workManager error=${exception.printStackTrace()}"
                            )
                            stopRecording(false) // 处理错误并停止录制
                            callback?.onStop(exception)
                            release()
                        }
                    })
                }

                override fun onOutputFormatChanged(
                    encoder: BaseRecorderEncoder?,
                    format: MediaFormat?
                ) {
                    LogUtil.e(TAG, "prepareVideoEncoder - onOutputFormatChanged")
                    videoOutputFormat = format // 设置视频输出格式
                    startMuxerIfReady() // 检查是否可以启动复用器
                }

                override fun onError(encoder: IRecorderEncoder?, exception: Throwable?) {
                    LogUtil.e(TAG, "prepareVideoEncoder() error=${exception?.printStackTrace()}")
                    callback?.onStop(exception) // 处理错误并停止录制
                    stopRecording(false)
                    release()
                }
            })
            prepare() // 准备编码器
        }
    }

    private fun prepareAudioEncoder() {
        audioEncoder = audioConfig?.let { MicRecorder(it) }?.apply { // 使用音频配置初始化音频编码器
            setCallback(object : BaseRecorderEncoder.BaseRecorderEncoderCallback() { // 设置编码器的回调
                override fun onInputBufferAvailable(encoder: BaseRecorderEncoder?, index: Int) {
                    LogUtil.d(TAG, "prepareAudioEncoder - onInputBufferAvailable() index=$index")
                }

                override fun onOutputBufferAvailable(
                    encoder: BaseRecorderEncoder?,
                    index: Int,
                    info: MediaCodec.BufferInfo
                ) {
                    workManager.addWork({
                        muxAudio(index, info) // 在工作管理器中处理音频数据的复用
                        index
                    }, object : WorkManager.WorkCallback<Int> {
                        override fun onSuccess(data: Int) {
                            LogUtil.d(TAG, "执行muxAudio成功 index=$index")
                        }

                        override fun onError(exception: Throwable) {
                            LogUtil.e(
                                TAG,
                                "prepareAudioEncoder() workManager error=${exception.message}"
                            )
                            stopRecording(false) // 处理错误并停止录制
                            callback?.onStop(exception)
                            release()
                        }

                    })
                }

                override fun onOutputFormatChanged(
                    encoder: BaseRecorderEncoder?,
                    format: MediaFormat?
                ) {
                    LogUtil.e(TAG, "prepareAudioEncoder - onOutputFormatChanged")
                    audioOutputFormat = format // 设置音频输出格式
                    startMuxerIfReady() // 检查是否可以启动复用器
                }

                override fun onError(encoder: IRecorderEncoder?, exception: Throwable?) {
                    LogUtil.e(TAG, "prepareAudioEncoder() error=${exception?.printStackTrace()}")
                    callback?.onStop(exception) // 处理错误并停止录制
                    stopRecording(false)
                    release()
                }
            })
            prepare() // 准备编码器
        }
    }

    private fun startMuxerIfReady() {
        LogUtil.d(TAG, "startMuxerIfReady()1")
        LogUtil.printThreadInfo(TAG, "startMuxerIfReady()1")
        // 如果复用器已启动或格式未准备好，则返回
        if (muxerStarted.get() || videoOutputFormat == null || (audioEncoder != null && audioOutputFormat == null)) {
            LogUtil.d(
                TAG,
                "startMuxerIfReady(), 避免重复调用混合器开启， muxerStarted=$muxerStarted"
            )
            return
        }

        LogUtil.d(TAG, "startMuxerIfReady()2")
        videoTrackIndex = muxer?.addTrack(videoOutputFormat!!) ?: INVALID_INDEX // 添加视频轨道
        audioTrackIndex =
            if (audioEncoder == null) INVALID_INDEX else muxer?.addTrack(audioOutputFormat!!)
                ?: INVALID_INDEX // 添加音频轨道
        muxer?.start() // 启动复用器
        muxerStarted.set(true)
        processPendingBuffers() // 处理待处理的缓冲区
    }

    private fun spliceFile() {
        LogUtil.d(TAG, "切换切片文件")
        LogUtil.printThreadInfo(TAG, "spliceFile()")
        releaseMuxer()
        val params = Bundle()
        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        videoEncoder?.getRecorderEncoder()?.setParameters(params)
        setupMuxer()
        startMuxerIfReady()
    }


    private fun processPendingBuffers() {
        LogUtil.d(
            TAG, "processPendingBuffers(): " +
                    "videoCacheSize=${pendingVideoEncoderBufferInfos.size}, " +
                    "audioCacheSize=${pendingAudioEncoderBufferInfos.size}"
        )

        // 处理视频缓冲区
        while (pendingVideoEncoderBufferInfos.isNotEmpty()) {
            val info = pendingVideoEncoderBufferInfos.poll()
            val index = pendingVideoEncoderBufferIndices.poll()
            LogUtil.d(TAG, "复用视频数据")
            muxVideo(index, info) // 复用视频数据
        }
        // 处理音频缓冲区
        if (audioEncoder != null) {
            while (pendingAudioEncoderBufferInfos.isNotEmpty()) {
                val info = pendingAudioEncoderBufferInfos.poll()
                val index = pendingAudioEncoderBufferIndices.poll()
                LogUtil.d(TAG, "复用音频数据")
                muxAudio(index, info) // 复用音频数据
            }
        }
    }

    private fun muxVideo(index: Int?, info: MediaCodec.BufferInfo?) {
        LogUtil.d(TAG, "muxVideo() index=$index")
        if (index == null || info == null) {
            return
        }
        if (!isRunning.get() || !muxerStarted.get() || videoTrackIndex == INVALID_INDEX) {
            LogUtil.d(
                TAG,
                "视频数据存入缓存，muxerStarted=$muxerStarted, videoTrackIndex=$videoTrackIndex"
            )
            pendingVideoEncoderBufferIndices.add(index)
            pendingVideoEncoderBufferInfos.add(info)
            return // 如果未准备好复用器，则将缓冲区信息添加到待处理列表
        }
        val encodedData = videoEncoder?.getOutputBuffer(index) // 获取视频编码器的输出缓冲区
        encodedData?.let {
            writeSampleData(videoTrackIndex, info, it) // 将视频数据写入复用器
            videoEncoder?.releaseOutputBuffer(index) // 释放视频编码器的输出缓冲区
        }
    }

    private fun muxAudio(index: Int?, info: MediaCodec.BufferInfo?) {
        LogUtil.d(TAG, "muxAudio() muxerStarted=$muxerStarted")
        if (index == null || info == null) {
            return
        }
        if (!isRunning.get() || !muxerStarted.get() || audioTrackIndex == INVALID_INDEX) {
            LogUtil.d(
                TAG,
                "音频数据存入缓存，muxerStarted=$muxerStarted, videoTrackIndex=$videoTrackIndex"
            )
            pendingAudioEncoderBufferIndices.add(index)
            pendingAudioEncoderBufferInfos.add(info)
            return // 如果未准备好复用器，则将缓冲区信息添加到待处理列表
        }
        val encodedData = audioEncoder?.getOutputBuffer(index) // 获取音频编码器的输出缓冲区
        encodedData?.let {
            writeSampleData(audioTrackIndex, info, it) // 将音频数据写入复用器
            audioEncoder?.releaseOutputBuffer(index) // 释放音频编码器的输出缓冲区
        }
    }

    private fun writeSampleData(
        track: Int,
        buffer: MediaCodec.BufferInfo,
        encodedData: ByteBuffer
    ) {
        LogUtil.d(TAG, "writeSampleData() buffer=${buffer.presentationTimeUs}")
        if ((buffer.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            buffer.size = 0 // 如果是编解码器配置缓冲区，则忽略数据
        }
        try {
            if (buffer.size != 0 && (buffer.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                encodedData.let {
                    it.position(buffer.offset)
                    it.limit(buffer.offset + buffer.size)
                    muxer?.writeSampleData(track, it, buffer) // 将数据写入复用器
                    callback?.onRecording(buffer.presentationTimeUs) // 通知当前的录制时间戳
                }
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }

    }

    fun quit() {
        forceQuit.set(true)  // 设置强制退出标志
        spliceChecker.stopCheck()
        if (!isRunning.get()) {
            release()        // 如果未在运行，直接释放资源
        } else {
            stopRecording(false)  // 否则，停止录制
        }
    }

    private fun signalEndOfStream() {
        LogUtil.d(TAG, "signalEndOfStream")
        // 为视频和音频轨道发出结束流的信号
        val eos = MediaCodec.BufferInfo().apply {
            set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        if (videoTrackIndex != INVALID_INDEX) {
            writeSampleData(videoTrackIndex, eos, ByteBuffer.allocate(0)) // 写入视频的结束流数据
        }
        if (audioTrackIndex != INVALID_INDEX) {
            writeSampleData(audioTrackIndex, eos, ByteBuffer.allocate(0)) // 写入音频的结束流数据
        }
        videoTrackIndex = INVALID_INDEX
        audioTrackIndex = INVALID_INDEX
    }

    fun setCallback(callback: Callback?) {
        this.callback = callback // 设置回调接口
    }

    private fun stopRecording(stopWithEOS: Boolean) {
        if (stopWithEOS) {
            signalEndOfStream() // 发出结束流的信号
        } else {
            stopEncoders() // 停止编码器
        }
        release() // 释放资源
    }

    private fun stopEncoders() {
        LogUtil.d(TAG, "stopEncoders")
        isRunning.set(false) // 设置录制状态为停止
        videoEncoder?.stop() // 停止视频编码器
        audioEncoder?.stop() // 停止音频编码器
    }

    private fun releaseMuxer() {
        if (muxerStarted.get()) {
            LogUtil.d(TAG, "releaseMuxer()")
            muxerStarted.set(false)
            muxer?.stop() // 停止复用器
            muxer?.release() // 释放复用器
            muxer = null
        }
    }

    private fun release() {
        LogUtil.d(TAG, "release")
        virtualDisplay?.surface = null // 释放虚拟显示的表面
        virtualDisplay = null

        videoOutputFormat = null
        audioOutputFormat = null
        videoTrackIndex = INVALID_INDEX
        audioTrackIndex = INVALID_INDEX

        workManager.release() // 释放工作管理器

        videoEncoder?.release() // 释放视频编码器
        audioEncoder?.release() // 释放音频编码器

        releaseMuxer()
    }

    fun getRecorderFileDirPath(): String {
        return recordFileManager.getRecorderFileDirPath()
    }

    companion object {
        private const val INVALID_INDEX = -1 // 无效的轨道索引
        private const val TAG = "ScreenRecorder" // 日志标签
    }
}
