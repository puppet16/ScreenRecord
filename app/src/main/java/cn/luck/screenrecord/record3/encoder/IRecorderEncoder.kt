package cn.luck.screenrecord.record3.encoder

import java.io.IOException


/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/17
 * desc    描述
 * ============================================================
 **/
interface IRecorderEncoder {
    @Throws(IOException::class)
    fun prepare()

    fun stop()

    fun release()

    fun setCallback(callback: IRecorderEncoderCallback?)


    interface IRecorderEncoderCallback {
        fun onError(encoder: IRecorderEncoder?, exception: Throwable?)
    }

}