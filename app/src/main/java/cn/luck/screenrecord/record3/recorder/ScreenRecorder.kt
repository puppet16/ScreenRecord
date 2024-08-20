package cn.luck.screenrecord.record3.recorder

import AudioRecorder
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
import cn.luck.screenrecord.record3.splicechecker.FileSizeSpliceChecker
import cn.luck.screenrecord.record3.config.AudioConfig
import cn.luck.screenrecord.record3.config.VideoConfig
import cn.luck.screenrecord.record3.encoder.BaseRecorderEncoder
import cn.luck.screenrecord.record3.encoder.IRecorderEncoder
import cn.luck.screenrecord.record3.encoder.VideoRecorderEncoder
import cn.luck.screenrecord.record3.manager.RecordFileManager
import cn.luck.screenrecord.record3.splicechecker.ISpliceChecker
import cn.luck.screenrecord.utils.LogUtil
import cn.luck.screenrecord.utils.OrderWorkManager
import cn.luck.screenrecord.utils.WorkManager
import java.io.IOException
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecorder(
    private val context: Context,
    // 视频配置对象
    private val videoConfig: VideoConfig,
    // 音频配置对象，可选
    private val audioConfig: AudioConfig?,      
) {
    // 视频编码器
    private var videoEncoder: VideoRecorderEncoder? = null
    // 音频编码器
    private var audioEncoder: AudioRecorder? = null
    // 视频输出格式
    private var videoOutputFormat: MediaFormat? = null
    // 音频输出格式
    private var audioOutputFormat: MediaFormat? = null
    // 视频轨道索引
    private var videoTrackIndex = INVALID_INDEX
    // 音频轨道索引
    private var audioTrackIndex = INVALID_INDEX
    // 媒体复用器，用于将音频和视频合成为一个文件
    private var muxer: MediaMuxer? = null
    // 复用器是否已启动
    private var muxerStarted = AtomicBoolean(false)
    // 是否强制退出
    private val forceQuit = AtomicBoolean(false)
    // 录制是否正在进行
    private val isRunning = AtomicBoolean(false)
    // 待处理的视频编码器缓冲区索引列表
    private val pendingVideoEncoderBufferIndices = LinkedList<Int>()
    // 待处理的音频编码器缓冲区索引列表
    private val pendingAudioEncoderBufferIndices = LinkedList<Int>()
    // 待处理的音频编码器缓冲区信息列表
    private val pendingAudioEncoderBufferInfos =
        LinkedList<MediaCodec.BufferInfo>()
    // 待处理的视频编码器缓冲区信息列表
    private val pendingVideoEncoderBufferInfos =
        LinkedList<MediaCodec.BufferInfo>()
    // 回调接口，用于通知录制状态
    private var recorderCallback: RecorderCallback? = null
    // 用于管理异步任务
    private val workManager = WorkManager()

    // 用于获取屏幕录制权限和创建 MediaProjection 对象
    private var mediaProjectionManager: MediaProjectionManager? = null
    // 用于屏幕录制的 MediaProjection 对象
    private var mediaProjection: MediaProjection? = null
    // 虚拟显示，将屏幕数据放到这里
    private var virtualDisplay: VirtualDisplay? = null
    // 录制后的文件的管理
    private val recordFileManager by lazy {
        RecordFileManager(context)
    }
    // 录制文件的切片时机检查器
    private val spliceChecker: ISpliceChecker by lazy {
        FileSizeSpliceChecker(recordFileManager)
    }

    private val orderWorkManager by lazy {
        OrderWorkManager()
    }

    companion object {
        private const val INVALID_INDEX = -1 // 无效的轨道索引
        private const val TAG = "ScreenRecorder" // 日志标签
    }

    init {
        // 获取 MediaProjectionManager 实例，用于屏幕录制
        mediaProjectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        recordFileManager.setVideoId("YYY")
    }

    /**
     * 创建虚拟显示
     */
    private fun setupMediaProjection() {
        if (virtualDisplay == null) {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                // 虚拟显示的名称
                "ScreenRecorder-display0",
                // 虚拟显示的宽度和高度
                SpliceScreenRecordService.DISPLAY_WIDTH,
                SpliceScreenRecordService.DISPLAY_HEIGHT,
                // 虚拟显示的DPI（像素密度）
                360,
                // 自动镜像标志
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                // 将输入Surface传递给VirtualDisplay，用于捕获屏幕内容
                videoEncoder?.getInputSurface(),
                null,
                null
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

    /**
     * 录屏功能的入口
     * @param resultCode Int
     * @param data Intent
     */
    fun startRecord(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        start()
    }


    private fun start() {
        // 如果录制已在进行，抛出异常
        if (isRunning.get()) throw IllegalStateException("ScreenRecorder is already running")
        // 添加录制任务到工作管理器
        workManager.addWork({
            startRecording()
        }, object : WorkManager.WorkCallback<Unit> {
            override fun onSuccess(data: Unit) {
                // 录制任务成功后调用回调通知开始，并检查切片时机
                recorderCallback?.onStart()
                spliceChecker.checkSplice {
                    LogUtil.printThreadInfo(TAG, "spliceChecker() callback splice=$it")
                    if (it) {
                        spliceFile()
                    }
                }
            }

            override fun onError(exception: Throwable) {
                LogUtil.e(TAG, "start() error=${exception.printStackTrace()}")
                // 处理错误，停止编码器
                stopEncoders()
                recorderCallback?.onStop(exception)
                // 释放资源
                release()
            }
        })
    }

    /**
     * 开始录制的准备工作
     *
     */
    private fun startRecording() {
        // 如果正在运行或强制退出，抛出异常
        if (isRunning.get() || forceQuit.get()) throw IllegalStateException()
        isRunning.set(true) // 设置录制状态为正在进行
        LogUtil.d(TAG, "startRecording()")
        try {
            setupMuxer()
            // 准备视频编码器
            prepareVideoEncoder()
            // 准备音频编码器
            prepareAudioEncoder()
        } catch (e: IOException) {
            // 捕获异常并抛出运行时异常
            LogUtil.e(TAG, "准备开始录制时，遇到异常$e")
            throw RuntimeException(e)
        }
        // 必须要在视频编码器准备好后，再初始化，否则初始化时取不到视频编码器的缓冲区
        setupMediaProjection()
    }

    /**
     * 创建复用器
     */
    private fun setupMuxer() {
        try {
            val filePath = recordFileManager.getNextOutputFile().absolutePath
            LogUtil.d(TAG, "设置复用器 setupMuxer() filePath=$filePath")
            // 初始化媒体复用器
            muxer = MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: IOException) {
            LogUtil.e(TAG, "创建复用器失败，e=${e.printStackTrace()}")
        }
    }

    /**
     * 准备视频编码器
     */
    private fun prepareVideoEncoder() {
        // 使用视频配置初始化视频编码器
        videoEncoder = VideoRecorderEncoder(videoConfig).apply {
            // 设置编码器的回调
            setCallback(object : BaseRecorderEncoder.BaseRecorderEncoderCallback() {
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
                    orderWorkManager.addWork({
                        // 异步处理视频数据的复用
                        muxVideo(index, info)
                        index
                    }, object : OrderWorkManager.WorkCallback<Int> {
                        override fun onSuccess(data: Int) {
                            LogUtil.d(TAG, "执行muxVideo成功, index=$index")
                        }

                        override fun onError(exception: Throwable) {
                            LogUtil.e(
                                TAG,
                                "prepareVideoEncoder() workManager error=${exception.printStackTrace()}"
                            )
                            // 处理错误并停止录制
                            stopRecording(false)
                            recorderCallback?.onStop(exception)
                            release()
                        }
                    })
                }

                override fun onOutputFormatChanged(
                    encoder: BaseRecorderEncoder?,
                    format: MediaFormat?
                ) {
                    LogUtil.e(TAG, "prepareVideoEncoder - onOutputFormatChanged")
                    // 设置视频输出格式
                    videoOutputFormat = format
                    // 检查是否可以启动复用器
                    startMuxerIfReady()
                }

                override fun onError(encoder: IRecorderEncoder?, exception: Throwable?) {
                    LogUtil.e(TAG, "prepareVideoEncoder() error=${exception?.printStackTrace()}")
                    // 处理错误并停止录制
                    recorderCallback?.onStop(exception)
                    stopRecording(false)
                    release()
                }
            })
            // 准备编码器
            prepare()
        }
    }

    /**
     * 准备音频编码器
     */
    private fun prepareAudioEncoder() {
        // 使用音频配置初始化音频编码器
        audioEncoder = audioConfig?.let { AudioRecorder(it) }?.apply {
            // 设置编码器的回调
            setCallback(object : BaseRecorderEncoder.BaseRecorderEncoderCallback() {
                override fun onInputBufferAvailable(encoder: BaseRecorderEncoder?, index: Int) {
                    LogUtil.d(TAG, "prepareAudioEncoder - onInputBufferAvailable() index=$index")
                }

                override fun onOutputBufferAvailable(
                    encoder: BaseRecorderEncoder?,
                    index: Int,
                    info: MediaCodec.BufferInfo
                ) {
                    // 异步处理音频数据的复用
                    orderWorkManager.addWork({
                        muxAudio(index, info, true)
                        index
                    }, object : OrderWorkManager.WorkCallback<Int> {
                        override fun onSuccess(data: Int) {
                            LogUtil.d(TAG, "执行muxAudio成功 index=$index")
                        }

                        override fun onError(exception: Throwable) {
                            LogUtil.e(
                                TAG,
                                "prepareAudioEncoder() workManager error=${exception.message}"
                            )
                            // 处理错误并停止录制
                            stopRecording(false)
                            recorderCallback?.onStop(exception)
                            release()
                        }

                    })
                }

                override fun onOutputFormatChanged(
                    encoder: BaseRecorderEncoder?,
                    format: MediaFormat?
                ) {
                    LogUtil.e(TAG, "prepareAudioEncoder - onOutputFormatChanged")
                    // 设置音频输出格式
                    audioOutputFormat = format
                    // 检查是否可以启动复用器
                    startMuxerIfReady()
                }

                override fun onError(encoder: IRecorderEncoder?, exception: Throwable?) {
                    LogUtil.e(TAG, "prepareAudioEncoder() error=${exception?.printStackTrace()}")
                    // 处理错误并停止录制
                    recorderCallback?.onStop(exception)
                    stopRecording(false)
                    release()
                }
            })
            // 准备编码器
            prepare()
        }
    }

    /**
     * 复用器开始工作，产出录制结果
     */
    private fun startMuxerIfReady() {
        LogUtil.printThreadInfo(TAG, "startMuxerIfReady()1")
        // 如果复用器已启动或格式未准备好，则返回
        if (muxerStarted.get() || videoOutputFormat == null || (audioEncoder != null && audioOutputFormat == null)) {
            LogUtil.d(
                TAG,
                "startMuxerIfReady(), 避免重复调用复用器开启， muxerStarted=$muxerStarted"
            )
            return
        }

        LogUtil.d(TAG, "startMuxerIfReady()2")
        // 添加视频轨道
        videoTrackIndex = muxer?.addTrack(videoOutputFormat!!) ?: INVALID_INDEX
        // 添加音频轨道
        audioTrackIndex =
            if (audioEncoder == null) INVALID_INDEX else muxer?.addTrack(audioOutputFormat!!)
                ?: INVALID_INDEX
        // 启动复用器
        muxer?.start()
        muxerStarted.set(true)
        // 处理待处理的缓冲区
        processPendingBuffers()
    }

    /**
     * 对文件进行切片处理
     * 1. 关闭当前的复用器
     * 2. 让视频解码器创建一个关键帧，即下一个切片第一帧为关键帧，这能极大保证切片的连续性
     * 3. 创建一个新的复用器
     * 4. 让新的复用器开始工作，继续产出内容
     * 问题：1. 在关闭复用器时，编码器也不再进行内容的回调，揣测复用器在关闭时，也关闭了来源的内容输出
     *      2. 所以即使创建关键帧，也会有一定的延迟，但是非常小，几十毫秒
     */
    private fun spliceFile() {
        LogUtil.d(TAG, "切换切片文件")
        LogUtil.printThreadInfo(TAG, "spliceFile()")
        releaseMuxer()
        // 通知解码器创建一个关键帧
        val params = Bundle()
        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        videoEncoder?.getRecorderEncoder()?.setParameters(params)
        setupMuxer()
        startMuxerIfReady()
    }

    /**
     * 处理待处理的缓冲区
     */
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
            LogUtil.d(TAG, "使用缓存的视频数据")
            muxVideo(index, info)
        }
        // 处理音频缓冲区
        if (audioEncoder != null) {
            while (pendingAudioEncoderBufferInfos.isNotEmpty()) {
                val info = pendingAudioEncoderBufferInfos.poll()
                val index = pendingAudioEncoderBufferIndices.poll()
                LogUtil.d(TAG, "使用缓存的音频数据")
                muxAudio(index, info)
            }
        }
    }

    /**
     * 将视频编码数据写入复用器
     * @param index Int?
     * @param info BufferInfo?
     */
    private fun muxVideo(index: Int?, info: MediaCodec.BufferInfo?) {
        LogUtil.d(TAG, "muxVideo() index=$index")
        if (index == null || info == null) {
            return
        }
        // 1. 如果未在运行 或 复用器未准备好 或 视频轨道非法，则将数据放入待处理列表中缓存中
        if (!isRunning.get() || !muxerStarted.get() || videoTrackIndex == INVALID_INDEX) {
            LogUtil.d(
                TAG,
                "视频数据存入缓存，muxerStarted=$muxerStarted, videoTrackIndex=$videoTrackIndex"
            )
            // 则将缓冲区信息添加到待处理列表
            pendingVideoEncoderBufferIndices.add(index)
            pendingVideoEncoderBufferInfos.add(info)
            return
        }

        // 2. 获取视频编码器的输出缓冲区
        val encodedData = videoEncoder?.getOutputBuffer(index)
        encodedData?.let {
            // 将视频数据写入复用器
            writeSampleData(videoTrackIndex, info, it)
            // 释放视频编码器的输出缓冲区
            videoEncoder?.releaseOutputBuffer(index)
        }
    }

    /**
     * 将音频编码数据写入复用器
     * @param index Int?
     * @param info BufferInfo?
     */
    private fun muxAudio(index: Int?, info: MediaCodec.BufferInfo?, forcedCache: Boolean = false) {
        LogUtil.d(TAG, "muxAudio() muxerStarted=$muxerStarted")
        if (index == null || info == null) {
            return
        }
        // 1. 如果未在运行 或 复用器未准备好 或 音频轨道非法，则将数据放入待处理列表中缓存中
        if (!isRunning.get() || !muxerStarted.get() || audioTrackIndex == INVALID_INDEX) {
            LogUtil.d(
                TAG,
                "音频数据存入缓存，muxerStarted=$muxerStarted, videoTrackIndex=$videoTrackIndex"
            )
            // 则将缓冲区信息添加到待处理列表
            pendingAudioEncoderBufferIndices.add(index)
            pendingAudioEncoderBufferInfos.add(info)
            return
        }
        if (pendingAudioEncoderBufferInfos.isNotEmpty() && forcedCache) {
            LogUtil.d(TAG, "待处理列表里有数据，就先处理待处理列表的，以保证写入数据的顺序")
            pendingVideoEncoderBufferIndices.add(index)
            pendingVideoEncoderBufferInfos.add(info)
            return
        }
        // 获取音频编码器的输出缓冲区
        val encodedData = audioEncoder?.getOutputBuffer(index)
        encodedData?.let {
            // 将音频数据写入复用器
            writeSampleData(audioTrackIndex, info, it)
            // 释放音频编码器的输出缓冲区
            audioEncoder?.releaseOutputBuffer(index)
        }
    }


    private var lastVideoTimestamp: Long = 0
    private var lastAudioTimestamp: Long = 0
    /**
     * 使用 muxer 将采样数据写入文件中
     * @param trackIndex Int
     * @param buffer BufferInfo
     * @param encodedData ByteBuffer
     */
    private fun writeSampleData(
        trackIndex: Int,
        buffer: MediaCodec.BufferInfo,
        encodedData: ByteBuffer
    ) {
        val originTrack = when (trackIndex) {
            videoTrackIndex -> {
                "视频"

            }
            audioTrackIndex -> {
                "音频"
            }
            else -> {
                ""
            }
        }
        LogUtil.d(TAG, "writeSampleData(${originTrack})， lastAudioTime=$lastAudioTimestamp, buffer=${buffer.presentationTimeUs}")
        if ((buffer.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // 如果是编解码器配置缓冲区，则忽略数据
            buffer.size = 0
        }
        try {
            val currentTimestamp = buffer.presentationTimeUs
            // 对视频帧进行时间戳检查
            if (trackIndex == videoTrackIndex) {
                if (currentTimestamp <= lastVideoTimestamp) {
                    LogUtil.d(TAG, "过滤无序的视频帧 timestamp: $currentTimestamp, lastVideoTime=$lastVideoTimestamp")
                    // 丢弃时间戳无序的帧
                    return
                }
                lastVideoTimestamp = currentTimestamp
            }

            // 对音频帧进行时间戳检查
            if (trackIndex == audioTrackIndex) {
                if (currentTimestamp <= lastAudioTimestamp) {
                    LogUtil.d(TAG, "过滤无序的音频帧 timestamp: $currentTimestamp, lastVideoTime=$lastVideoTimestamp")
                    // 丢弃时间戳无序的帧
                    return
                }
                lastAudioTimestamp = currentTimestamp
            }

            if (buffer.size != 0 && (buffer.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                encodedData.let {
                    it.position(buffer.offset)
                    it.limit(buffer.offset + buffer.size)
                    muxer?.writeSampleData(trackIndex, it, buffer) // 将数据写入复用器
                    recorderCallback?.onRecording(buffer.presentationTimeUs) // 通知当前的录制时间戳
                }
            }
        } catch (exception: Exception) {
            LogUtil.e(TAG, "muxer 将 ${originTrack} 的采样数据写入文件时报错：exception=$exception")
            exception.printStackTrace()
        }

    }

    /**
     * 退出录制任务
     */
    fun quit() {
        // 设置强制退出标志
        forceQuit.set(true)
        // 不再进行切片检查
        spliceChecker.stopCheck()
        // 如果未在运行，直接释放资源
        if (!isRunning.get()) {
            release()
        } else {
            // 否则，停止录制
            stopRecording(false)
        }
    }

    /**
     * 停止录制任务
     * @param stopWithEOS Boolean
     */
    private fun stopRecording(stopWithEOS: Boolean) {
        if (stopWithEOS) {
            // 发出结束流的信号
            signalEndOfStream()
        } else {
            // 停止编码器
            stopEncoders()
        }
        // 释放资源
        release()
    }


    /**
     * 给视频和音频的轨道发送结束流的信号
     */
    private fun signalEndOfStream() {
        LogUtil.d(TAG, "signalEndOfStream")
        // 为视频和音频轨道发出结束流的信号
        val eos = MediaCodec.BufferInfo().apply {
            set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        if (videoTrackIndex != INVALID_INDEX) {
            // 写入视频的结束流数据
            writeSampleData(videoTrackIndex, eos, ByteBuffer.allocate(0))
        }
        if (audioTrackIndex != INVALID_INDEX) {
            // 写入音频的结束流数据
            writeSampleData(audioTrackIndex, eos, ByteBuffer.allocate(0))
        }
        videoTrackIndex = INVALID_INDEX
        audioTrackIndex = INVALID_INDEX
    }

    /**
     * 停止编码器
     */
    private fun stopEncoders() {
        LogUtil.d(TAG, "stopEncoders")
        // 设置录制状态为停止
        isRunning.set(false)
        // 停止视频编码器
        videoEncoder?.stop()
        // 停止音频编码器
        audioEncoder?.stop()
    }

    /**
     * 停止并释放 Muxer
     */
    private fun releaseMuxer() {
        if (muxerStarted.get()) {
            LogUtil.d(TAG, "releaseMuxer()")
            muxerStarted.set(false)
            // 停止复用器
            muxer?.stop()
            // 释放复用器
            muxer?.release()
            muxer = null

            videoTrackIndex = INVALID_INDEX
            audioTrackIndex = INVALID_INDEX
        }
    }

    /**
     * 释放所有资源
     */
    private fun release() {
        LogUtil.d(TAG, "release")
        // 释放虚拟显示的缓冲区
        virtualDisplay?.surface = null
        virtualDisplay = null

        videoOutputFormat = null
        audioOutputFormat = null
        // 取消所有异步处理
        workManager.release()
        orderWorkManager.release()
        // 释放视频编码器
        videoEncoder?.release()
        // 释放音频编码器
        audioEncoder?.release()

        releaseMuxer()
    }

    fun getRecorderFileDirPath(): String {
        return recordFileManager.getRecorderFileDirPath()
    }

    /**
     * 设置回调接口
     * @param recorderCallback RecorderCallback?
     */
    fun setRecorderCallback(recorderCallback: RecorderCallback?) {
        this.recorderCallback = recorderCallback
    }

    /**
     * 回调接口定义
     */
    interface RecorderCallback {
        // 录制停止时调用
        fun onStop(error: Throwable?)
        // 录制开始时调用
        fun onStart()
        // 录制过程中调用，用于通知当前录制的时间戳
        fun onRecording(presentationTimeUs: Long)
    }

}
