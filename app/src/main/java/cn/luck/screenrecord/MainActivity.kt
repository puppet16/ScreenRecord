package cn.luck.screenrecord

import android.annotation.SuppressLint
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.luck.screenrecord.record.ScreenRecordManager
import cn.luck.screenrecord.ui.theme.ScreenRecordTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

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
        Box(Modifier.fillMaxSize()) {
            ShowTime()
        }
    }

    @Composable
    fun ShowTime() {
        val time = remember {
            mutableLongStateOf(System.currentTimeMillis() / 1000)
        }
        val color = remember {
            mutableIntStateOf(generateRandomColor())
        }
        val recording = remember {
            mutableStateOf(false)
        }
        LaunchedEffect(key1 = Unit) {
            while (true) {
                time.longValue = System.currentTimeMillis()
                if (recording.value) {
                    color.intValue = generateRandomColor()
                }
                delay(1000)
            }
        }

        val currentTime by remember(time) {
            derivedStateOf {
                val dateFormat =
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                dateFormat.format(Date(time.longValue))
            }
        }


        Box(modifier = Modifier.fillMaxSize().background(Color(color.intValue)).padding(bottom = 100.dp)) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = currentTime,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                color = Color(getComplementaryColor(color.intValue))
            )
            Button(modifier = Modifier.size(200.dp, 70.dp).align(Alignment.BottomCenter).padding(bottom = 20.dp), onClick = {
                if (recording.value) {
                    stopRecord()
                } else {
                    startRecord()
                }
                recording.value = !recording.value
            }) {
                Text(text = if(recording.value) "结束录屏" else "开始录屏")
            }
        }
    }


    private fun getComplementaryColor(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)

        // 计算对比色的色调 (hue)
        hsv[0] = (hsv[0] + 180) % 360

        // 返回对比色
        return android.graphics.Color.HSVToColor(hsv)
    }

    /**
     * 随机一个颜色，不含黑色和灰色
     * @return Int
     */
    private fun generateRandomColor(): Int {
        val hue = Random.nextInt(0, 360) // 色调范围 0-360
        val saturation = Random.nextFloat() * 0.5f + 0.5f // 饱和度范围 0.5-1.0，避免灰色
        val lightness = Random.nextFloat() * 0.4f + 0.6f // 亮度范围 0.6-1.0，避免黑色

        return android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation, lightness))
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

    override fun onStop() {
        super.onStop()
        manager.onStop(this)
    }


    override fun onDestroy() {
        // 停止所有录制并释放资源
        manager.onStop(this)
        super.onDestroy()
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ScreenRecordTheme {
    }
}