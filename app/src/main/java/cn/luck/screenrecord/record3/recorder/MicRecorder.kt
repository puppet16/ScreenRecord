import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import android.util.SparseLongArray
import cn.luck.screenrecord.record3.config.AudioConfig
import cn.luck.screenrecord.record3.encoder.AudioRecorderEncoder
import cn.luck.screenrecord.record3.encoder.BaseRecorderEncoder
import cn.luck.screenrecord.record3.encoder.IRecorderEncoder
import cn.luck.screenrecord.utils.LogUtil
import cn.luck.screenrecord.utils.WorkManager
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean


// MicRecorder 类实现了 IRecorderEncoder 接口，用于录制音频数据并进行编码
class MicRecorder(private val config: AudioConfig) : IRecorderEncoder {
    // 日志标签
    private val TAG = "MicRecorder"


    // 用于处理音频编码的类实例
    private val encoder: AudioRecorderEncoder = AudioRecorderEncoder(config)

    // 用于音频录制的 AudioRecord 实例，协程中使用
    private var mic: AudioRecord? = null

    // 原子布尔变量，用于标识是否强制停止录音
    private val forceStop = AtomicBoolean(false)

    // 编码回调接口
    private var callback: BaseRecorderEncoder.BaseRecorderEncoderCallback? = null

    // 管理协程任务的工作管理器
    private val workManager = WorkManager()

    private val channelsSampleRate = config.sampleRate * config.channelCount

    private val mFramesUsCache = SparseLongArray(2)
    companion object {
        private const val LAST_FRAME_ID = -1

    }




    private var lastInputTimestamp: Long = 0
    private var lastOutputTimestamp: Long = 0

    // 设置编码回调接口
    override fun setCallback(callback: IRecorderEncoder.IRecorderEncoderCallback?) {
        this.callback = callback as? BaseRecorderEncoder.BaseRecorderEncoderCallback
    }

    // 准备录音器和编码器
    override fun prepare() {
        // 使用 WorkManager 异步执行录音准备工作
        workManager.addWork({
            prepareInternal()  // 调用内部的准备方法
        }, object : WorkManager.WorkCallback<Unit> {
            override fun onSuccess(data: Unit) {
                // 准备成功时的回调处理
            }

            override fun onError(exception: Throwable) {
                // 准备失败时触发回调
                callback?.onError(this@MicRecorder, exception)
            }
        })
    }

    // 内部的录音准备方法，使用协程执行
    private suspend fun prepareInternal() {
        // 创建 AudioRecord 实例并开始录音
        mic = createAudioRecord(
            config.sampleRate,
            config.channelCount,
            AudioFormat.ENCODING_PCM_16BIT
        )
        mic?.startRecording() ?: throw IOException("Failed to start recording")

        startAsyncEncoding()
        // 准备编码器
        encoder.prepare()
    }

    private fun startAsyncEncoding() {
        // 设置编码器的回调，用于异步处理输入输出缓冲区
        encoder.setCallback(object : BaseRecorderEncoder.BaseRecorderEncoderCallback() {
            override fun onInputBufferAvailable(encoder: BaseRecorderEncoder?, index: Int) {
//                // 当输入缓冲区可用时调用此方法
                feedAudioEncoder(index)
            }

            override fun onOutputBufferAvailable(
                encoder: BaseRecorderEncoder?,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
//                // 当输出缓冲区可用时调用此方法
                drainOutput(index, info)
            }

            override fun onError(encoder: IRecorderEncoder?, exception: Throwable?) {
                // 处理编码器的错误
                LogUtil.e(TAG, "Error during encoding: ${exception?.message}")
                // 这里可以根据需要停止编码或执行其他错误处理逻辑
            }

            override fun onOutputFormatChanged(
                encoder: BaseRecorderEncoder?,
                format: MediaFormat?
            ) {
                // 处理输出格式的变化
                LogUtil.d(TAG, "Output format changed: $format")
                callback?.onOutputFormatChanged(encoder, format)
            }

        })
    }

