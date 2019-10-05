package ru.yourok.torrserve.activitys.add

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import ru.yourok.torrserve.activitys.play.PlayActivity


class AddPermanent : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var torrentLink = ""
        var title = ""
        var poster = ""
        var info = ""

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

        if (intent.hasExtra("Poster"))
            poster = intent.getStringExtra("Poster")
        if (intent.hasExtra("poster"))
            poster = intent.getStringExtra("poster")
        if (intent.hasExtra("Title"))
            title = intent.getStringExtra("Title")
        if (intent.hasExtra("title"))
            title = intent.getStringExtra("title")
        if (intent.hasExtra("info"))
            info = intent.getStringExtra("info")

        val vintent = Intent(this, PlayActivity::class.java)
        vintent.setData(Uri.parse(torrentLink))
        vintent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        vintent.action = Intent.ACTION_VIEW
        vintent.putExtra("Title", title)
        vintent.putExtra("Poster", poster)
        vintent.putExtra("Info", info)
        startActivity(vintent)

        finish()
    }
}