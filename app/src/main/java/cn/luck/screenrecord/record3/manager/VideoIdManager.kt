package cn.luck.screenrecord.record3.manager

import cn.luck.screenrecord.record3.config.BindConfig

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/19
 * desc    用于生成本次录屏的唯一标识
 * ============================================================
 **/
class VideoIdManager {

    private var videoId: String = ""

    private var bindConfig: BindConfig? = null

    fun setBindConfig(bindConfig: BindConfig) {
        this.bindConfig = bindConfig
    }


    fun getVideoId(): String {
        if (videoId.isEmpty()) {
            videoId = bindConfig?.let {
                "${it.lessonId}_${it.studentCode}_${System.currentTimeMillis()}"
            }?:"recordId"
        }

        return videoId
    }
}