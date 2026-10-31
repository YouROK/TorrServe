package ru.yourok.torrserve.ui.fragments.main.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.activities.play.PlayActivity
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.ui.fragments.main.torrents.TorrentsAdapter

class SearchFragment : TSFragment() {

    private var torrentAdapter: TorrentsAdapter? = null
    private var emptyView: TextView? = null
    private var allTorrents: List<Torrent> = emptyList()
    private var jobSearch: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.search_fragment, container, false)
        torrentAdapter = TorrentsAdapter(requireActivity())
        emptyView = vi.findViewById(R.id.empty_view)
        vi.findViewById<ListView>(R.id.lvResults)?.let { lvResults ->
            lvResults.adapter = torrentAdapter
            lvResults.setOnItemClickListener { _, _, i, _ ->
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
        }
        return vi
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val etSearch = view.findViewById<TextInputEditText>(R.id.etSearch)
        etSearch?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                refreshList()
                true
            } else
                false
        }
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                search(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        refreshList()
    }

    // reload added torrents from server and search again
    private fun refreshList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = try {
                Api.listTorrent()
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                allTorrents = list
                view?.findViewById<TextInputEditText>(R.id.etSearch)?.text?.toString()?.let {
                    search(it)
                }
            }
        }
    }

    private fun search(query: String) {
        jobSearch?.cancel()
        jobSearch = lifecycleScope.launch {
            val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            val found = if (tokens.isEmpty())
                allTorrents
            else
                allTorrents.filter { torr ->
                    // Gson can set null despite non-null declared type
                    val title: String? = torr.title
                    val name: String? = torr.name
                    tokens.all { title?.contains(it, true) == true || name?.contains(it, true) == true }
                }
            torrentAdapter?.update(found)
            emptyView?.apply {
                setText(if (tokens.isEmpty()) R.string.no_torrents else R.string.nothing_found)
                visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
