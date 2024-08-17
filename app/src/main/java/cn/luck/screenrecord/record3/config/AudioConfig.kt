package cn.luck.screenrecord.record3.config

import android.media.MediaFormat
import com.google.gson.Gson

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/17
 * desc    描述
 * ============================================================
 **/
class AudioConfig private constructor(
    val codecName: String,
    val mimeType: String,
    val bitRate: Int,
    val sampleRate: Int,
    val channelCount: Int,
    val profile: Int
) {

    class Builder {
        private var codecName: String = ""
        private var mimeType: String = ""
        private var bitRate: Int = 0
        private var sampleRate: Int = 0
        private var channelCount: Int = 0
        private var profile: Int = 0

        fun setCodecName(codecName: String) = apply { this.codecName = codecName }
        fun setMimeType(mimeType: String) = apply { this.mimeType = mimeType }
        fun setBitRate(bitRate: Int) = apply { this.bitRate = bitRate }
        fun setSampleRate(sampleRate: Int) = apply { this.sampleRate = sampleRate }
        fun setChannelCount(channelCount: Int) = apply { this.channelCount = channelCount }
        fun setProfile(profile: Int) = apply { this.profile = profile }

        fun build() = AudioConfig(codecName, mimeType, bitRate, sampleRate, channelCount, profile)
    }

    fun toFormat(): MediaFormat {
        val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        return format
    }

    override fun toString(): String {
        return Gson().toJson(this)
    }
}