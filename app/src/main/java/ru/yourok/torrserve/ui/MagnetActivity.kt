package ru.yourok.torrserve.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.settings.Prefs
import ru.yourok.torrserve.settings.Session
import java.util.Locale
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * Окно для magnet/torrs-ссылок из других приложений.
 *
 * Как в старом клиенте - **одно окно**, внутри которого меняются страницы:
 * действие → ожидание → файлы → предзагрузка → выбор плеера.
 * Никаких отдельных окон-диалогов: прогресс предзагрузки и список плееров
 * показываются на месте, в этом же окне.
 *
 * `action=play` играет сразу, `action=choose` открывает окно,
 * без extras - смотрит на запомненное действие.
 */
class MagnetActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var link: String = ""
    private var extraTitle: String = ""
    private var extraPoster: String = ""
    private var extraCategory: String = ""
    private var session: Session? = null
    private var chosenAction = ACTION_ADD_PLAY

    private var filesSection: FilesSection? = null
    private var pickLatch = CountDownLatch(1)
    @Volatile private var pickedFile: SiloApi.TorrentFile? = null

    /** Номер текущего сценария: новый intent или отмена делают старые колбэки недействительными. */
    @Volatile private var flow = 0
    @Volatile private var running = false
    @Volatile private var cancelled = false

    private var preloader: Preloader? = null

    /** Выбранная кнопка действия: на неё ставим фокус при открытии окна (пульт). */
    private var selectedActionChip: SiloChip? = null
    private var initialFocusPending = false

    // Данные для запуска плеера после предзагрузки
    private var playApi: SiloApi? = null
    private var playToken: String = ""
    private var playHash: String = ""
    private var playTitle: String = ""
    private var playPoster: String = ""
    private var playFiles: List<SiloApi.TorrentFile> = emptyList()
    private var playFile: SiloApi.TorrentFile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!resolveLink(intent)) {
            finish()
            return
        }
        if (prefs.serverUrl.isBlank()) {
            toast(getString(R.string.magnet_no_server))
            finish()
            return
        }
        session = if (prefs.isOpenLocalMode) null else prefs.activeSession()
        setContentView(R.layout.activity_magnet)
        applyWindowWidth()
        bindCommon()
        route()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Второй magnet-интент не открывает второе окно: страницы сбрасываются на новую ссылку
        setIntent(intent)
        abortFlow()
        if (!resolveLink(intent)) {
            finish()
            return
        }
        bindCommon()
        session = if (prefs.isOpenLocalMode) null else prefs.activeSession()
        route()
    }

    override fun onBackPressed() {
        abortFlow()
        super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        // Ушли из окна (Home, другое приложение) - предзагрузку не продолжаем.
        // noHistory всё равно закроет окно; при смене конфигурации не трогаем.
        if (!isFinishing && !isChangingConfigurations) {
            abortFlow()
        }
    }

    override fun onDestroy() {
        abortFlow()
        super.onDestroy()
    }

    /**
     * Окно из другого приложения может получить фокус позже, чем построены кнопки.
     * Ставим начальный фокус на выбранное действие по факту получения фокуса.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !initialFocusPending) return
        initialFocusPending = false
        focusChip(selectedActionChip)
    }

    /**
     * Фокус на кнопку: requestFocus не срабатывает в touch-режиме (тап по экрану),
     * поэтому при неудаче используем requestFocusFromTouch.
     */
    private fun focusChip(chip: SiloChip?) {
        chip ?: return
        chip.post {
            if (!chip.requestFocus()) chip.requestFocusFromTouch()
        }
    }

    // ---------------------------------------------------------------- данные

    /** Метаданные, которые передаёт вызывающее приложение (как в старом клиенте). */
    private fun readExtras(intent: Intent?) {
        // Новый intent (onNewIntent) не должен унаследовать метаданные прошлой ссылки
        extraTitle = ""
        extraPoster = ""
        extraCategory = ""
        val extras = intent?.extras ?: return
        extras.keySet().forEach { key ->
            when (key.lowercase(Locale.ROOT)) {
                "title" -> extraTitle = extras.getString(key).orEmpty()
                "poster" -> extraPoster = extras.getString(key).orEmpty()
                "category" -> extraCategory = extras.getString(key).orEmpty()
            }
        }
    }

    private fun resolveLink(intent: Intent?): Boolean {
        readExtras(intent)
        val data = intent?.data?.toString()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val value = listOf(data, text).firstOrNull {
            it != null && (it.startsWith("magnet:") || it.startsWith("torrs://") || it.startsWith("http"))
        }
        if (value.isNullOrBlank()) {
            toast(getString(R.string.magnet_no_link))
            return false
        }
        link = value.trim()
        return true
    }

    /** Имя раздачи: из extras или из magnet-параметра dn. */
    private fun torrentName(): String {
        if (extraTitle.isNotBlank()) return extraTitle
        val match = Regex("[?&]dn=([^&]+)").find(link) ?: return ""
        return runCatching {
            java.net.URLDecoder.decode(match.groupValues[1], "UTF-8").replace('+', ' ')
        }.getOrDefault("")
    }

    private fun bindCommon() {
        findViewById<TextView>(R.id.magnet_name)?.apply {
            val name = torrentName()
            text = name
            visibility = if (name.isBlank()) View.GONE else View.VISIBLE
        }
    }

    /** Окно не шире экрана: на телефоне 680dp не влезают. */
    private fun applyWindowWidth() {
        val screen = resources.displayMetrics.widthPixels
        val target = minOf(dp(680), screen - dp(32))
        findViewById<View>(R.id.magnet_window)?.layoutParams?.apply {
            width = target.coerceAtLeast(dp(280))
        }
    }

    // ---------------------------------------------------------------- маршрут

    private fun route() {
        val extraAction = intent?.getStringExtra(EXTRA_ACTION)
        when (extraAction) {
            "play" -> startPlayOnly(ACTION_PLAY)
            "choose" -> showActionsPage()
            else -> {
                // Сохранённое действие = magnetAction > 0 (magnetRemember - наследие)
                if (prefs.magnetAction > 0) {
                    startPlayOnly(prefs.magnetAction)
                } else {
                    showActionsPage()
                }
            }
        }
    }

    /** Обрывает текущий сценарий: старые фоновые колбэки больше не трогают окно. */
    private fun abortFlow() {
        flow++
        cancelled = true
        running = false
        preloader?.cancel()
        preloader = null
        pickLatch.countDown()
        initialFocusPending = false
    }

    // ---------------------------------------------------------------- страницы

    private enum class Page { ACTIONS, BUSY, FILES, PRELOAD, PLAYERS }

    private fun show(page: Page) {
        val pages = mapOf(
            Page.ACTIONS to R.id.magnet_page_actions,
            Page.BUSY to R.id.magnet_page_busy,
            Page.FILES to R.id.magnet_page_files,
            Page.PRELOAD to R.id.magnet_preload_container,
            Page.PLAYERS to R.id.magnet_page_players,
        )
        pages.forEach { (key, id) ->
            findViewById<View>(id)?.visibility = if (key == page) View.VISIBLE else View.GONE
        }
    }

    private fun showActionsPage() {
        running = false
        show(Page.ACTIONS)

        chosenAction = when (prefs.magnetAction) {
            ACTION_ADD, ACTION_PLAY, ACTION_ADD_PLAY -> prefs.magnetAction
            else -> ACTION_ADD_PLAY
        }
        buildUserChips()
        buildActionChips()

        // Окно всегда временное: галочка снята. Включённая - переписать действие
        // по умолчанию (и пользователя), снятая - оставить сохранённое как было.
        findViewById<CheckBox>(R.id.magnet_remember).isChecked = false
        findViewById<View>(R.id.magnet_run).setOnClickListener {
            if (running) return@setOnClickListener
            val remember = findViewById<CheckBox>(R.id.magnet_remember)
            if (remember.isChecked) {
                prefs.magnetAction = chosenAction
                prefs.magnetRemember = true
                session?.let { prefs.activeUserId = it.id }
            }
            execute(chosenAction, session)
        }
    }

    /** Действие уже известно: сразу выполняем, без страницы выбора. */
    private fun startPlayOnly(action: Int) {
        chosenAction = action
        execute(action, session)
    }

    private fun showBusy(text: String) {
        show(Page.BUSY)
        findViewById<TextView>(R.id.magnet_busy_status)?.text = text
        findViewById<ProgressBar>(R.id.magnet_busy_progress)?.isIndeterminate = true
    }

    private fun showFilesPage(files: List<SiloApi.TorrentFile>) {
        show(Page.FILES)

        val tabs = findViewById<LinearLayout>(R.id.magnet_tabs)
        val list = findViewById<RecyclerView>(R.id.magnet_files)
        if (filesSection == null) {
            filesSection = FilesSection(this, tabs, list, onFileClick = { file ->
                if (!running) return@FilesSection
                pickedFile = file
                pickLatch.countDown()
            })
        }
        // Список файлов получает свою высоту: карточка целиком влезает в экран,
        // прокручивается только список, а не всё окно.
        val screenH = resources.displayMetrics.heightPixels
        val listHeight = minOf(dp(360), maxOf(dp(200), screenH - dp(300)))
        list.layoutParams = (list.layoutParams as LinearLayout.LayoutParams).apply {
            height = listHeight
        }
        filesSection?.submit(files, preferredFileId = preferredFileId(files))
        filesSection?.focusPreferred()
    }

    /** Последний воспроизведённый файл, иначе последний просмотренный. */
    private fun preferredFileId(files: List<SiloApi.TorrentFile>): Int? =
        prefs.lastFile(currentHash).takeIf { it >= 0 }
            ?: files.lastOrNull { it.viewed }?.id

    private var currentHash: String = ""

    private fun buildUserChips(focusSelected: Boolean = false) {
        val container = findViewById<LinearLayout>(R.id.magnet_users)
        container.removeAllViews()
        val sessions = prefs.getSessions()
        if (prefs.isOpenLocalMode) {
            // Открытый сервер: всё выполняется как owner, выбор пользователя не имеет смысла
            container.addView(chipView(LocalAuth.OWNER_ID, true) {})
            return
        }
        if (sessions.isEmpty()) {
            container.addView(chipView(getString(R.string.sidebar_no_user), false) {})
            return
        }
        var selectedChip: SiloChip? = null
        sessions.forEach { item ->
            val chip = chipView(item.username, item.id == session?.id) {
                // Пользователь в этом окне - временный: активного меняем только
                // при сохранении галочкой «Запомнить».
                session = item
                buildUserChips(focusSelected = true)
            }
            if (item.id == session?.id) selectedChip = chip
            container.addView(chip)
        }
        if (focusSelected) focusChip(selectedChip)
    }

    private fun buildActionChips() {
        val container = findViewById<LinearLayout>(R.id.magnet_actions_row)
        container.removeAllViews()
        // На телефоне три плашки в строку не влезают - столбиком на всю ширину
        val compact = resources.configuration.screenWidthDp < 600
        container.orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val options = listOf(
            ACTION_PLAY to getString(R.string.magnet_action_play),
            ACTION_ADD_PLAY to getString(R.string.magnet_action_add_play),
            ACTION_ADD to getString(R.string.magnet_action_add),
        )
        var focusTarget: SiloChip? = null
        options.forEach { (value, label) ->
            val chip = chipView(label, value == chosenAction) {
                chosenAction = value
                buildActionChips()
            }
            if (compact) {
                chip.gravity = android.view.Gravity.CENTER
                chip.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) }
            }
            if (value == chosenAction) focusTarget = chip
            container.addView(chip)
        }
        selectedActionChip = focusTarget
        initialFocusPending = true
        focusChip(focusTarget)
    }

    /** Кнопка выбора по правилу стиля приложения (docs/STYLE.md). */
    private fun chipView(text: String, selected: Boolean, onClick: () -> Unit): SiloChip {
        val chip = SiloChip(this)
        chip.bind(text, selected)
        chip.setOnClickListener { onClick() }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = dp(8) }
        chip.layoutParams = params
        return chip
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- выполнение

    /** 1 - добавить, 2 - играть, 3 - играть и добавить. */
    private fun execute(action: Int, chosen: Session?) {
        if (running) return
        val token = chosen?.token.orEmpty()
        if (chosen == null && !prefs.isOpenLocalMode && prefs.getSessions().isNotEmpty()) {
            toast(getString(R.string.main_not_logged))
            finishSafely()
            return
        }
        running = true
        cancelled = false
        pickedFile = null
        // Своя защёлка на сценарий: abortFlow считает down именно её
        val latch = CountDownLatch(1)
        pickLatch = latch
        val gen = ++flow

        showBusy(getString(R.string.magnet_adding))
        val api = SiloApi(prefs.serverUrl)
        thread {
            val (hash, error) = api.addTorrentEx(
                token = token,
                link = link,
                saveToDb = action != ACTION_PLAY,
                title = extraTitle,
                poster = extraPoster,
                category = extraCategory,
            )
            if (gen != flow) return@thread
            if (hash == null) {
                ui {
                    if (gen != flow) return@ui
                    toast(getString(R.string.magnet_error, error ?: ""))
                    finishSafely()
                }
                return@thread
            }
            currentHash = hash

            if (action == ACTION_ADD) {
                ui {
                    if (gen != flow) return@ui
                    toast(getString(R.string.magnet_added))
                    finishSafely()
                }
                return@thread
            }

            // Ждём метаданные
            ui { if (gen == flow) showBusy(getString(R.string.magnet_metadata)) }
            var details: SiloApi.TorrentDetails? = null
            val deadline = System.currentTimeMillis() + META_WAIT_MS
            while (System.currentTimeMillis() < deadline && gen == flow && !cancelled) {
                val d = runCatching { api.getTorrent(token, hash) }.getOrNull()
                if (d != null && d.files.any { isPlayable(it.name) }) {
                    details = d
                    break
                }
                Thread.sleep(POLL_MS)
            }
            if (gen != flow) return@thread
            val d = details
            if (d == null) {
                ui { toast(getString(R.string.magnet_no_playable)); finishSafely() }
                return@thread
            }

            val playable = d.files.filter { isPlayable(it.name) }
            val file = if (playable.size == 1) {
                playable.first()
            } else {
                // Показываем список файлов в этом же окне и ждём выбор
                ui { if (gen == flow) showFilesPage(playable) }
                latch.await()
                pickedFile
            }
            if (gen != flow || cancelled) return@thread
            if (file == null) {
                ui { finishSafely() }
                return@thread
            }

            prefs.setLastFile(hash, file.id)
            ui {
                if (gen != flow) return@ui
                startPreload(gen, api, token, hash, d, file, playable)
            }
        }
    }

    /** Предзагрузка в этом же окне + запуск плеера без окон-диалогов. */
    private fun startPreload(
        gen: Int,
        api: SiloApi,
        token: String,
        hash: String,
        details: SiloApi.TorrentDetails,
        file: SiloApi.TorrentFile,
        playable: List<SiloApi.TorrentFile>,
    ) {
        currentHash = hash
        playApi = api
        playToken = token
        playHash = hash
        playTitle = details.title.ifBlank { torrentName() }
        playPoster = details.poster
        playFiles = playable
        playFile = file

        show(Page.PRELOAD)
        val container = findViewById<android.widget.FrameLayout>(R.id.magnet_preload_container)
        container.removeAllViews()
        val panel = PreloadPanel(
            context = this,
            onCancel = {
                preloader?.cancel()
                finishSafely()
            },
            onOpenNow = { preloader?.openNow() },
        )
        panel.bind(file.name)
        container.addView(panel.view)

        val preloader = Preloader(this, api, token, hash, file, panel)
        this.preloader = preloader
        preloader.start {
            if (gen != flow || isFinishing) return@start
            panel.done()
            afterPreload(gen)
        }
    }

    private fun afterPreload(gen: Int) {
        if (gen != flow) return
        val file = playFile ?: return finishSafely()
        val api = playApi ?: return finishSafely()
        val pkg = prefs.playerPkg
        if (pkg.isNotBlank()) {
            launchPlayer(pkg)
            return
        }
        showPlayersPage(gen, api, file)
    }

    /** Выбор плеера - страница того же окна, без отдельного диалога. */
    private fun showPlayersPage(gen: Int, api: SiloApi, file: SiloApi.TorrentFile) {
        show(Page.PLAYERS)
        val list = findViewById<RecyclerView>(R.id.players_list)
        val remember = findViewById<CheckBox>(R.id.players_remember)
        remember.isChecked = true
        PlayerPicker.bind(
            context = this,
            list = list,
            remember = remember,
            url = api.streamUrl(playToken, playHash, file.id),
            mime = Players.mimeOf(file.name),
        ) { pkg ->
            if (gen != flow) return@bind
            // «Спрашивать каждый раз» - остаёмся на списке, пока не выбран плеер
            if (pkg.isBlank()) return@bind
            launchPlayer(pkg)
        }
    }

    private fun launchPlayer(pkg: String) {
        val api = playApi ?: return finishSafely()
        val file = playFile ?: return finishSafely()
        val token = playToken
        val hash = playHash
        val playable = playFiles
        val startIndex = playable.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
        val playlist = playable.map { api.streamUrl(token, hash, it.id) to it.name }

        // Отмечаем просмотр (работает для раздач в библиотеке)
        thread { runCatching { api.setFileViewed(token, hash, file.id, true) } }

        Players.launch(
            context = this,
            url = api.streamUrl(token, hash, file.id),
            mime = Players.mimeOf(file.name),
            title = playTitle,
            poster = playPoster,
            pkg = pkg,
            playlist = playlist,
            startIndex = startIndex,
        )
        finishSafely()
    }

    private fun isPlayable(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in PLAYABLE
    }

    private fun finishSafely() {
        if (!isFinishing) finish()
    }

    private fun ui(block: () -> Unit) = runOnUiThread(block)

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_ACTION = "action"

        const val ACTION_ADD = 1
        const val ACTION_PLAY = 2
        const val ACTION_ADD_PLAY = 3

        private const val POLL_MS = 500L
        private const val META_WAIT_MS = 90_000L

        private val PLAYABLE = setOf(
            "mkv", "mp4", "m4v", "avi", "mov", "wmv", "webm", "ts", "m2ts", "mpg", "mpeg",
            "flv", "3gp", "ogv", "divx", "vob", "rmvb", "mts", "asf",
            "mp3", "flac", "aac", "m4a", "ogg", "oga", "wav", "wma", "opus", "ape", "ac3",
            "dts", "mka", "alac", "aiff",
        )
    }
}
