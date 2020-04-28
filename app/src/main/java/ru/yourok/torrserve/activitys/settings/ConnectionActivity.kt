package ru.yourok.torrserve.activitys.settings

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_connection.*
import ru.yourok.torrserve.R
import ru.yourok.torrserve.adapters.HostAdapter
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.serverloader.ServerFile
import ru.yourok.torrserve.services.ServerService
import kotlin.concurrent.thread

class ConnectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        rvHosts.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ConnectionActivity)
            adapter = HostAdapter {
                etHost.setText(it)
                setHost(false)
            }
            addItemDecoration(DividerItemDecoration(this@ConnectionActivity, LinearLayout.VERTICAL))
        }

        btnFindHosts.setOnClickListener {
            update()
        }

        buttonCancel.setOnClickListener {
            finish()
        }

        buttonOk.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            setHost(true)
        }

        tvConnectedHost.text = Preferences.getCurrentHost()
        etHost.setText(Preferences.getCurrentHost())

        update()
    }

    private fun setHost(isFinishOnErr: Boolean) {
        thread {
            try {
                var host = etHost.text.toString()
                val uri = Uri.parse(host)

                if (uri.scheme == null)
                    host = "http://$host"

                if (uri.port == -1)
                    host += ":8090"

                Preferences.setCurrentHost(host)

                if (ServerFile.serverExists() && (host.toLowerCase().contains("localhost") || host.toLowerCase().contains("127.0.0.1"))) {
                    ServerService.start()
                    ServerService.wait(10)
                }

                if (Api.serverCheck(host).isEmpty()) {
                    App.Toast(getString(R.string.server_not_responding))
                    Handler(getMainLooper()).post {
                        progressBar.visibility = View.GONE
                    }
                    if (isFinishOnErr)
                        finish()
                    return@thread
                }

                val lst = Preferences.getHosts().toMutableList()
                lst.add(host)
                if (lst.size > 10)
                    lst.removeAt(0)
                Preferences.setHosts(lst)

                finish()
            } catch (e: Exception) {
                e.message?.let {
                    App.Toast(it)
                }
                Handler(getMainLooper()).post {
                    progressBar.visibility = View.GONE
                }
                if (isFinishOnErr)
                    finish()
            }
        }
    }

    private fun update() {
        progressBar.visibility = View.VISIBLE
        btnFindHosts.isEnabled = false
        val currHosts = (rvHosts.adapter as HostAdapter).update {
            Handler(getMainLooper()).post {
                progressBar.visibility = View.GONE
                btnFindHosts.isEnabled = true
            }
        }

        tvCurrentHost.text = currHosts.joinToString(", ")
    }
}
