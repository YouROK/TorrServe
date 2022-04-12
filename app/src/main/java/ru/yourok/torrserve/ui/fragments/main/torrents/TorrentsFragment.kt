package ru.yourok.torrserve.ui.fragments.main.torrents

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.activities.play.PlayActivity
import ru.yourok.torrserve.ui.fragments.TSFragment


class TorrentsFragment : TSFragment() {

    private var torrentAdapter: TorrentsAdapter? = null
    private lateinit var emptyView: TextView
    private var isOpened = false

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
                val vintent = Intent(App.appContext(), PlayActivity::class.java)
                vintent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                vintent.action = Intent.ACTION_VIEW
                vintent.putExtra("hash", torr.hash)
                vintent.putExtra("title", torr.title)
                vintent.putExtra("poster", torr.poster)
                vintent.putExtra("action", "play")
                App.appContext().startActivity(vintent)
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
        viewModel = ViewModelProvider(this@TorrentsFragment).get(TorrentsViewModel::class.java)
        val data = (viewModel as TorrentsViewModel).getData()
        data.observe(this@TorrentsFragment) {
            torrentAdapter?.update(it)
            if (it.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                if (!isOpened) {
                    activity?.findViewById<DrawerLayout>(R.id.drawerLayout)?.openDrawer(Gravity.LEFT)
                    isOpened = true
                }
            } else
                emptyView.visibility = View.GONE
        }
    }
}