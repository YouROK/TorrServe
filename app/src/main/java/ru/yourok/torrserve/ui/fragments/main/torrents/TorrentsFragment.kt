package ru.yourok.torrserve.ui.fragments.main.torrents

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.play.PlayActivity
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.utils.TorrentHelper


class TorrentsFragment : TSFragment() {

    private var torrentAdapter: TorrentsAdapter? = null
    private var sortMode: Boolean = Settings.sortTorrByTitle
    private var category = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.main_fragment, container, false)
        torrentAdapter = TorrentsAdapter(requireActivity())
        vi.findViewById<ListView>(R.id.lvTorrents)?.let { lvTorrents ->
            lvTorrents.adapter = torrentAdapter
            lvTorrents.emptyView = vi.findViewById(R.id.empty_view)
            lvTorrents.setOnItemClickListener { _, _, i, _ ->
                val torr = torrentAdapter?.getItem(i) as Torrent
                val intent = Intent(App.context, PlayActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.action = Intent.ACTION_VIEW
                intent.putExtra("hash", torr.hash)
                intent.putExtra("title", torr.title)
                torr.category?.let { if (it.isNotBlank()) intent.putExtra("category", it) }
                intent.putExtra("poster", torr.poster)
                intent.putExtra("action", "play")
                App.context.startActivity(intent)
            }
            lvTorrents.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
            lvTorrents.setMultiChoiceModeListener(TorrentsActionBar(lvTorrents))
        }
        return vi
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        category = arguments?.getString("category") ?: ""
        viewModel = ViewModelProvider(this)[TorrentsViewModel::class.java]
        (viewModel as TorrentsViewModel).getData().observe(viewLifecycleOwner) { updateList() }
        applySearchBarVisibility()
        view.findViewById<EditText>(R.id.etSearch).doAfterTextChanged { updateList() }
    }

    override fun onResume() {
        super.onResume()
        (viewModel as TorrentsViewModel).setUpdate(true)
        applySearchBarVisibility()
    }

    override fun onPause() {
        super.onPause()
        if (isViewModelInitialized())
            (viewModel as TorrentsViewModel).setUpdate(false)
    }

    fun sort(mode: Boolean = sortMode) {
        val list = torrentAdapter!!.list
        if (list.isNotEmpty()) {
            sortMode = !mode
            Settings.set("sort_torrents", sortMode)
            updateList()
            App.toast(if (sortMode) R.string.sort_by_name else R.string.sort_by_date)
            activity?.findViewById<ListView>(R.id.lvTorrents)?.apply {
                this.setSelection(0)
                requestFocus()
            }
        }
    }

    suspend fun filter(cat: String = "") = withContext(Dispatchers.Main) {
        category = cat
        updateList()
    }

    private fun applySearchBarVisibility() {
        view?.findViewById<View>(R.id.search_bar)?.isVisible = Settings.showSearchBar
    }

    private fun updateList() {
        val query = view?.findViewById<EditText>(R.id.etSearch)?.text?.toString()?.trim().orEmpty()
        val list = (viewModel as TorrentsViewModel).getData().value.orEmpty().filter {
            (if (category == "uncategorized") it.category.isNullOrBlank()
            else category.isBlank() || it.category?.contains(category, true) == true) &&
                    (query.isEmpty() || it.title.orEmpty().contains(query, true) || it.name.orEmpty().contains(query, true))
        }
        torrentAdapter?.update(if (sortMode) list.sortedBy { it.title } else list.sortedByDescending { it.timestamp })
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

    @SuppressLint("NotifyDataSetChanged")
    fun onKeyDown(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_INFO -> {
                activity?.currentFocus?.let {
                    it.findViewById<ListView>(R.id.lvTorrents)?.let { lv ->
                        val itemPosition = lv.selectedItemPosition
                        if (itemPosition in torrentAdapter!!.list.indices) {
                            torrentAdapter!!.list[itemPosition].let {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val torrent = TorrentHelper.waitFiles(it.hash) ?: let {
                                        return@launch
                                    }
                                    TorrentHelper.showFFPInfo(lv.context, "", torrent)
                                }
                            }
                        }
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_X -> {
                sort(sortMode)
                if (Utils.isTvBox()) return true
            }

        }
        return false
    }
}
