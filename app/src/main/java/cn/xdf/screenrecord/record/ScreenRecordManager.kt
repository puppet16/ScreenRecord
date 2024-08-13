package cn.xdf.screenrecord.record

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/13
 * desc    描述
 * ============================================================
 **/
class ScreenRecordManager {

    var myService: SpliceScreenRecordService? = null
    var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SpliceScreenRecordService.ScreenRecordBinder
            myService = binder.recordService
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            myService = null
            isBound = false
        }
    }

    fun onStart(context: Context) {
        Intent(context, SpliceScreenRecordService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun startRecording(resultCode: Int, resultData: Intent) {
        myService?.startRecording(resultCode, resultData)
    }

    fun stopRecording() {
        myService?.stopRecording()
    }

    fun onStop(context: Context) {
        if (isBound) {
            context.unbindService(connection)
            isBound = false
        }
    }
}