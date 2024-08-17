package cn.luck.screenrecord

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import cn.luck.screenrecord.record.MultiScreenRecordService
import cn.luck.screenrecord.utils.LogUtil

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/13
 * desc    录屏服务的管理
 * ============================================================
 **/
class ScreenRecordManager {

    var myService: MultiScreenRecordService? = null
    var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MultiScreenRecordService.ScreenRecordBinder
            myService = binder.recordService
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            myService = null
            isBound = false
        }
    }

    companion object {
        private val TAG = "ScreenRecordManager"
    }

    fun onStart(context: Context) {

        LogUtil.d(TAG, "绑定录屏服务 onStart()")
        Intent(context, MultiScreenRecordService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun startRecording(resultCode: Int, resultData: Intent) {
        LogUtil.d(TAG, "开始录屏 startRecording()")
        myService?.startRecording(resultCode, resultData, "xxx")
    }

    fun stopRecording() {
        LogUtil.d(TAG, "停止录屏 stopRecording()")
        myService?.stopRecording()
    }

    fun onStop(context: Context) {
        LogUtil.d(TAG, "解绑录屏服务 onStop()")
        if (isBound) {
            context.unbindService(connection)
            isBound = false
        }
    }

    fun getRecorderFileDirPath(): String {
       return myService?.getRecorderFileDirPath()?:""
    }
}