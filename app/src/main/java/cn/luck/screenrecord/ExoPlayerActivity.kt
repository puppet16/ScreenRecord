package cn.luck.screenrecord

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import cn.luck.screenrecord.utils.LogUtil
import cn.luck.screenrecord.utils.FileUtils
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/17
 * desc    描述
 * ============================================================
 **/
@SuppressLint("RestrictedApi")
class ExoPlayerActivity: ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var tvName: TextView
    private lateinit var filePathList: List<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exoplayer)

        playerView = findViewById(R.id.player_view)
        tvName = findViewById(R.id.tvName)
        // 初始化 ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView.player = player



        // 设置并准备播放器
        player.setMediaSource(generateMediaSource())
        player.prepare()
        player.playWhenReady = true

        playerView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

        // 监听播放状态的变化
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                // 更新当前播放视频的位置
                mediaItem?.let {
                    val currentIndex = player.currentMediaItemIndex
                    updateVideoList(currentIndex)
                }
            }
        })

        // 初始化时显示视频列表
        updateVideoList(0)

    }


    private fun updateVideoList(currentIndex: Int) {
        val spannableString = SpannableString(filePathList.joinToString("\n"))

        filePathList.forEachIndexed { index, name ->
            val start = spannableString.toString().indexOf(name)
            val end = start + name.length

            if (index == currentIndex) {
                // 当前播放的视频名称标红
                spannableString.setSpan(
                    ForegroundColorSpan(Color.RED),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                // 其余视频名称设置为默认颜色
                spannableString.setSpan(
                    ForegroundColorSpan(Color.GREEN),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

        }

        tvName.text = spannableString
    }
    private fun generateMediaSource(): ConcatenatingMediaSource {
        // 播放列表
        var dirPath = (intent.getStringExtra("dirPath") ?: "")

        if (!dirPath.contains("xxx")) {
            dirPath +="/xxx"
        }
        filePathList = FileUtils.getFileListByDirPath(dirPath)
        LogUtil.d("ExoPlayerActivity", "播放列表：$filePathList")
        val uris = filePathList.map {
            Uri.parse(it)
        }
        val concatenatingMediaSource = ConcatenatingMediaSource()
        for (uri in uris) {
            val mediaSource = ProgressiveMediaSource.Factory(
                DefaultDataSourceFactory(
                    this,
                    Util.getUserAgent(this, "yourApplicationName")
                )
            )
                .createMediaSource(MediaItem.fromUri(uri))
            concatenatingMediaSource.addMediaSource(mediaSource)
        }
        return concatenatingMediaSource
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onResume() {
        super.onResume()
        player.playWhenReady = true
    }
}