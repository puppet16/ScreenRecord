package cn.luck.screenrecord.record3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat.MIMETYPE_AUDIO_AAC
import android.media.MediaFormat.MIMETYPE_VIDEO_AVC
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.luck.screenrecord.R
import cn.luck.screenrecord.record3.config.AudioConfig
import cn.luck.screenrecord.record3.config.VideoConfig
import cn.luck.screenrecord.record3.recorder.ScreenRecorder
import cn.luck.screenrecord.record3.utils.RecordFileManager
import cn.luck.screenrecord.utils.LogUtil
import com.google.gson.Gson
import java.io.File


/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/13
 * desc    描述
 * ============================================================
 **/
class ScreenRecordService3 : Service() {


    companion object {
        private val TAG = "ScreenRecordService3"

        const val CHANNEL_ID = "ForegroundServiceChannel"
        const val NOTIFICATION_ID = 1

        private const val VIDEO_BIT_RATE = 6000000 // 视频比特率
        private const val VIDEO_FRAME_RATE = 30 // 视频帧率
        private const val AUDIO_BIT_RATE = 96000 // 音频比特率
        private const val AUDIO_SAMPLE_RATE = 44100 // 音频采样率

        const val DISPLAY_WIDTH = 1920 // 显示宽度
        const val DISPLAY_HEIGHT = 1080 // 显示高度

        const val VIDEO_AVC = MIMETYPE_VIDEO_AVC // H.264 Advanced Video Coding
        const val AUDIO_AAC = MIMETYPE_AUDIO_AAC // H.264 Advanced Audio Coding

    }

    private var recorder: ScreenRecorder? = null


    override fun onCreate() {
        super.onCreate()
        showNotification()

    }

    fun startRecording(resultCode: Int, resultData: Intent, journeyId: String) {
        LogUtil.d(
            TAG,
            "startRecording：journeyId=$journeyId, resultCode=$resultCode, resultData=${
                Gson().toJson(
                    resultData
                )
            }"
        )
        val videoConfig = createVideoConfig()
        val audioConfig = createAudioConfig()
        recorder = newRecorder(videoConfig, audioConfig)
        recorder?.startRecord(resultCode, resultData)
    }


    private fun newRecorder(videoConfig: VideoConfig, audioConfig: AudioConfig): ScreenRecorder {
        val recorder = ScreenRecorder(baseContext, videoConfig, audioConfig)
        recorder.setCallback(object : ScreenRecorder.Callback {
            override fun onStop(error: Throwable?) {
                LogUtil.i(TAG, "recorder error! error=${error?.printStackTrace()}")
            }

            override fun onStart() {
                LogUtil.i(TAG, "录制开始了")

            }

            override fun onRecording(presentationTimeUs: Long) {
                LogUtil.i(TAG, "正在录制中， presentationTimeUs=$presentationTimeUs")
            }

        })
        return recorder
    }

    private fun createAudioConfig(): AudioConfig {
        return AudioConfig.Builder()
            .setBitRate(AUDIO_BIT_RATE)
            .setMimeType(AUDIO_AAC)
            .setChannelCount(1)
            .setSampleRate(AUDIO_SAMPLE_RATE)
            .setProfile(CodecProfileLevel.AACObjectMain)
            .build()

    }

    private fun createVideoConfig(): VideoConfig {
        return VideoConfig.Builder()
            .setMimeType(VIDEO_AVC)
            .setBitrate(VIDEO_BIT_RATE)
            .setFramerate(VIDEO_FRAME_RATE)
            .setHeight(DISPLAY_HEIGHT)
            .setWidth(DISPLAY_WIDTH)
            .setIframeInterval(1)
            .build()
    }


    fun stopRecording() {
        recorder?.quit()
    }

    /**
     * 前台服务必须要显示一个通知
     */
    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)

        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("My Foreground Service")
            .setContentText("Service is running...")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    fun getRecorderFileDirPath(): String {
        return recorder?.getRecorderFileDirPath() ?: ""
    }

    override fun onBind(intent: Intent?): IBinder {
        LogUtil.d(TAG, "onBind")
        return ScreenRecordBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        LogUtil.d(TAG, "onUnbind")
        recorder?.quit()
        return super.onUnbind(intent)
    }

    override fun unbindService(conn: ServiceConnection) {
        LogUtil.d(TAG, "unbindService")
        super.unbindService(conn)
    }


    inner class ScreenRecordBinder : Binder() {
        val recordService: ScreenRecordService3
            get() = this@ScreenRecordService3
    }


}