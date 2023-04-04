package ru.yourok.torrserve.ui.fragments.main.torrents

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
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
    private lateinit var emptyView: TextView
//    private var isOpened = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.main_fragment, container, false)
        torrentAdapter = TorrentsAdapter(requireActivity())
        emptyView = vi.findViewById(R.id.empty_view)
        vi.findViewById<ListView>(R.id.lvTorrents)?.let { lvTorrents ->
            lvTorrents.adapter = torrentAdapter
            lvTorrents.setOnItemClickListener { _, _, i, _ ->
                val torr = torrentAdapter?.getItem(i) as Torrent
                val intent = Intent(App.context, PlayActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.action = Intent.ACTION_VIEW
                intent.putExtra("hash", torr.hash)
                intent.putExtra("title", torr.title)
                intent.putExtra("poster", torr.poster)
                intent.putExtra("action", "play")
                App.context.startActivity(intent)
            }
            lvTorrents.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
            lvTorrents.setMultiChoiceModeListener(TorrentsActionBar(lvTorrents))
        }
        return vi
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            start()
        }
    }

    suspend fun start() = withContext(Dispatchers.Main) {
        viewModel = ViewModelProvider(this@TorrentsFragment)[TorrentsViewModel::class.java]
        val data = (viewModel as TorrentsViewModel).getData()
        data.observe(this@TorrentsFragment) {
            torrentAdapter?.update(it)
            if (it.isEmpty()) {
                emptyView.visibility = View.VISIBLE
//                if (!isOpened) {
//                    activity?.findViewById<DrawerLayout>(R.id.drawerLayout)?.openDrawer(GravityCompat.START)
//                    isOpened = true
//                }
            } else { // close menu
                emptyView.visibility = View.GONE
//                if (isOpened) {
//                    activity?.findViewById<DrawerLayout>(R.id.drawerLayout)?.closeDrawers()
//                    isOpened = false
//                }
            }
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

    private var sortMode: Boolean = Settings.sortTorrByTitle()

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
                val list = torrentAdapter!!.list
                if (list.size > 0) {
                    when (sortMode) {
                        false -> {
                            torrentAdapter?.update(list.sortedBy { it.title })
                            App.toast(R.string.sort_by_name)
                        }

                        true -> {
                            torrentAdapter?.update(list.sortedByDescending { it.timestamp })
                            App.toast(R.string.sort_by_date)
                        }
                    }
                    sortMode = !sortMode
                    Settings.set("sort_torrents", sortMode)
                    activity?.findViewById<ListView>(R.id.lvTorrents)?.apply {
                        this.setSelection(0)
                        requestFocus()
                    }
                }
                if (Utils.isTV()) return true
            }

        }
        return false
    }
}