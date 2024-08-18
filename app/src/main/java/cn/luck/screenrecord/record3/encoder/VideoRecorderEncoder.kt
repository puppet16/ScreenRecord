package cn.luck.screenrecord.record3.encoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import cn.luck.screenrecord.record3.config.VideoConfig

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/17
 * desc    视频编码器
 * ============================================================
 **/
class VideoRecorderEncoder (private val config: VideoConfig): BaseRecorderEncoder(config.codecName) {

    private var surface: Surface? = null

    override fun createMediaFormat(): MediaFormat {
        return config.toFormat()
    }

    override fun onEncoderConfigured(encoder: MediaCodec) {
        surface = encoder.createInputSurface()
    }


    fun getInputSurface(): Surface {
        return surface ?: throw NullPointerException("surface is null")
    }

    override fun release() {
        surface?.let {
            it.release()
            surface = null
        }
        super.release()
    }
}