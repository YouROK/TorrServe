package ru.yourok.torrserve.ui.fragments.rutor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.ui.activities.play.addTorrent
import ru.yourok.torrserve.ui.fragments.TSFragment


class RutorFragment : TSFragment() {

    private val torrsAdapter = TorrentsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.rutor_fragment, container, false)
    }

    private var jobSearch: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.apply {
            findViewById<EditText>(R.id.etSearch).apply {
                setOnEditorActionListener { textView, actionId, keyEvent ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        jobSearch?.let { it.cancel() }
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
                                Log.d("", "onTextChanged: ${it.size}")
                                withContext(Dispatchers.Main) {
                                    torrsAdapter.set(it)
                                }
                            }
                        }
                    }

                    true
                }
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable) {
                        val query = s.toString().trim()
                        if (query.isNotBlank() && query.length >= 3) {
                            jobSearch?.let { it.cancel() }
                            jobSearch = lifecycleScope.launch(Dispatchers.IO) {
                                val result = try {
                                    Api.searchTorrents(query)
                                } catch (e: Exception) {
                                    e.message?.let {
                                        App.toast(it)
                                    }
                                    null
                                }
                                result?.let {
                                    Log.d("", "onTextChanged: ${it.size}")
                                    withContext(Dispatchers.Main) {
                                        torrsAdapter.set(it)
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
        }
    }
}