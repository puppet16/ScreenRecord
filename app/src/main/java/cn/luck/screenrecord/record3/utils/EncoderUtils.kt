package cn.luck.screenrecord.record3.utils

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.util.SparseArray
import cn.luck.screenrecord.utils.LogUtil
import cn.luck.screenrecord.utils.WorkManager
import java.lang.reflect.Modifier

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

        private val sAACProfiles = SparseArray<String>()
        private val sAVCProfiles = SparseArray<String>()
        private val sAVCLevels = SparseArray<String>()
        private val sColorFormats = SparseArray<String>()

        private val workManager = WorkManager()

        fun findEncodersByTypeAsync(mimeType: String, callback: IMediaCodecInfoCallback) {
            workManager.addWork({ findEncoderByType(mimeType) }, object: WorkManager.WorkCallback<Array<MediaCodecInfo>> {
                override fun onSuccess(data: Array<MediaCodecInfo>) {
                    callback.onResult(data)
                }

                override fun onError(exception: Throwable) {
                    LogUtil.e(TAG, "findEncodersByTypeAsync error, exception=$exception")
                }

            })
        }


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
            if (sAVCProfiles.size() == 0 || sAVCLevels.size() == 0) {
                initProfileLevels()
            }
            var profile: String? = null
            var level: String? = null
            var i = sAVCProfiles.indexOfKey(avcProfileLevel.profile)
            if (i >= 0) {
                profile = sAVCProfiles.valueAt(i)
            }

            i = sAVCLevels.indexOfKey(avcProfileLevel.level)
            if (i >= 0) {
                level = sAVCLevels.valueAt(i)
            }

            if (profile == null) {
                profile = avcProfileLevel.profile.toString()
            }
            if (level == null) {
                level = avcProfileLevel.level.toString()
            }
            return "$profile-$level"
        }

        fun aacProfiles(): Array<String?> {
            if (sAACProfiles.size() == 0) {
                initProfileLevels()
            }
            val profiles = arrayOfNulls<String>(sAACProfiles.size())
            for (i in 0 until sAACProfiles.size()) {
                profiles[i] = sAACProfiles.valueAt(i)
            }
            return profiles
        }

        fun toProfileLevel(str: String): CodecProfileLevel? {
            if (sAVCProfiles.size() == 0 || sAVCLevels.size() == 0 || sAACProfiles.size() == 0) {
                initProfileLevels()
            }
            var profile = str
            var level: String? = null
            val i = str.indexOf('-')
            if (i > 0) { // AVC profile has level
                profile = str.substring(0, i)
                level = str.substring(i + 1)
            }
            val res = CodecProfileLevel()
            if (profile.startsWith("AVC")) {
                res.profile = keyOfValue(sAVCProfiles, profile)
            } else if (profile.startsWith("AAC")) {
                res.profile = keyOfValue(sAACProfiles, profile)
            } else {
                try {
                    res.profile = profile.toInt()
                } catch (e: NumberFormatException) {
                    return null
                }
            }
            if (level != null) {
                if (level.startsWith("AVC")) {
                    res.level = keyOfValue(sAVCLevels, level)
                } else {
                    try {
                        res.level = level.toInt()
                    } catch (e: NumberFormatException) {
                        return null
                    }
                }
            }
            return if (res.profile > 0 && res.level >= 0) res else null
        }


        private fun initProfileLevels() {
            val fields = CodecProfileLevel::class.java.getFields()
            for (f in fields) {
                if (f.modifiers and (Modifier.STATIC or Modifier.FINAL) == 0) {
                    continue
                }
                val name = f.getName()
                val target: SparseArray<String> = if (name.startsWith("AVCProfile")) {
                    sAVCProfiles
                } else if (name.startsWith("AVCLevel")) {
                    sAVCLevels
                } else if (name.startsWith("AACObject")) {
                    sAACProfiles
                } else {
                    continue
                }
                try {
                    target.put(f.getInt(null), name)
                } catch (e: IllegalAccessException) {
                    LogUtil.e(TAG, "initProfileLevels error, exception=$e")
                }
            }
        }

        fun toHumanReadable(colorFormat: Int): String {
            if (sColorFormats.size() == 0) {
                initColorFormatFields()
            }
            val i = sColorFormats.indexOfKey(colorFormat)
            return if (i >= 0) sColorFormats.valueAt(i) else "0x" + Integer.toHexString(
                colorFormat
            )
        }
        fun toColorFormat(str: String): Int {
            if (sColorFormats.size() == 0) {
                initColorFormatFields()
            }
            val color = keyOfValue(sColorFormats, str)
            if (color > 0) return color
            return if (str.startsWith("0x")) {
                str.substring(2).toInt(16)
            } else 0
        }

        private fun <T> keyOfValue(array: SparseArray<T>, value: T): Int {
            val size = array.size()
            for (i in 0 until size) {
                val t = array.valueAt(i)
                if (t === value || t == value) {
                    return array.keyAt(i)
                }
            }
            return -1
        }
        private fun initColorFormatFields() {
            // COLOR_
            val fields = CodecCapabilities::class.java.getFields()
            for (f in fields) {
                if (f.modifiers and (Modifier.STATIC or Modifier.FINAL) == 0) {
                    continue
                }
                val name = f.getName()
                if (name.startsWith("COLOR_")) {
                    try {
                        val value = f.getInt(null)
                        sColorFormats.put(value, name)
                    } catch (e: IllegalAccessException) {
                        LogUtil.e(TAG, "initColorFormatFields error, exception=$e")
                    }
                }
            }
        }

    }


    interface IMediaCodecInfoCallback {
        fun onResult(infos: Array<MediaCodecInfo>)
    }
}