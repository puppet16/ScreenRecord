package cn.luck.screenrecord.record3.encoder

import android.media.MediaFormat
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

    override fun createMediaFormat(): MediaFormat {
        return config.toFormat()
    }
}