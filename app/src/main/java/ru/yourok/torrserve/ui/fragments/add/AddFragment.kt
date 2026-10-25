package ru.yourok.torrserve.ui.fragments.add

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.normalize
import ru.yourok.torrserve.ext.popBackStackFragment
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.TorrentDetails
import ru.yourok.torrserve.settings.BTSets
import ru.yourok.torrserve.ui.activities.play.addTorrent
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.ui.fragments.rutor.TorrentsAdapter
import ru.yourok.torrserve.utils.Format
import ru.yourok.torrserve.utils.TorrentHelper

class AddFragment : TSFragment() {

    private val torrsAdapter = TorrentsAdapter()
    private var jobSearch: Job? = null
    private var lastQuery = ""
    private var rutorEnabled = false
    private var torznabEnabled = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.add_fragment, container, false)
    }

    override fun onDestroyView() {
        // a search still running is cancelled with the fragment and never hides the progress itself
        activity?.findViewById<View>(R.id.progressBar)?.visibility = View.GONE
        super.onDestroyView()
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
                val category = view.findViewById<TextInputEditText>(R.id.etCategory)?.text?.toString() ?: ""

                if (link.isNotBlank())
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            addTorrent("", link, title, poster, category, "", true)
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
            // SEARCH and CATEGORY
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val sets = loadSettings()
                    rutorEnabled = sets?.EnableRutorSearch == true
                    torznabEnabled = sets?.EnableTorznabSearch == true
                    val categoryEnabled = Api.getMatrixVersionInt() > 131
                    withContext(Dispatchers.Main) {
                        findViewById<TextInputLayout>(R.id.tvRutor)?.apply {
                            visibility = if (rutorEnabled || torznabEnabled)
                                View.VISIBLE
                            else
                                View.GONE
                            hint = listOfNotNull(getString(R.string.rutor).takeIf { rutorEnabled }, "Torznab".takeIf { torznabEnabled }).joinToString(" + ")
                        }
                        // search shows up after the form already took focus, move it to the top row
                        if ((rutorEnabled || torznabEnabled) && findViewById<View>(R.id.etMagnet)?.hasFocus() == true)
                            findViewById<View>(R.id.etSearch)?.requestFocus()
                        findViewById<TextInputLayout>(R.id.tvCategory)?.apply {
                            visibility = if (categoryEnabled)
                                View.VISIBLE
                            else
                                View.GONE
                        }
                    }
                }
            }
            findViewById<androidx.constraintlayout.widget.Group>(R.id.adder)?.visibility = View.VISIBLE
            findViewById<TextInputEditText>(R.id.etSearch)?.setOnEditorActionListener { textView, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                )
                    submitSearch(textView.text.toString().trim())
                true
            }
            findViewById<TextInputLayout>(R.id.tvRutor)?.apply {
                setEndIconOnClickListener {
                    submitSearch(findViewById<TextInputEditText>(R.id.etSearch)?.text?.toString()?.trim() ?: "")
                }
                // the blank suffix reserves room for the sort button laid over the field; EditText padding is overridden here
                suffixTextView.minWidth = Format.dp2px(48f)
            }
            // RESULTS
            findViewById<RecyclerView>(R.id.rvRTorrents)?.apply {
                setHasFixedSize(true)
                isFocusable = false // only rows take focus, else Tab lands on the empty list behind the form
                layoutManager = LinearLayoutManager(context)
                adapter = torrsAdapter
                addItemDecoration(DividerItemDecoration(context, LinearLayout.VERTICAL))
            }
            torrsAdapter.onClick = {
                lifecycleScope.launch(Dispatchers.IO) {
                    showProgress() // a .torrent link goes through the indexer and can take a while
                    try {
                        val category = it.Categories.normalize()
                        val torrent = addTorrent("", it.Magnet.ifBlank { it.Link }, it.Title, "", category, "", true)
                        // on failure stay on the results, so another one can be picked
                        torrent?.let {
                            App.toast("${App.context.getString(R.string.stat_string_added)}: ${it.title}", true)
                            if (isAdded) popBackStackFragment() // the user may have left during a slow .torrent fetch
                        } ?: App.toast(R.string.error_add_torrent)
                    } catch (e: Exception) {
                        App.toast(e.message ?: App.context.getString(R.string.error_add_torrent))
                    }
                    hideProgress()
                }
            }
            torrsAdapter.onLongClick = { showInfo(it, view.context) }
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

    fun onKeyDown(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_INFO -> {
                activity?.currentFocus?.let {
                    it.findViewById<RecyclerView>(R.id.rvRTorrents)?.let { rv ->
                        val itemPosition = rv.getChildAdapterPosition(rv.focusedChild)
                        if (itemPosition in torrsAdapter.list.indices)
                            showInfo(torrsAdapter.list[itemPosition], rv.context)
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_X -> {
                sortResults()
                return true
            }
        }

        return false
    }

    private var sortMode: Int = 0

    @SuppressLint("NotifyDataSetChanged")
    private fun sortResults() {
        val list = torrsAdapter.list //.toMutableList()
        when (sortMode) {
            0 -> {
                torrsAdapter.set(list.sortedBy { it.Title })
                App.toast(R.string.sort_by_name)
            }

            1 -> { // 10.22 GB (RuTor), 4.7 GCiB (Torznab: the server's formatSize writes CiB for iB)
                val sort = list.sortedWith(compareBy(nullsLast(reverseOrder())) { td ->
                    val num = td.Size.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                    when {
                        td.Size.contains("T", true) -> num?.times(1024.0 * 1024 * 1024)
                        td.Size.contains("G", true) -> num?.times(1024.0 * 1024)
                        td.Size.contains("M", true) -> num?.times(1024.0)
                        else -> num
                    }
                })
                torrsAdapter.set(sort) // list.sortedByDescending { it.Size }
                App.toast(R.string.sort_by_size)
            }

            2 -> {
                val sort = list.sortedWith(compareBy(nullsLast(reverseOrder())) { it.Seed })
                torrsAdapter.set(sort)
                App.toast(R.string.sort_by_seed)
            }

            3 -> { // 2021-06-21T00:00:00+03:00
                torrsAdapter.set(list.sortedByDescending { it.CreateDate })
//                val sort = list.sortedWith(compareBy(nullsLast(reverseOrder())) { td ->
//                    if (td.CreateDate.length == 25) td.CreateDate.substring(0, 10)
//                    else td.CreateDate
//                })
//                torrsAdapter.set(sort)
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
    }

    private fun setupSortFab() {
        // TODO: add sort options menu
        requireActivity().findViewById<View>(R.id.sortFab)?.setOnClickListener { sortResults() }
    }

    private fun showSortFab() {
        requireActivity().findViewById<View>(R.id.sortFab)?.visibility = View.VISIBLE
    }

    private fun hideSortFab() {
        requireActivity().findViewById<View>(R.id.sortFab)?.visibility = View.GONE
    }

    // search on submit only: Torznab is slow and a cancelled job can't abort its HTTP request
    private fun submitSearch(query: String) {
        if (query.isBlank() || query == lastQuery) return
        lastQuery = query
        jobSearch?.cancel()
        jobSearch = lifecycleScope.launch(Dispatchers.IO) {
            if (BuildConfig.DEBUG) Log.d("*****", "search($query)")
            val result = search(query)
            withContext(Dispatchers.Main) {
                when {
                    result == null -> {
                        lastQuery = "" // every source failed, let the same query retry
                    }

                    result.isNotEmpty() -> {
                        torrsAdapter.set(result)
                        view?.findViewById<androidx.constraintlayout.widget.Group>(R.id.adder)?.visibility = View.GONE
                        view?.findViewById<LinearLayout>(R.id.footer)?.visibility = View.GONE
                        showSortFab()
                    }

                    else -> {
                        lastQuery = "" // let the same query be searched again later
                        App.toast(R.string.no_torrents)
                        hideSortFab()
                    }
                }
            }
        }
    }

    private fun showInfo(td: TorrentDetails, context: Context) {
        lifecycleScope.launch(Dispatchers.IO) {
            val link = td.Magnet.ifBlank { td.Link }
            val torr = addTorrent("", link, td.Title, "", td.Categories.normalize(), "", false) ?: return@launch
            val torrent = TorrentHelper.waitFiles(torr.hash) ?: return@launch
            TorrentHelper.showFFPInfo(context, link, torrent)
        }
    }

    // null when every enabled source failed, so the caller keeps the previous results
    private suspend fun search(query: String): List<TorrentDetails>? = supervisorScope {
        showProgress()
        try {
            listOfNotNull(
                if (rutorEnabled) async { Api.searchTorrents(query) } else null,
                if (torznabEnabled) async { Api.searchTorznab(query) } else null,
            ).mapNotNull {
                try {
                    it.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.message?.let { msg -> App.toast(msg) }
                    null
                }
            }.takeIf { it.isNotEmpty() }?.flatten()
        } finally {
            // a cancelled search was replaced by a newer one, which keeps the progress on
            if (isActive) hideProgress()
        }
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