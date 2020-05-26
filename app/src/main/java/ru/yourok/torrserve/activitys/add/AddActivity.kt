package ru.yourok.torrserve.activitys.add

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_add.*
import org.json.JSONObject
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import kotlin.concurrent.thread

class AddActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        thread {
            if (Api.serverEcho().isEmpty()) {
                App.Toast(getString(R.string.server_not_responding))
                finish()
            }
        }

        setContentView(R.layout.activity_add)

        editTextTorrLink.requestFocus()

        buttonAdd.setOnClickListener {
            thread {
                try {
                    val title = editTextTorrTitle?.text?.toString() ?: ""
                    val poster = editTextTorrPoster?.text?.toString() ?: ""
                    var info = ""

                    val js = JSONObject()
                    if (poster.isNotEmpty())
                        js.put("poster_path", poster)
                    if (title.isNotEmpty())
                        js.put("title", title)
                    info = js.toString(0)

                    Api.torrentAdd(editTextTorrLink.text.toString().trim(), title, info, true)
                    finish()
                } catch (e: Exception) {
                    val msg = e.message ?: getString(R.string.error_add_torrent)
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        buttonCancel.setOnClickListener {
            finish()
        }
    }
}
