package ru.yourok.torrserve.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.settings.Prefs
import kotlin.concurrent.thread

/**
 * Карточка торрента: постер, статус, действия и файлы с вкладками по типам.
 * Список файлов общий с окном добавления magnet-ссылки (FilesSection).
 */
class TorrentDetailFragment : Fragment(), Refreshable {

    private lateinit var prefs: Prefs
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var hashView: TextView
    private lateinit var posterView: android.widget.ImageView
    private lateinit var play: TextView
    private lateinit var delete: TextView
    private lateinit var filesSection: FilesSection

    private var details: SiloApi.TorrentDetails? = null
    private var focusGiven = false

    private val hash: String by lazy { requireArguments().getString(ARG_HASH).orEmpty() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View =
        inflater.inflate(R.layout.fragment_torrent_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs(requireContext())
        titleView = view.findViewById(R.id.detail_title)
        statusView = view.findViewById(R.id.detail_status)
        hashView = view.findViewById(R.id.detail_hash)
        posterView = view.findViewById(R.id.detail_poster)
        play = view.findViewById(R.id.detail_play)
        delete = view.findViewById(R.id.detail_delete)

        // Название рядом с постером - одна строка с прокруткой (бегущая строка)
        titleView.isSingleLine = true
        titleView.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        titleView.marqueeRepeatLimit = -1
        titleView.isSelected = true
        titleView.isHorizontalFadingEdgeEnabled = true
        titleView.setHorizontallyScrolling(true)

        filesSection = FilesSection(
            context = requireContext(),
            tabs = view.findViewById(R.id.detail_tabs),
            list = view.findViewById(R.id.detail_files) as RecyclerView,
            onFileClick = { playFile(it) },
            onFileLongClick = { toggleViewed(it) },
        )

        play.setOnClickListener { firstPlayable()?.let { playFile(it) } }
        play.setOnLongClickListener {
            firstPlayable()?.let { playFile(it, forceAsk = true) }
            true
        }
        delete.setOnClickListener { confirmDelete() }

        load()
    }

    override fun onResume() {
        super.onResume()
        ui.postDelayed(refreshTick, REFRESH_MS)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(refreshTick)
    }

    private val refreshTick = object : Runnable {
        override fun run() {
            load()
            ui.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onRefresh() = load()

    private fun load() {
        thread {
            try {
                val d = SiloApi(prefs.serverUrl).getTorrent(prefs.activeToken, hash)
                ui.post { if (isAdded) bind(d) }
            } catch (e: Exception) {
                ui.post { if (isAdded && details == null) toast(getString(R.string.torrent_load_error)) }
            }
        }
    }

    private fun bind(d: SiloApi.TorrentDetails) {
        details = d

        // Верхний заголовок в карточке не дублируем: название только рядом с постером
        (activity as? MainActivity)?.setHeaderTitle("")
        titleView.text = d.title.ifBlank { d.hash }
        hashView.text = d.hash

        val info = mutableListOf<String>()
        info += statLabel(d.stat)
        if (d.category.isNotBlank()) info += d.category
        if (d.sizeBytes > 0) info += TorrentsFragment.humanSize(d.sizeBytes)
        if (d.stat <= 3 && (d.peers > 0 || d.seeders > 0)) {
            info += getString(R.string.card_seeders_peers, d.seeders, d.peers)
        }
        if (d.downloadSpeed > 0) info += String.format("%.1f MB/s", d.downloadSpeed / 1024 / 1024)
        info += resources.getQuantityString(R.plurals.files_count, d.files.size, d.files.size)
        statusView.text = info.joinToString(" · ")

        PosterLoader.load(requireContext(), d.poster, posterView)

        val hasVideo = d.files.any { filesSection.kindOf(it.name) == FilesSection.Kind.VIDEO }
        val hasAudio = d.files.any { filesSection.kindOf(it.name) == FilesSection.Kind.AUDIO }
        play.visibility = if (hasVideo || hasAudio) View.VISIBLE else View.GONE
        play.setText(if (hasVideo) R.string.detail_play_first else R.string.detail_play_audio)
        view?.findViewById<TextView>(R.id.detail_no_files)?.visibility =
            if (d.files.isEmpty()) View.VISIBLE else View.GONE

        // Фокус на последний воспроизведённый файл, иначе на последний просмотренный
        val preferred = prefs.lastFile(hash).takeIf { it >= 0 }
            ?: d.files.lastOrNull { it.viewed }?.id
        filesSection.submit(d.files, preferredFileId = preferred)

        // Фокус ставим один раз, когда файлы уже есть; иначе ждём метаданные
        if (!focusGiven) {
            if (d.files.isEmpty()) {
                delete.post { if (!delete.hasFocus()) delete.requestFocus() }
            } else {
                focusGiven = true
                filesSection.focusPreferred(preferred)
            }
        }
    }

    private fun firstPlayable(): SiloApi.TorrentFile? =
        filesSection.videoFiles().firstOrNull()
            ?: filesSection.currentFiles().firstOrNull()
            ?: details?.files?.firstOrNull()

    private fun playFile(file: SiloApi.TorrentFile, forceAsk: Boolean = false) {
        prefs.setLastFile(hash, file.id)
        val api = SiloApi(prefs.serverUrl)
        val token = prefs.activeToken
        val d = details
        val url = api.streamUrl(token, hash, file.id)
        val playable = filesSection.videoFiles().ifEmpty { d?.files.orEmpty() }
        val playlist = playable.map { api.streamUrl(token, hash, it.id) to it.name }
        val startIndex = playable.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
        val mime = Players.mimeOf(file.name)
        val title = d?.title.orEmpty()
        val poster = d?.poster.orEmpty()

        PreloadDialog.prepare(requireContext(), api, token, hash, file) {
            if (!isAdded) return@prepare
            // Отмечаем файл просмотренным (для раздач в библиотеке)
            thread { runCatching { api.setFileViewed(token, hash, file.id, true) } }
            Players.play(
                context = requireContext(),
                url = url,
                mime = mime,
                title = title,
                poster = poster,
                forceAsk = forceAsk,
                playlist = playlist,
                startIndex = startIndex,
            )
        }
    }

    private fun toggleViewed(file: SiloApi.TorrentFile) {
        thread {
            val err = SiloApi(prefs.serverUrl).setFileViewed(prefs.activeToken, hash, file.id, !file.viewed)
            ui.post { if (err != null) toast(err) else load() }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.torrent_delete_confirm)
            .setPositiveButton(R.string.torrent_delete) { _, _ ->
                thread {
                    val err = SiloApi(prefs.serverUrl).dropTorrent(prefs.activeToken, hash)
                    ui.post {
                        if (err == null) {
                            (activity as? MainActivity)?.showScreen(MainActivity.Screen.TORRENTS)
                        } else {
                            toast(err)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(text: String) = Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()

    private fun statLabel(stat: Int): String = when (stat) {
        0 -> getString(R.string.stat_added)
        1 -> getString(R.string.stat_getting_info)
        2 -> getString(R.string.stat_preload)
        3 -> getString(R.string.stat_working)
        4 -> getString(R.string.stat_closed)
        else -> getString(R.string.stat_in_db)
    }

    companion object {
        private const val ARG_HASH = "hash"
        private const val REFRESH_MS = 4000L

        fun newInstance(hash: String): TorrentDetailFragment =
            TorrentDetailFragment().apply {
                arguments = Bundle().apply { putString(ARG_HASH, hash) }
            }
    }
}