    // 处理编码器输出的音频数据
    private fun drainOutput(index: Int, info: MediaCodec.BufferInfo) {
        LogUtil.d(TAG, "录音 drainOutput() index=$index, info=${info.presentationTimeUs}")
        if (forceStop.get()) return
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // 如果输出格式发生变化，触发回调
            callback?.onOutputFormatChanged(encoder, encoder.getRecorderEncoder().outputFormat)
        } else if (index >= 0) {
            // 如果输出缓冲区可用，触发回调
            val current = info.presentationTimeUs
            if (current <= lastOutputTimestamp) {
                LogUtil.e(TAG,"音频输出时过滤无序的帧 timestamp: $current, lastTimestamp=$lastOutputTimestamp")
                return
            }
            lastOutputTimestamp = current
            callback?.onOutputBufferAvailable(encoder, index, info)
        } else {
            LogUtil.d(TAG, "没有可用缓冲区")
        }
    }

    // 将音频数据送入编码器
    private fun feedAudioEncoder(index: Int) {
        // 如果索引无效或已强制停止，则不执行任何操作
        if (index < 0 || forceStop.get()) return
        val r = mic ?: return
        // 判断是否结束录音
        val eos = r.recordingState == AudioRecord.RECORDSTATE_STOPPED
        val frame = encoder.getInputBuffer(index)
        if (frame == null) {
            LogUtil.e(TAG, "feedAudioEncoder() index=$index, frame is null")
            return
        }
        // 从麦克风读取音频数据
        val read = if (!eos) {
            r.read(frame, frame.limit())
        } else {
            0
        }

        // 计算时间戳
        val pstTs = calculateFrameTimestamp(read * 8)

        if (pstTs <= lastInputTimestamp) {
            LogUtil.e(TAG,"音频输入时过滤无序的帧 timestamp: $pstTs, lastTimestamp=$lastInputTimestamp")
            return
        }
        lastInputTimestamp = pstTs
        LogUtil.d(TAG, "录音写入缓冲区：index=$index, time=$pstTs")
        // 设置标志位
        val flags =
            if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else MediaCodec.BUFFER_FLAG_KEY_FRAME

        // 将音频数据送入编码器
        encoder.queueInputBuffer(index, frame.position(), read, pstTs, flags)
    }

    fun getOutputBuffer(index: Int): ByteBuffer? {
        LogUtil.d(TAG, "录音 getOutputBuffer() index=$index")
        if (forceStop.get()) return null
        return encoder.getOutputBuffer(index)
    }


    fun releaseOutputBuffer(index: Int) {
        LogUtil.d(TAG, "录音 releaseOutputBuffer() index=$index")
//        if (forceStop.get()) return
//        workManager.addWork({
            if (!forceStop.get()) {
                encoder.releaseOutputBuffer(index)
            }
//        })
    }

    // 停止录音
    override fun stop() {
        LogUtil.d(TAG, "录音 stop()")
        // 设置强制停止标志
        forceStop.set(true)
        // 使用 WorkManager 异步执行停止录音和编码的任务
        workManager.addWork({
            mic?.stop()  // 停止录音
            encoder.stop()  // 停止编码
        }, object : WorkManager.WorkCallback<Unit> {
            override fun onSuccess(data: Unit) {
                // 成功停止时的回调处理
                LogUtil.d(TAG, "已停止录音")
            }

            override fun onError(exception: Throwable) {
                // 停止过程中出错时触发回调
                callback?.onError(this@MicRecorder, exception)
            }
        })
    }

    // 释放录音和编码资源
    override fun release() {
        LogUtil.d(TAG, "录音 release()")
        // 使用 WorkManager 异步执行资源释放任务
        workManager.addWork({
            mic?.release()  // 释放 AudioRecord 资源
            mic = null
            encoder.release()  // 释放编码器资源
        }, object : WorkManager.WorkCallback<Unit> {
            override fun onSuccess(data: Unit) {
                // 成功释放资源时的回调处理
            }

            override fun onError(exception: Throwable) {
                // 释放资源过程中出错时触发回调
                callback?.onError(this@MicRecorder, exception)
            }
        })
        workManager.release()  // 释放工作管理器资源
    }

    /**
     * Gets presentation time (us) of polled frame.
     * 1 sample = 16 bit
     */
    private fun calculateFrameTimestamp(totalBits: Int): Long {
        val samples = totalBits shr 4
        var frameUs = mFramesUsCache[samples, -1]
        if (frameUs == -1L) {
            frameUs = samples * 1000000L / channelsSampleRate
            mFramesUsCache.put(samples, frameUs)
        }
        var timeUs = SystemClock.elapsedRealtimeNanos() / 1000
        // accounts the delay of polling the audio sample data
        timeUs -= frameUs
        var currentUs: Long
        val lastFrameUs = mFramesUsCache[LAST_FRAME_ID, -1]
        currentUs = if (lastFrameUs == -1L) { // it's the first frame
            timeUs
        } else {
            lastFrameUs
        }
        LogUtil.d(
            TAG,
            "count samples pts: $currentUs, time pts: $timeUs, samples: $samples"
        )
        // maybe too late to acquire sample data
        if (timeUs - currentUs >= frameUs shl 1) {
            // reset
            currentUs = timeUs
        }
        mFramesUsCache.put(LAST_FRAME_ID, currentUs + frameUs)
        return currentUs
    }

    // 创建并初始化 AudioRecord 实例
    @SuppressLint("MissingPermission")
    private fun createAudioRecord(
        sampleRateInHz: Int,
        channelCount: Int,
        audioFormat: Int
    ): AudioRecord? {
        // 获取 AudioRecord 所需的最小缓冲区大小
        val minBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelCount, audioFormat)
        return if (minBytes > 0) {
            // 如果缓冲区大小有效，创建 AudioRecord 实例
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateInHz,
                channelCount,
                audioFormat,
                minBytes * 2
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    // 如果初始化失败，返回 null
                    Log.e(TAG, "Failed to initialize AudioRecord")
                    null
                }
            }
        } else {
            // 如果缓冲区大小无效，记录错误日志并返回 null
            Log.e(
                TAG,
                String.format(
                    Locale.US,
                    "Bad arguments: getMinBufferSize(%d, %d, %d)",
                    sampleRateInHz,
                    channelCount,
                    audioFormat
                )
            )
            null
        }
    }
}
