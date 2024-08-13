package cn.xdf.screenrecord

import android.annotation.SuppressLint
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xdf.screenrecord.record.ScreenRecordManager
import cn.xdf.screenrecord.ui.theme.ScreenRecordTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val REQUEST_CODE_SCREEN_RECORD = 1
    private val manager = ScreenRecordManager()
    private val TAG = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenRecordTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContentContainer()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        manager.onStart(this)
    }

    @Composable
    fun ContentContainer() {
        Column(Modifier.fillMaxSize()) {
            ButtonList(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(20.dp))
            ShowTime()
        }
    }

    @Composable
    fun ShowTime() {
        Box(modifier = Modifier.fillMaxSize()) {
            val currentTime by remember(System.currentTimeMillis()) {
                derivedStateOf {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                     dateFormat.format(Date(System.currentTimeMillis()))
                }
            }

            Text(
                modifier = Modifier.align(Alignment.Center),
                text = currentTime,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    fun ButtonList(modifier: Modifier = Modifier) {
        Row(modifier) {
            Button(modifier = Modifier.size(200.dp, 50.dp), onClick = { startRecord() }) {
                Text(text = "开始录屏")
            }
            Spacer(modifier = Modifier.width(30.dp))
            Button(modifier = Modifier.size(200.dp, 50.dp), onClick = { stopRecord() }) {
                Text(text = "停止录屏")
            }
        }
    }

    @SuppressLint("ServiceCast")
    private fun startRecord() {
        // 请求屏幕录制权限
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_CODE_SCREEN_RECORD
        )
    }

    private fun stopRecord() {
        manager.stopRecording()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
//        Log.e(TAG, data.extras)
        if (requestCode == REQUEST_CODE_SCREEN_RECORD) {
            if (resultCode == RESULT_OK && data != null) {
                // 开始录制屏幕
                manager.startRecording(resultCode, data)

            } else {
                // 用户拒绝了权限请求
                Toast.makeText(this, "Screen recording permission denied", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // 停止所有录制并释放资源
        manager.onStop(this)
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ScreenRecordTheme {
    }
}