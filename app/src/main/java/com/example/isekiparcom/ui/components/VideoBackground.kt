package com.example.isekiparcom.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource

@Composable
fun VideoBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            val playerView = PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false
                setBackgroundColor(android.graphics.Color.WHITE)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

            }

            val player = ExoPlayer.Builder(ctx).build()

            val videoUri = Uri.parse("android.resource://${ctx.packageName}/raw/background")
            val mediaItem = MediaItem.fromUri(videoUri)

            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.prepare()
            player.playWhenReady = true

            playerView.player = player
            playerView
        },
        modifier = modifier,
        onRelease = { view ->
            view.player?.release()
        }
    )
}
