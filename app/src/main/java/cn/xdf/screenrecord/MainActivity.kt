package cn.xdf.screenrecord

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import cn.xdf.screenrecord.record.ScreenRecorder
import cn.xdf.screenrecord.ui.theme.ScreenRecordTheme

class MainActivity : ComponentActivity() {

    private val screenRecorder: ScreenRecorder = ScreenRecorder(this)
    private val REQUEST_CODE_SCREEN_RECORD = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenRecordTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                }
            }
        }
    }

    @Composable
    fun ButtonList(modifier: Modifier = Modifier) {
        Row(modifier) {
            Button(modifier = Modifier.size(100.dp, 50.dp), onClick = { startRecord() }) {
                Text(text = "开始录屏")
            }
            Spacer(modifier = Modifier.width(30.dp))
            Button(modifier = Modifier.size(100.dp, 50.dp), onClick = { stopRecord() }) {
                Text(text = "停止录屏")
            }
        }
    }

    @SuppressLint("ServiceCast")
    private fun startRecord() {
        // 请求屏幕录制权限
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_CODE_SCREEN_RECORD
        )
    }

    private fun stopRecord() {
        screenRecorder.stopRecording()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_RECORD) {
            if (resultCode == RESULT_OK && data != null) {
                // 开始录制屏幕
                screenRecorder.startRecording(resultCode, data)
            } else {
                // 用户拒绝了权限请求
                Toast.makeText(this, "Screen recording permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止所有录制并释放资源
        screenRecorder.release()
    }
}



@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ScreenRecordTheme {
    }
}