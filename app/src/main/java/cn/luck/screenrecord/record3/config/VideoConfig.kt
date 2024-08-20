package cn.luck.screenrecord.record3.config

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/17
 * desc    描述
 * ============================================================
 **/
class VideoConfig private constructor(
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val framerate: Int,
    val iframeInterval: Int,
    val codecName: String?,
    val mimeType: String?,
    val codecProfileLevel: CodecProfileLevel?
) {

    companion object {
        private const val VIDEO_BIT_RATE = 6000000 // 视频比特率
        private const val VIDEO_FRAME_RATE = 30 // 视频帧率

        const val DISPLAY_WIDTH = 1920 // 显示宽度
        const val DISPLAY_HEIGHT = 1080 // 显示高度

        const val VIDEO_AVC = MediaFormat.MIMETYPE_VIDEO_AVC // H.264 Advanced Video Coding
        const val AUDIO_AAC = MediaFormat.MIMETYPE_AUDIO_AAC // H.264 Advanced Audio Coding

    }



    class Builder {
        private var width: Int = 0
        private var height: Int = 0
        private var bitrate: Int = 0
        private var framerate: Int = 0
        private var iframeInterval: Int = 0
        private var codecName: String? = null
        private var mimeType: String? = null
        private var codecProfileLevel: CodecProfileLevel? = null

        fun setWidth(width: Int) = apply { this.width = width }
        fun setHeight(height: Int) = apply { this.height = height }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setFramerate(framerate: Int) = apply { this.framerate = framerate }
        fun setIframeInterval(iframeInterval: Int) = apply { this.iframeInterval = iframeInterval }
        fun setCodecName(codecName: String?) = apply { this.codecName = codecName }
        fun setMimeType(mimeType: String?) = apply { this.mimeType = mimeType }
        fun setCodecProfileLevel(codecProfileLevel: CodecProfileLevel?) = apply { this.codecProfileLevel = codecProfileLevel }

        fun build() = VideoConfig(
            width, height, bitrate, framerate, iframeInterval,
            codecName, mimeType, codecProfileLevel
        )
    }


    fun toFormat(): MediaFormat {
        val format = MediaFormat.createVideoFormat(mimeType!!, width, height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, framerate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframeInterval)
        if (codecProfileLevel != null && codecProfileLevel.profile != 0 && codecProfileLevel.level != 0) {
            format.setInteger(MediaFormat.KEY_PROFILE, codecProfileLevel.profile)
            format.setInteger("level", codecProfileLevel.level)
        }
        // maybe useful
        // format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 10_000_000);
        return format
    }


}