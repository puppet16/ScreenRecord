package cn.luck.screenrecord.record3.encoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Looper
import cn.luck.screenrecord.utils.LogUtil
import java.io.IOException
import java.nio.ByteBuffer

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/17
 * desc    描述
 * ============================================================
 **/
abstract class BaseRecorderEncoder(private val codecName: String? = null) : IRecorderEncoder {

    private var recorderEncoder: MediaCodec? = null
    private var encoderCallback: BaseRecorderEncoderCallback? = null


    private val codecCallback: MediaCodec.Callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            encoderCallback?.onInputBufferAvailable(this@BaseRecorderEncoder, index)
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            encoderCallback?.onOutputBufferAvailable(this@BaseRecorderEncoder, index, info)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            encoderCallback?.onError(this@BaseRecorderEncoder, e)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            encoderCallback?.onOutputFormatChanged(this@BaseRecorderEncoder, format)
        }
    }

    companion object {
        private val TAG = "BaseRecorderEncoder"

    }

    /**
     * 该方法必须要在子线程里调用
     */
    override fun prepare() {
//        if (Looper.myLooper() == null || Looper.myLooper() == Looper.getMainLooper()) {
//            throw IllegalArgumentException("不能在主线程里调用该方法 prepare")
//        }
        LogUtil.printThreadInfo(TAG, "prepare()")
        if (recorderEncoder != null) {
            throw IllegalArgumentException("已经准备过了 mEncoder 不为空")
        }

        val format = createMediaFormat()
        LogUtil.d(TAG, "创建媒体格式：$format")
        val mimeType = format.getString(MediaFormat.KEY_MIME) ?: ""
        recorderEncoder = createMediaCodec(mimeType)
        recorderEncoder?.let {
            try {
                // NOTE: MediaCodec maybe crash on some devices due to null callback
                it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                onEncoderConfigured(it)
                it.setCallback(codecCallback)
                it.start()
            } catch (e: MediaCodec.CodecException) {
                LogUtil.e(TAG, "配置 MediaCodec 失败!\n  format$format, exception=$e")
                throw e
            }
        }
    }

    /**
     * 创建 MediaCodec
     * 优化使用 传进来的 codecName 进行创建
     * @param type String
     * @return MediaCodec
     * @throws IOException
     */

    @Throws(IOException::class)
    private fun createMediaCodec(type: String): MediaCodec {
        if (!codecName.isNullOrEmpty()) {
            try {
                return MediaCodec.createByCodecName(codecName)
            } catch (e: IOException) {
                LogUtil.d(TAG, "创建 MediaCodec 通过名字 '$codecName' 失败! \n exception=$e")
            }
        }
        return MediaCodec.createEncoderByType(type)
    }

    fun getRecorderEncoder(): MediaCodec {
        return recorderEncoder ?: throw NullPointerException("MediaCodec 不能为空")
    }

    /**
     * @throws NullPointerException if prepare() not call
     * @see MediaCodec.getOutputBuffer
     */
    fun getOutputBuffer(index: Int): ByteBuffer? {
        return getRecorderEncoder().getOutputBuffer(index)
    }

    /**
     * @throws NullPointerException if prepare() not call
     * @see MediaCodec.getInputBuffer
     */
    fun getInputBuffer(index: Int): ByteBuffer? {
        return getRecorderEncoder().getInputBuffer(index)
    }

    /**
     * @throws NullPointerException if prepare() not call
     * @see MediaCodec.queueInputBuffer
     * @see MediaCodec.getInputBuffer
     */
    fun queueInputBuffer(index: Int, offset: Int, size: Int, pstTs: Long, flags: Int) {
        getRecorderEncoder().queueInputBuffer(index, offset, size, pstTs, flags)
    }

    /**
     * @throws NullPointerException if prepare() not call
     * @see MediaCodec.releaseOutputBuffer
     */
    fun releaseOutputBuffer(index: Int) {
        getRecorderEncoder().releaseOutputBuffer(index, false)
    }

    /**
     * 创建媒体格式
     */
    abstract fun createMediaFormat(): MediaFormat

    /**
     * call immediately after {@link #getEncoder() MediaCodec}
     * configure with {@link #createMediaFormat() MediaFormat} success
     *
     * @param encoder MediaCodec
     */
    protected open fun onEncoderConfigured(encoder: MediaCodec) {}

    override fun stop() {
        LogUtil.d(TAG, "stop()")
        recorderEncoder?.stop()
    }

    override fun release() {
        LogUtil.d(TAG, "release()")
        recorderEncoder?.release()
        recorderEncoder = null
    }

    override fun setCallback(callback: IRecorderEncoder.IRecorderEncoderCallback?) {
        if (callback is BaseRecorderEncoderCallback) {
            if (recorderEncoder != null) {
                throw IllegalArgumentException("mEncoder 不为空啊")
            }
            this.encoderCallback = callback
        } else {
            throw IllegalArgumentException("请设置正确的 callback")
        }
    }


    abstract class BaseRecorderEncoderCallback : IRecorderEncoder.IRecorderEncoderCallback {
        open fun onInputBufferAvailable(encoder: BaseRecorderEncoder?, index: Int) {}

        open fun onOutputFormatChanged(encoder: BaseRecorderEncoder?, format: MediaFormat?) {}

        open fun onOutputBufferAvailable(
            encoder: BaseRecorderEncoder?,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
        }
    }
}