package cn.xdf.screenrecord.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.xdf.screenrecord.R
import cn.xdf.screenrecord.util.LogUtil
import com.google.gson.Gson

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/13
 * desc    描述
 * ============================================================
 **/
class SpliceScreenRecordService : Service() {


    companion object {
        private val TAG = "ScreenRecordService"

        const val CHANNEL_ID = "ForegroundServiceChannel"
        const val NOTIFICATION_ID = 1
    }

    private var resultCode: Int = -1
    private var resultData: Intent? = null
    private var recorder: SpliceScreenRecorder? = null
    override fun onCreate() {
        super.onCreate()
        showNotification()
        recorder = SpliceScreenRecorder(baseContext)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        LogUtil.d(TAG, "onStartCommand")
        resultCode = intent.getIntExtra("code", -1)
        resultData = intent.getParcelableExtra("data")
        resultData?.let {
            LogUtil.d(
                TAG,
                "请求录屏结果：resultCode=$resultCode, resultData=${Gson().toJson(resultData)}"
            )
            recorder?.startScreenRecording(resultCode, it)
        } ?: run {
            LogUtil.e(TAG, "请求录屏失败")
        }


        return super.onStartCommand(intent, flags, startId)
    }

    fun startRecording(resultCode: Int, resultData: Intent) {
        LogUtil.d(
            TAG,
            "请求录屏结果：resultCode=$resultCode, resultData=${Gson().toJson(resultData)}"
        )
        recorder?.startScreenRecording(resultCode, resultData)
    }

    fun stopRecording() {
        recorder?.stopScreenRecording()
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


    override fun onBind(intent: Intent?): IBinder {
        LogUtil.d(TAG, "onBind")
        return ScreenRecordBinder()
    }

    override fun unbindService(conn: ServiceConnection) {
        LogUtil.d(TAG, "unbindService")
        recorder?.stopScreenRecording()
        super.unbindService(conn)
    }


    inner class ScreenRecordBinder : Binder() {
        val recordService: SpliceScreenRecordService
            get() = this@SpliceScreenRecordService
    }


}