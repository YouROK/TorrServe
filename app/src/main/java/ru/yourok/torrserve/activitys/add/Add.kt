package ru.yourok.torrserve.activitys.add

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.activitys.play.PlayActivity

open class Add : AppCompatActivity() {

    protected var playIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var torrentLink = ""

        if (intent.action != null && intent.action.equals(Intent.ACTION_VIEW)) {
            intent.data?.let {
                torrentLink = it.toString()
            }
        }

        ///Intent send
        if (intent.action != null && intent.action.equals(Intent.ACTION_SEND)) {
            if (intent.getStringExtra(Intent.EXTRA_TEXT) != null)
                torrentLink = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (intent.extras.get(Intent.EXTRA_STREAM) != null)
                torrentLink = intent.extras.get(Intent.EXTRA_STREAM).toString()
        }

        if (torrentLink.isEmpty()) {
            finish()
            return
        }

        playIntent = Intent(this, PlayActivity::class.java).also {
            it.setData(Uri.parse(torrentLink))
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            it.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            it.action = Intent.ACTION_VIEW

            intent?.extras?.apply {
                keySet().forEach { key ->
                    when (key.toLowerCase()) {
                        "poster" -> it.putExtra("Poster", this.getString(key))
                        "title" -> it.putExtra("Title", this.getString(key))
                        "info" -> it.putExtra("Info", this.getString(key))
                        "season" -> it.putExtra("Season", this.getInt(key))
                        "episode" -> it.putExtra("Episode", this.getInt(key))
                    }
                }
            }
        }
    }

    open fun startPlayActivity() {
        playIntent?.let {
            startActivity(it)
            finish()
        }
    }
}