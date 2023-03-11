package ru.yourok.torrserve.ui.fragments.add

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.ui.activities.play.addTorrent
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.ui.fragments.rutor.TorrentsAdapter
import ru.yourok.torrserve.utils.Format.byteFmt
import ru.yourok.torrserve.utils.Format.durFmt
import ru.yourok.torrserve.utils.Format.speedFmt

class AddFragment : TSFragment() {

    private val torrsAdapter = TorrentsAdapter()
    private var jobSearch: Job? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.add_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.apply {
            findViewById<LinearLayout>(R.id.footer)?.visibility = View.VISIBLE
            findViewById<Button>(R.id.btnOK)?.setOnClickListener {
                val link = view.findViewById<EditText>(R.id.etMagnet)?.text?.toString() ?: ""
                val title = view.findViewById<EditText>(R.id.etTitle)?.text?.toString() ?: ""
                val poster = view.findViewById<EditText>(R.id.etPoster)?.text?.toString() ?: ""

                if (link.isNotBlank())
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            addTorrent("", link, title, poster, "", true)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            App.toast(e.message ?: getString(R.string.error_retrieve_data))
                        }
                    }
                popBackStackFragment()
            }
            findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
                popBackStackFragment()
            }

            findViewById<androidx.constraintlayout.widget.Group>(R.id.adder)?.visibility = View.VISIBLE
            findViewById<EditText>(R.id.etSearch).apply {
                setOnEditorActionListener { textView, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        jobSearch?.cancel()
                        jobSearch = lifecycleScope.launch(Dispatchers.IO) {
                            val result = try {
                                Api.searchTorrents(textView.text.toString().trim())
                            } catch (e: Exception) {
                                e.message?.let {
                                    App.toast(it)
                                }
                                null
                            }
                            result?.let {
                                if (BuildConfig.DEBUG) Log.d("*****", "onTextChanged: ${it.size}")
                                if (it.isNotEmpty())
                                    withContext(Dispatchers.Main) {
                                        torrsAdapter.set(it)
                                    }
                                else
                                    App.toast(R.string.no_torrents)
                            }
                        }
                    }
                    true
                }
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable) {
                        val query = s.toString().trim()
                        if (query.isNotBlank() && query.length >= 3) {
                            jobSearch?.cancel()
                            jobSearch = lifecycleScope.launch(Dispatchers.IO) {
                                if (BuildConfig.DEBUG) Log.d("*****", "Api.searchTorrents($query)")
                                val result = try {
                                    Api.searchTorrents(query)
                                } catch (e: Exception) {
                                    e.message?.let {
                                        App.toast(it)
                                    }
                                    null
                                }
                                result?.let {
                                    if (BuildConfig.DEBUG) Log.d("*****", "onTextChanged: ${it.size}")
                                    if (it.isNotEmpty())
                                        withContext(Dispatchers.Main) {
                                            torrsAdapter.set(it)
                                            view.findViewById<androidx.constraintlayout.widget.Group>(R.id.adder)?.visibility = View.GONE
                                            view.findViewById<LinearLayout>(R.id.footer)?.visibility = View.GONE
                                        }
                                }
                            }
                        }
                    }

                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                })
            }

            findViewById<RecyclerView>(R.id.rvRTorrents)?.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(context)
                adapter = torrsAdapter
                addItemDecoration(DividerItemDecoration(context, LinearLayout.VERTICAL))
            }

            torrsAdapter.onClick = {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        addTorrent("", it.Magnet, it.Title, "", "", true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        App.toast(e.message ?: getString(R.string.error_retrieve_data))
                    }
                }
                popBackStackFragment()
            }
            torrsAdapter.onLongClick = {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val torr = addTorrent("", it.Magnet, it.Title, "", "", false)
                        delay(500)
                        torr?.let { t ->
                            val data = Api.getFFP(t.hash, 0)
                            //Log.d("*****", "data: ${data.toString()}")
                            data?.let {
                                val format = it.format
                                val streams = it.streams
                                val size = byteFmt(it.format.size.toDouble())
                                val duration = durFmt(it.format.duration.toDouble())
                                val bitrate = speedFmt(it.format.bit_rate.toDouble()/8)
                                withContext(Dispatchers.Main) {
                                    App.toast(" ${format.tags.title}\n Format: ${format.format_long_name}\n Streams: ${format.nb_streams}\n Size: $size\n Runtime: $duration\n Bitrate: $bitrate", true)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        App.toast(e.message ?: getString(R.string.error_retrieve_data))
                    }
                }
            }
        }
    }
}