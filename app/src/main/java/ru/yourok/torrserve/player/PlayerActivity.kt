package ru.yourok.torrserve.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Rational
import com.google.android.exoplayer2.ui.SimpleExoPlayerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.player.player.PlayerHolder
import ru.yourok.torrserve.player.player.PlayerState


class PlayerActivity : AppCompatActivity() {

    private val playerState by lazy { PlayerState() }
    private lateinit var playerHolder: PlayerHolder
    private var data: Uri? = null

    // Android lifecycle hooks.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        data = intent.data
        data ?: let {
            finish()
            return
        }

        // While the user is in the app, the volume controls should adjust the music volume.
        volumeControlStream = AudioManager.STREAM_MUSIC
        createPlayer()
    }

    override fun onStart() {
        super.onStart()
        startPlayer()
    }

    override fun onStop() {
        super.onStop()
        stopPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    // ExoPlayer related functions.
    private fun createPlayer() {
        playerHolder = PlayerHolder(this, playerState, findViewById(R.id.player_view))
    }

    private fun startPlayer() {
        data?.let {
            playerHolder.start(it)
        }
    }

    private fun stopPlayer() {
        playerHolder.stop()
    }

    private fun releasePlayer() {
        playerHolder.release()
    }

    // Picture in Picture related functions.
    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                    with(PictureInPictureParams.Builder()) {
                        val width = 16
                        val height = 9
                        setAspectRatio(Rational(width, height))
                        build()
                    })
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean,
                                               newConfig: Configuration?) {
        findViewById<SimpleExoPlayerView>(R.id.player_view).useController = !isInPictureInPictureMode
    }
}