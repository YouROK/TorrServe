package ru.yourok.torrserve.ui.fragments.add

import android.annotation.SuppressLint
import android.app.Instrumentation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.BTSets
import ru.yourok.torrserve.ui.activities.play.addTorrent
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.ui.fragments.rutor.TorrentsAdapter
import ru.yourok.torrserve.utils.Format
import ru.yourok.torrserve.utils.TorrentHelper

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
            // FOOTER
            findViewById<LinearLayout>(R.id.footer)?.visibility = View.VISIBLE
            findViewById<Button>(R.id.btnOK)?.setOnClickListener {
                val link = view.findViewById<TextInputEditText>(R.id.etMagnet)?.text?.toString() ?: ""
                val title = view.findViewById<TextInputEditText>(R.id.etTitle)?.text?.toString() ?: ""
                val poster = view.findViewById<TextInputEditText>(R.id.etPoster)?.text?.toString() ?: ""

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
            // SEARCH
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val rutorEnabled = loadSettings()?.EnableRutorSearch == true
                    withContext(Dispatchers.Main) {
                        findViewById<TextInputLayout>(R.id.tvRutor)?.apply {
                            visibility = if (rutorEnabled)
                                View.VISIBLE
                            else
                                View.GONE
                        }
                    }
                }
            }
            findViewById<androidx.constraintlayout.widget.Group>(R.id.adder)?.visibility = View.VISIBLE
            findViewById<TextInputEditText>(R.id.etSearch)?.apply {
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
                                        showSortFab()
                                    }
                                else {
                                    App.toast(R.string.no_torrents)
                                    hideSortFab()
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
                                            showSortFab()
                                        }
                                }
                            }
                        }
                    }

                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                })
            }
            // RESULTS
            findViewById<RecyclerView>(R.id.rvRTorrents)?.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(context)
                adapter = torrsAdapter
                addItemDecoration(DividerItemDecoration(context, LinearLayout.VERTICAL))
            }
            torrsAdapter.onClick = {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val torrent = addTorrent("", it.Magnet, it.Title, "", "", true)
                        torrent?.let { App.toast("${getString(R.string.stat_string_added)}: ${it.title}") } ?: App.toast(getString(R.string.error_add_torrent))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        App.toast(e.message ?: getString(R.string.error_add_torrent))
                    }
                }
                popBackStackFragment()
            }
            torrsAdapter.onLongClick = {
                lifecycleScope.launch(Dispatchers.IO) {
                    val torrent: Torrent
                    val torr = addTorrent("", it.Magnet, it.Title, "", "", false) ?: let {
                        return@launch
                    }
                    torrent = TorrentHelper.waitFiles(torr.hash) ?: let {
                        return@launch
                    }
                    TorrentHelper.showFFPInfo(view.context, it.Magnet, torrent)
                }
            }
            // SORT
            setupSortFab()
        }
    }

    fun onKeyUp(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_X -> {
                return true
            }
        }
        return false
    }

    private var sortMode: Int = 0
    @SuppressLint("NotifyDataSetChanged")
    fun onKeyDown(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_INFO -> {
                activity?.currentFocus?.let {
                    it.findViewById<RecyclerView>(R.id.rvRTorrents)?.let { rv ->
                        val itemPosition = rv.getChildAdapterPosition(rv.focusedChild)
                        if (itemPosition in torrsAdapter.list.indices) {
                            torrsAdapter.list[itemPosition].let {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val torrent: Torrent
                                    val torr = addTorrent("", it.Magnet, it.Title, "", "", false) ?: let {
                                        return@launch
                                    }
                                    torrent = TorrentHelper.waitFiles(torr.hash) ?: let {
                                        return@launch
                                    }
                                    TorrentHelper.showFFPInfo(rv.context, it.Magnet, torrent)
                                }
                            }
                        }
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_X -> {
                val list = torrsAdapter.list.toMutableList()
                when (sortMode) {
                    0 -> {
                        torrsAdapter.set(list.sortedBy { it.Title })
                        App.toast(R.string.sort_by_name)
                    }

                    1 -> {
                        val sort = list.sortedWith(compareBy(nullsLast(reverseOrder())) { td ->
                            if (td.Size.contains("GB", true))
                                td.Size.filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.let { it * 1024 * 1024 }
                            else if (td.Size.contains("MB", true))
                                td.Size.filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.let { it * 1024 }
                            else
                                td.Size.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                        })
                        torrsAdapter.set(sort) // list.sortedByDescending { it.Size }
                        App.toast(R.string.sort_by_size)
                    }

                    2 -> {
                        val sort = list.sortedWith(compareBy(nullsLast(reverseOrder())) { it.Seed }) // .toIntOrNull()
                        torrsAdapter.set(sort)
                        App.toast(R.string.sort_by_seed)
                    }

                    3 -> {
                        torrsAdapter.set(list.sortedByDescending { it.CreateDate })
                        App.toast(R.string.sort_by_date)
                    }
                }
                torrsAdapter.notifyDataSetChanged()
                if (torrsAdapter.list.size > 0)
                    activity?.findViewById<RecyclerView>(R.id.rvRTorrents)?.apply {
                        scrollToPosition(0)
                        // FIXME: Why RecycleView loose focus to Logo?
                        Handler(Looper.getMainLooper()).postDelayed({
                            getChildAt(0).requestFocus()
                        }, 500)
                    }
                if (sortMode == 3) sortMode = 0 else sortMode++
                return true
            }
        }

        return false
    }

    private fun setupSortFab() { // Sort Fab
        val fab: FloatingActionButton? = requireActivity().findViewById(R.id.sortFab)
        fab?.apply {
            setImageDrawable(AppCompatResources.getDrawable(this.context, R.drawable.round_sort_24))
            customSize = Format.dp2px(32f)
            setMaxImageSize(Format.dp2px(24f))
            setOnClickListener {
                // TODO: add sort options menu
                Thread {
                    try {
                        Instrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BUTTON_X)
                    } catch (_: InterruptedException) {
                    }
                }.start()
            }
            visibility = View.GONE
        }
    }

    private fun showSortFab() {
        val fab: FloatingActionButton? = requireActivity().findViewById(R.id.sortFab)
        fab?.show()
    }

    private fun hideSortFab() {
        val fab: FloatingActionButton? = requireActivity().findViewById(R.id.sortFab)
        fab?.hide()
    }

    private fun loadSettings(): BTSets? {
        return try {
            Api.getSettings()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}