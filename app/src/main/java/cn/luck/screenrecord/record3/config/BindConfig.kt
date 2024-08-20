package cn.luck.screenrecord.record3.config

import com.google.gson.Gson

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/19
 * desc    本次录屏绑定的课次信息
 * ============================================================
 **/
data class BindConfig(
    var lessonId: String = "",
    var studentCode: String = ""
) {
    override fun toString(): String {
        return Gson().toJson(this)
    }
}