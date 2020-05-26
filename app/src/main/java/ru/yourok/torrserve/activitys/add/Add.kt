package ru.yourok.torrserve.activitys.add

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.activitys.play.PlayActivity

open class Add : AppCompatActivity() {

    protected var torrentLink = ""
    protected var title = ""
    protected var poster = ""
    protected var info = ""
    protected var fileTemplate = ""
    protected var playIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        intent?.extras?.apply {
            keySet().forEach {
                when (it.toLowerCase()) {
                    "poster" -> poster = this.getString(it)
                    "title" -> title = this.getString(it)
                    "info" -> info = this.getString(it)
                    "filetemplate" -> fileTemplate = this.getString(it)
                }
            }
        }

        playIntent = Intent(this, PlayActivity::class.java).also {
            it.setData(Uri.parse(torrentLink))
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            it.action = Intent.ACTION_VIEW
            it.putExtra("Title", title)
            it.putExtra("Poster", poster)
            it.putExtra("Info", info)
            it.putExtra("FileTemplate", fileTemplate)
        }
    }
}