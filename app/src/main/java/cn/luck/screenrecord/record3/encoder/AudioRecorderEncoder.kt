package cn.luck.screenrecord.record3.encoder

import android.media.MediaFormat
import cn.luck.screenrecord.record3.config.AudioConfig

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/17
 * desc    音频编码器
 * ============================================================
 **/
class AudioRecorderEncoder(private val config: AudioConfig): BaseRecorderEncoder(config.codecName) {

    override fun createMediaFormat(): MediaFormat {
        return config.toFormat()
    }
}