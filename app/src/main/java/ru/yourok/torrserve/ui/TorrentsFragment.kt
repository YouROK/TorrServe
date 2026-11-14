package ru.yourok.torrserve.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.Prefs
import kotlin.concurrent.thread

/**
 * Экран торрентов: сетка плиток в стиле веб-версии.
 * Данные приходят по SSE (/api/events): сервер присылает только изменения,
 * поэтому список не мигает и не переставляется.
 */
class TorrentsFragment : Fragment(), Refreshable {

    private lateinit var prefs: Prefs
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private val adapter = CardsAdapter()

    /** Порядок плиток: hash -> элемент. Новые добавляются в конец, обновления не меняют порядок. */
    private val items = LinkedHashMap<String, SiloApi.TorrentItem>()
    private var query: String = ""
    private var category: String = ""
    private var focusGiven = false

    /** Хэш раздачи, на которую нужно вернуть фокус при открытии экрана. */
    var restoreFocusHash: String? = null

    private var sseThread: Thread? = null
    @Volatile
    private var sseActive = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View =
        inflater.inflate(R.layout.fragment_torrents, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs(requireContext())
        list = view.findViewById(R.id.torrents_list)
        empty = view.findViewById(R.id.torrents_empty)
        list.layoutManager = GridLayoutManager(requireContext(), spanCount())
        list.clipChildren = false
        list.clipToPadding = false
        (list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        list.itemAnimator = null
        list.adapter = adapter

        empty.setOnClickListener {
            (activity as? MainActivity)?.showScreen(MainActivity.Screen.USERS)
        }
        (activity as? MainActivity)?.setSearchListener { q ->
            query = q
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        startStream()
        loadOnce()
        focusFirstCard(0)
    }

    override fun onPause() {
        super.onPause()
        stopStream()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopStream()
    }

    override fun onRefresh() {
        items.clear()
        loadOnce()
        restartStream()
    }

    /**
     * Слияние полного списка с уже показанным: существующие позиции сохраняются,
     * недостающие удаляются, новые добавляются в конец. Так плитки не перескакивают.
     */
    private fun mergeAll(incoming: List<SiloApi.TorrentItem>) {
        val byHash = incoming.associateBy { it.hash }
        items.keys.retainAll(byHash.keys)
        incoming.forEach { card ->
            items[card.hash] = mergeOne(items[card.hash], card)
        }
    }

    /** Обновление одной карточки: поля, которых нет в SSE (дата, название), не теряем. */
    private fun mergeOne(old: SiloApi.TorrentItem?, card: SiloApi.TorrentItem): SiloApi.TorrentItem {
        if (old == null) return card
        return card.copy(
            timestamp = if (card.timestamp > 0) card.timestamp else old.timestamp,
            title = card.title.ifBlank { old.title },
            poster = card.poster.ifBlank { old.poster },
            category = card.category.ifBlank { old.category },
            torrs = card.torrs.ifBlank { old.torrs },
            sizeBytes = if (card.sizeBytes > 0) card.sizeBytes else old.sizeBytes,
        )
    }

    /** Фильтр по категории (пусто - все). */
    fun setCategory(value: String) {
        category = value
        render()
    }

    /** Доступные категории у текущих торрентов. */
    fun categories(): List<String> =
        items.values.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    /** Перерисовать после смены режима сортировки. */
    fun onSortChanged() = render()

    /** Удалить все торренты активного пользователя. */
    fun deleteAll(onDone: (Int) -> Unit) {
        val token = prefs.activeToken
        val url = prefs.serverUrl
        val hashes = items.keys.toList()
        thread {
            val api = SiloApi(url)
            var removed = 0
            hashes.forEach { hash ->
                if (api.dropTorrent(token, hash) == null) removed++
            }
            ui.post {
                items.clear()
                render()
                onDone(removed)
            }
        }
    }

    /** После добавления торрента: перезагружаем список и отдаём фокус плите. */
    fun onTorrentAdded() {
        onRefresh()
        focusFirstCard(0)
    }

    // ---------- Данные ----------

    private fun loadOnce() {
        val url = prefs.serverUrl
        thread {
            try {
                // Локальный сервер: сначала поднимаем его, потом входим паролем владельца
                if (prefs.isLocalServer && !SiloApi(url).ping()) {
                    TorrService.start(requireContext().applicationContext)
                    for (i in 0 until 30) {
                        Thread.sleep(300)
                        if (SiloApi(url).ping()) break
                    }
                }
                val hadSession = prefs.activeSession() != null
                LocalAuth.ensureSession(requireContext(), prefs)
                val token = prefs.activeToken
                if (!hadSession && prefs.activeSession() != null) {
                    // Вошли автоматически - обновляем подпись в боковом меню
                    ui.post { (activity as? MainActivity)?.updateSidebarState() }
                }
                val result = SiloApi(url).listTorrents(token)
                ui.post {
                    if (!isAdded) return@post
                    mergeAll(result)
                    render()
                }
            } catch (e: Exception) {
                ui.post {
                    if (!isAdded) return@post
                    if (items.isEmpty()) {
                        val unauthorized = e.message?.contains("401") == true ||
                            e.message?.contains("404") == true
                        // Открытый режим (без пароля владельца) работает без токена: это owner, не «нужен вход»
                        val localNoLogin = prefs.isLocalServer &&
                            prefs.ownerPassword.isNotEmpty() && prefs.activeToken.isEmpty()
                        val needsLogin = unauthorized || localNoLogin
                        showEmpty(
                            when {
                                needsLogin && prefs.isLocalServer -> getString(R.string.main_local_need_login)
                                needsLogin -> getString(R.string.main_need_login)
                                else -> getString(R.string.main_load_error)
                            },
                            // Для локального сервера ведём в настройки, где задаётся пароль владельца
                            clickable = needsLogin && prefs.isLocalServer,
                        )
                    }
                }
            }
        }
    }

    private fun startStream() {
        if (sseActive) return
        sseActive = true
        val token = prefs.activeToken
        val url = prefs.serverUrl
        sseThread = thread {
            SiloApi(url).events(
                token = token,
                isActive = { sseActive },
                onSync = { cards ->
                    ui.post {
                        if (!isAdded || !sseActive) return@post
                        mergeAll(cards)
                        render()
                    }
                },
                onCard = { card ->
                    ui.post {
                        if (!isAdded || !sseActive) return@post
                        val merged = mergeOne(items[card.hash], card)
                        if (items[card.hash] == merged) return@post
                        items[card.hash] = merged
                        render()
                    }
                },
                onRemove = { hash ->
                    ui.post {
                        if (!isAdded || !sseActive) return@post
                        if (items.remove(hash) != null) render()
                    }
                },
            )
        }
    }

    private fun stopStream() {
        sseActive = false
        sseThread?.interrupt()
        sseThread = null
    }

    private fun restartStream() {
        stopStream()
        startStream()
    }

    // ---------- Отрисовка ----------

    /** Долгое нажатие на плитку: быстрые действия с раздачей. */
    private fun showCardMenu(item: SiloApi.TorrentItem) {
        val ctx = requireContext()
        val actions = arrayOf(
            getString(R.string.card_open),
            getString(R.string.card_viewed_all),
            getString(R.string.card_copy_torrs),
            getString(R.string.torrent_delete),
        )
        android.app.AlertDialog.Builder(ctx)
            .setTitle(item.title.ifBlank { item.hash })
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> (activity as? MainActivity)?.openTorrent(item.hash)
                    1 -> thread {
                        val err = SiloApi(prefs.serverUrl).markAllViewed(prefs.activeToken, item.hash)
                        ui.post {
                            if (!isAdded) return@post
                            android.widget.Toast.makeText(
                                ctx,
                                err ?: getString(R.string.card_viewed_done),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    2 -> {
                        val link = item.torrs.ifBlank { "magnet:?xt=urn:btih:${item.hash}" }
                        val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("torrs", link))
                        android.widget.Toast.makeText(ctx, R.string.card_copied, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    3 -> thread {
                        val err = SiloApi(prefs.serverUrl).dropTorrent(prefs.activeToken, item.hash)
                        ui.post {
                            if (!isAdded) return@post
                            if (err != null) {
                                android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                items.remove(item.hash)
                                render()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun render() {
        val all = items.values.toList()
        val byCategory = if (category.isBlank()) all else all.filter { it.category == category }
        val filtered = if (query.isBlank()) byCategory
        else byCategory.filter { it.title.contains(query, ignoreCase = true) || it.hash.contains(query, ignoreCase = true) }
        val sorted = when (prefs.sortMode) {
            1 -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            2 -> filtered.sortedWith(compareByDescending<SiloApi.TorrentItem> { it.sizeBytes }.thenBy { it.hash })
            3 -> filtered.sortedWith(compareByDescending<SiloApi.TorrentItem> { it.downloadSpeed }.thenBy { it.hash })
            // Дате, затем хэш: порядок всегда одинаковый, плитки не переставляются
            else -> filtered.sortedWith(compareByDescending<SiloApi.TorrentItem> { it.timestamp }.thenBy { it.hash })
        }

        if (sorted.isEmpty()) {
            showEmpty(if (all.isEmpty()) getString(R.string.main_empty) else getString(R.string.torrents_nothing_found))
            return
        }
        empty.visibility = View.GONE
        list.visibility = View.VISIBLE
        adapter.submit(sorted)
        if (!focusGiven) {
            // Фокус ставим только когда он реально встал (флаг внутри)
            focusCardByHash(restoreFocusHash)
            restoreFocusHash = null
        } else if (list.findFocus() == null && activity?.currentFocus == null) {
            // Ничего не в фокусе (например, после перерисовки) - возвращаем на прежнюю плитку
            adapter.restoreFocusAt(adapter.lastFocus())
        }
    }

    /** Ставит фокус на карточку с нужным хэшем (или на первую, если её нет). */
    private fun focusCardByHash(hash: String?) {
        val index = adapter.indexOf(hash).coerceAtLeast(0)
        if (index > 0) list.scrollToPosition(index)
        focusAt(index, 0)
    }

    /** Фокус на плитку [index] с повторами, пока список не разложится. */
    private fun focusAt(index: Int, attempt: Int) {
        list.postDelayed({
            if (!isAdded) return@postDelayed
            // RecyclerView по умолчанию норовит взять фокус себе - возвращаем его плиткам
            list.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            list.isFocusable = false
            if (adapter.itemCount == 0) {
                if (attempt < FOCUS_TRIES) focusAt(index, attempt + 1)
                return@postDelayed
            }
            val pos = index.coerceIn(0, adapter.itemCount - 1)
            val target = list.findViewHolderForAdapterPosition(pos)?.itemView
                ?: list.layoutManager?.findViewByPosition(pos)
            val ok = target?.requestFocus() == true ||
                target?.requestFocusFromTouch() == true
            if (ok) {
                focusGiven = true
                return@postDelayed
            }
            if (attempt < FOCUS_TRIES) focusAt(pos, attempt + 1)
        }, 150)
    }

    private fun showEmpty(text: String, clickable: Boolean = false) {
        empty.isClickable = clickable
        empty.isFocusable = clickable
        empty.setOnClickListener {
            if (clickable) (activity as? MainActivity)?.showScreen(MainActivity.Screen.SETTINGS)
        }
        adapter.submit(emptyList())
        list.visibility = View.GONE
        empty.visibility = View.VISIBLE
        empty.text = text
    }

    private fun focusFirstCard(attempt: Int) = focusAt(0, attempt)

    private fun spanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        val usable = if (widthDp >= MainActivity.WIDE_DP) widthDp - 260 else widthDp
        return (usable / CARD_DP).coerceAtLeast(2)
    }

    /**
     * Только активные состояния (сервер: 0 добавлен, 1 получение данных, 2 предзагрузка,
     * 3 работает, 4 закрыт, 5 в базе). У неактивных бейджа нет.
     */
    private fun isActive(stat: Int): Boolean = stat <= 3

    private fun statusOf(stat: Int): Pair<String, Int> = when (stat) {
        0 -> getString(R.string.stat_added) to Color.parseColor("#46505A")
        1 -> getString(R.string.stat_getting_info) to Color.parseColor("#C07D18")
        2 -> getString(R.string.stat_preload) to Color.parseColor("#F0A832")
        else -> getString(R.string.stat_working) to Color.parseColor("#4E8F89")
    }

    private inner class CardsAdapter : RecyclerView.Adapter<CardsAdapter.VH>() {
        private val data = mutableListOf<SiloApi.TorrentItem>()
        private var lastFocusPos = 0

        fun lastFocus(): Int = lastFocusPos

        fun restoreFocusAt(pos: Int) = restoreFocus(pos, 0)

        /** Возвращает фокус на плитку [pos] (несколько попыток, пока список перестраивается). */
        private fun restoreFocus(pos: Int, attempt: Int) {
            list.postDelayed({
                if (!isAdded || data.isEmpty()) return@postDelayed
                val index = pos.coerceIn(0, data.size - 1)
                val focused = list.findFocus()
                if (focused != null && list.getChildAdapterPosition(focused) == index) return@postDelayed
                val view = list.findViewHolderForAdapterPosition(index)?.itemView
                    ?: list.layoutManager?.findViewByPosition(index)
                if (view?.requestFocus() == true) return@postDelayed
                if (attempt < FOCUS_RETRIES) restoreFocus(index, attempt + 1)
            }, FOCUS_RETRY_MS)
        }

        private val bgNormal by lazy { ContextCompat.getDrawable(requireContext(), R.drawable.bg_card_normal) }
        private val bgHover by lazy { ContextCompat.getDrawable(requireContext(), R.drawable.bg_card_hover) }
        private val bgFocus by lazy { ContextCompat.getDrawable(requireContext(), R.drawable.bg_card_focus) }
        private val density by lazy { resources.displayMetrics.density }

        /** Обновление по хэшам: неизменённые плитки не трогаем, порядок не меняется. */
        fun indexOf(hash: String?): Int {
            if (hash == null) return 0
            val idx = data.indexOfFirst { it.hash == hash }
            return if (idx >= 0) idx else 0
        }

        fun submit(newItems: List<SiloApi.TorrentItem>) {
            // Где сейчас фокус: внутри списка, снаружи или нигде
            val focusedChild = list.findFocus()
            val focusInList = focusedChild != null
            val focusOutside = !focusInList && activity?.currentFocus != null
            val pos = if (focusInList) list.getChildAdapterPosition(focusedChild!!) else lastFocusPos

            val oldHashes = data.map { it.hash }
            val newHashes = newItems.map { it.hash }
            if (oldHashes == newHashes) {
                newItems.forEachIndexed { index, item ->
                    if (data[index] != item) {
                        data[index] = item
                        notifyItemChanged(index)
                    }
                }
                if (focusInList) restoreFocus(pos, 0)
                return
            }
            data.clear()
            data.addAll(newItems)
            notifyDataSetChanged()
            // Не перебиваем фокус, если пользователь ушёл в меню
            if (!focusOutside) restoreFocus(pos, 0)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(layoutInflater.inflate(R.layout.item_torrent, parent, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = data[position]
            holder.title.text = item.title.ifBlank { item.hash }

            val meta = mutableListOf<String>()
            val size = humanSize(item.sizeBytes)
            if (size.isNotBlank()) meta += size
            // Пиры/сиды и скорость - только у активной раздачи
            if (isActive(item.stat)) {
                if (item.seeders > 0 || item.peers > 0) {
                    meta += getString(R.string.card_seeders_peers, item.seeders, item.peers)
                }
                if (item.downloadSpeed > 0) {
                    meta += String.format("%.1f MB/s", item.downloadSpeed / 1024 / 1024)
                }
            }
            holder.info.text = meta.filter { it.isNotBlank() }.joinToString(" · ")

            // Бейдж статуса - только когда раздача активна
            if (isActive(item.stat)) {
                val (label, color) = statusOf(item.stat)
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = label
                holder.badge.background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(color)
                }
            } else {
                holder.badge.visibility = View.GONE
            }

            PosterLoader.load(requireContext(), item.poster, holder.poster)
            holder.itemView.setOnClickListener {
                (activity as? MainActivity)?.openTorrent(item.hash)
            }
            holder.itemView.setOnLongClickListener {
                showCardMenu(item)
                true
            }
            // Пульт: долгое удержание OK или кнопка MENU открывают меню раздачи
            holder.itemView.setOnKeyListener { _, keyCode, event ->
                val menuKey = keyCode == android.view.KeyEvent.KEYCODE_MENU
                val longOk = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER && event.isLongPress
                if (menuKey || longOk) {
                    showCardMenu(item)
                    true
                } else {
                    false
                }
            }

            // Плитка «выступает» при фокусе (пульт) и при наведении мыши - как :hover в вебе
            val view = holder.itemView
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) lastFocusPos = holder.bindingAdapterPosition
                applyState(v, hasFocus, v.isHovered)
            }
            view.setOnHoverListener { v, event ->
                applyState(v, v.hasFocus(), event.action != MotionEvent.ACTION_HOVER_EXIT)
                false
            }
            applyState(view, view.hasFocus(), view.isHovered)
        }

        private fun applyState(v: View, focused: Boolean, hovered: Boolean) {
            val active = focused || hovered
            v.background = when {
                focused -> bgFocus
                hovered -> bgHover
                else -> bgNormal
            }
            // Как :hover в вебе: плитка «всплывает» из фона (масштаб + тень)
            v.animate().scaleX(if (active) 1.05f else 1f)
                .scaleY(if (active) 1.05f else 1f)
                .setDuration(160L)
                .start()
            v.elevation = if (active) 18f * density else 0f
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val poster = v.findViewById<android.widget.ImageView>(R.id.item_poster)
            val badge = v.findViewById<TextView>(R.id.item_badge)
            val title = v.findViewById<TextView>(R.id.item_title)
            val info = v.findViewById<TextView>(R.id.item_info)
        }
    }

    companion object {
        private const val CARD_DP = 200
        private const val FOCUS_RETRIES = 10
        private const val FOCUS_TRIES = 40
        private const val FOCUS_RETRY_MS = 120L

        fun humanSize(bytes: Long): String {
            if (bytes <= 0) return ""
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var v = bytes.toDouble()
            var i = 0
            while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
            return String.format("%.1f %s", v, units[i])
        }
    }
}
