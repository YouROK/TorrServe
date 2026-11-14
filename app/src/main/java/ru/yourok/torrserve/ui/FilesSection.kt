package ru.yourok.torrserve.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.SiloApi
import java.util.Locale

/**
 * Список файлов раздачи с вкладками по типам: Видео / Аудио / Субтитры / Остальные.
 * Используется и в карточке торрента, и в окне добавления magnet-ссылки.
 *
 * Пустые вкладки не показываются. Если группа одна - вкладка остаётся как подпись,
 * но фокус на неё не встаёт (иначе приходится жать лишнюю кнопку).
 */
class FilesSection(
    private val context: Context,
    private val tabs: LinearLayout,
    private val list: RecyclerView,
    private val onFileClick: (SiloApi.TorrentFile) -> Unit,
    private val onFileLongClick: ((SiloApi.TorrentFile) -> Unit)? = null,
) {

    enum class Kind { VIDEO, AUDIO, SUBTITLES, OTHER }

    private val adapter = Adapter()
    private var groups: Map<Kind, List<SiloApi.TorrentFile>> = emptyMap()
    private var selected: Kind? = null
    private var lastFocusedId: Int? = null

    init {
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.isFocusable = false
        list.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    /** Текущая выбранная вкладка (может быть null, если файлов нет). */
    fun currentKind(): Kind? = selected

    /** Файлы выбранной вкладки. */
    fun currentFiles(): List<SiloApi.TorrentFile> = groups[selected].orEmpty()

    /** Все видеофайлы раздачи (для плейлиста плеера). */
    fun videoFiles(): List<SiloApi.TorrentFile> = groups[Kind.VIDEO].orEmpty()

    /**
     * Обновляет данные. [preferredFileId] - файл, на который нужно поставить фокус
     * (обычно последний просмотренный/воспроизведённый).
     * Возвращает true, если набор файлов изменился и список перерисован.
     */
    fun submit(files: List<SiloApi.TorrentFile>, preferredFileId: Int? = null): Boolean {
        val newGroups = buildGroups(files)
        if (newGroups == groups) {
            if (preferredFileId != null) lastFocusedId = preferredFileId
            return false
        }
        groups = newGroups
        if (selected !in groups.keys) {
            selected = when {
                groups.containsKey(Kind.VIDEO) -> Kind.VIDEO
                else -> groups.keys.firstOrNull()
            }
        }
        if (preferredFileId != null) lastFocusedId = preferredFileId
        rebuildTabs()
        adapter.submit(groups[selected].orEmpty())
        return true
    }

    /** Ставит фокус на последний просмотренный файл (или на первый). */
    fun focusPreferred(preferredId: Int? = null) {
        if (preferredId != null) lastFocusedId = preferredId
        val files = currentFiles()
        if (files.isEmpty()) return
        val index = files.indexOfFirst { it.id == lastFocusedId }.coerceAtLeast(0)
        focusItem(index, 0)
    }

    /** Есть ли вкладки (файлы разных типов). */
    fun hasTabs(): Boolean = groups.size > 1

    private fun focusItem(index: Int, attempt: Int) {
        list.postDelayed({
            list.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
            list.isFocusable = false
            val view = list.findViewHolderForAdapterPosition(index)?.itemView
                ?: list.layoutManager?.findViewByPosition(index)
            if (view?.requestFocus() == true || view?.requestFocusFromTouch() == true) return@postDelayed
            if (attempt < FOCUS_RETRIES) focusItem(index, attempt + 1) else list.requestFocus()
        }, 120)
    }

    private fun rebuildTabs() {
        tabs.removeAllViews()
        val many = groups.size > 1
        if (groups.isEmpty()) {
            tabs.visibility = View.GONE
            return
        }
        tabs.visibility = View.VISIBLE
        groups.forEach { (kind, files) ->
            val tab = TextView(context).apply {
                text = context.getString(labelRes(kind)) + " " + files.size
                textSize = 14f
                val selectedNow = kind == selected
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (selectedNow) R.color.silo_amber else R.color.silo_text_muted,
                    )
                )
                setPadding(dp(18), dp(9), dp(18), dp(9))
                background = ContextCompat.getDrawable(
                    context,
                    if (selectedNow) R.drawable.bg_tab else R.drawable.bg_tab_unselected,
                )
                // Одна группа - вкладка-подпись, фокус не ставим
                isFocusable = many
                isClickable = many
                if (selectedNow) isSelected = true
                if (many) {
                    setOnClickListener { select(kind) }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                            keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        ) {
                            focusPreferred()
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(8) }
            tabs.addView(tab, params)
        }
    }

    private fun select(kind: Kind) {
        selected = kind
        rebuildTabs()
        adapter.submit(groups[kind].orEmpty())
        tabs.getChildAt(0)?.post { focusPreferred() }
    }

    private fun buildGroups(all: List<SiloApi.TorrentFile>): Map<Kind, List<SiloApi.TorrentFile>> {
        val result = LinkedHashMap<Kind, List<SiloApi.TorrentFile>>()
        Kind.values().forEach { kind ->
            val items = all.filter { kindOf(it.name) == kind }
            if (items.isNotEmpty()) result[kind] = items
        }
        return result
    }

    fun kindOf(name: String): Kind {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            in VIDEO_EXT -> Kind.VIDEO
            in AUDIO_EXT -> Kind.AUDIO
            in SUBTITLE_EXT -> Kind.SUBTITLES
            else -> Kind.OTHER
        }
    }

    private fun labelRes(kind: Kind): Int = when (kind) {
        Kind.VIDEO -> R.string.tab_video
        Kind.AUDIO -> R.string.tab_audio
        Kind.SUBTITLES -> R.string.tab_subtitles
        Kind.OTHER -> R.string.tab_other
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        private val data = mutableListOf<SiloApi.TorrentFile>()

        fun submit(items: List<SiloApi.TorrentFile>) {
            val sameIds = data.size == items.size && data.map { it.id } == items.map { it.id }
            if (sameIds) {
                items.forEachIndexed { index, file ->
                    if (data[index] != file) {
                        data[index] = file
                        notifyItemChanged(index)
                    }
                }
                return
            }
            data.clear()
            data.addAll(items)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH =
            VH(android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val file = data[position]
            holder.name.text = file.name
            holder.info.text = TorrentsFragment.humanSize(file.length)
            holder.dot.visibility = if (file.viewed) View.VISIBLE else View.INVISIBLE
            holder.itemView.isFocusableInTouchMode = false
            holder.itemView.setOnClickListener {
                lastFocusedId = file.id
                onFileClick(file)
            }
            holder.itemView.setOnLongClickListener {
                onFileLongClick?.invoke(file)
                onFileLongClick != null
            }
            holder.itemView.setOnKeyListener { _, keyCode, event ->
                val up = keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP &&
                    event.action == android.view.KeyEvent.ACTION_DOWN
                if (up && position == 0 && groups.size > 1) {
                    tabs.getChildAt(0)?.requestFocus()
                    true
                } else {
                    false
                }
            }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.file_name)
            val info: TextView = v.findViewById(R.id.file_info)
            val dot: View = v.findViewById(R.id.file_viewed_dot)
        }
    }

    companion object {
        private const val FOCUS_RETRIES = 12

        private val VIDEO_EXT = setOf(
            "mkv", "mp4", "m4v", "avi", "mov", "wmv", "webm", "ts", "m2ts", "mpg", "mpeg",
            "flv", "3gp", "ogv", "divx", "vob", "rmvb", "mts", "asf",
        )
        private val AUDIO_EXT = setOf(
            "mp3", "flac", "aac", "m4a", "ogg", "oga", "wav", "wma", "opus", "ape", "ac3",
            "dts", "mka", "alac", "aiff",
        )
        private val SUBTITLE_EXT = setOf("srt", "ass", "ssa", "sub", "vtt", "idx", "smi", "sup", "ttml")
    }
}
