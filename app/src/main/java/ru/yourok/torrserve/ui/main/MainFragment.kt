package ru.yourok.torrserve.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.yourok.torrserve.R
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.settings.Settings


class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: TorrentsViewModel
    private var torrentAdapter: TorrentsAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.main_fragment, container, false)
        torrentAdapter = TorrentsAdapter(requireActivity())
        vi.findViewById<ListView>(R.id.lvTorrents)?.let { lvTorrents ->
            lvTorrents.adapter = torrentAdapter
        }
        setupNavigator(vi)
        return vi
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(TorrentsViewModel::class.java)
        val data = viewModel.getData()
        data.observe(this) { torrents ->
            torrentAdapter?.update(torrents)
        }
    }

    private fun setupNavigator(view: View) {
        with(view) {
            val tvCurrHost = findViewById<TextView>(R.id.tvCurrentHost)
            tvCurrHost.text = Settings.getHost()

            findViewById<FrameLayout>(R.id.header).setOnClickListener { _ ->
//                startActivity(Intent(this, ConnectionActivity::class.java))
            }
            findViewById<FrameLayout>(R.id.header).setOnLongClickListener {
//                startActivity(Intent(this, ServerSettingsActivity::class.java))
                true
            }

            findViewById<FrameLayout>(R.id.btnAdd).setOnClickListener {
//                startActivity(Intent(this, AddActivity::class.java))
            }

            findViewById<FrameLayout>(R.id.btnRemoveAll).setOnClickListener { _ ->
//                thread {
//                    try {
//                        val torrList = Api.torrentList()
//                        torrList.forEach {
//                            val hash = it.getString("Hash", "")
//                            if (hash.isNotEmpty())
//                                Api.torrentRemove(hash)
//                        }
//                        torrAdapter.checkList()
//                        UpdaterCards.updateCards()
//                    } catch (e: Exception) {
//                        e.message?.let {
//                            App.Toast(it)
//                        }
//                    }
//                }
            }

            findViewById<FrameLayout>(R.id.btnPlaylist).setOnClickListener {
//                thread {
//                    try {
//                        if (Api.torrentList().isNotEmpty()) {
//                            val intent = Intent(Intent.ACTION_VIEW)
//                            intent.setDataAndType(Uri.parse(Net.getHostUrl("/torrent/playlist.m3u")), "video/*")
//                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                            App.getContext().startActivity(intent)
//                        }
//                    } catch (e: Exception) {
//                        e.message?.let {
//                            App.Toast(it)
//                        }
//                    }
//                }
            }

            findViewById<FrameLayout>(R.id.btnDonate).setOnClickListener {
//                Donate.donateDialog(this)
            }

            findViewById<FrameLayout>(R.id.btnUpdate).setOnClickListener {
//                startActivity(Intent(this, UpdaterActivity::class.java))
            }

            findViewById<FrameLayout>(R.id.btnSettings).setOnClickListener {
//                startActivity(Intent(this, AppSettingsActivity::class.java))
            }

            findViewById<FrameLayout>(R.id.btnExit).setOnClickListener {
                TorrService.stop()
                activity?.finishAffinity()
            }
        }
    }
}