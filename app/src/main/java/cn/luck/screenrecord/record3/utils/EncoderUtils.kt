package cn.luck.screenrecord.record3.utils

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import cn.luck.screenrecord.utils.LogUtil
import java.lang.IllegalArgumentException

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/17
 * desc    描述
 * ============================================================
 **/
class EncoderUtils {
    companion object {

        private const val TAG = "EncoderUtils"


        /**
         * 根据 type 找对应的 MediaCodec
         * @param mimeType String
         * @return Array<MediaCodecInfo>
         */
        fun findEncoderByType(mimeType: String): Array<MediaCodecInfo> {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

            val infoList = codecList.codecInfos.filter {
                try {
                    it.isEncoder && it.getCapabilitiesForType(mimeType) != null
                } catch (e: IllegalArgumentException) {
                    LogUtil.e(TAG, "不支持，Exception=$e")
                    false
                }
            }

            return infoList.toTypedArray()
        }

        fun avcProfileLevelToString(avcProfileLevel: MediaCodecInfo.CodecProfileLevel): String {

        }



    }


    interface IMediaCodecInfoCallback {
        fun onResult(infos: Array<MediaCodecInfo>)
    }
}