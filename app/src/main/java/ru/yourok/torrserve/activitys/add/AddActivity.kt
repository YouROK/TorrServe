package ru.yourok.torrserve.activitys.add

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_add.*
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
                    Api.torrentAdd(editTextTorrLink.text.toString().trim(), "", "", true)
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
