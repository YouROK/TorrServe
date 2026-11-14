package ru.yourok.torrserve.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
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
 * Две страницы в одном окне:
 * 1. действие (пользователь + Играть / Играть и добавить / Добавить);
 * 2. файлы (появляется после действия, если файлов несколько).
 *
 * Как в старом клиенте: `action=play` играет сразу, `action=choose` открывает окно,
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
    private val pickLatch = CountDownLatch(1)
    @Volatile private var pickedFile: SiloApi.TorrentFile? = null
    @Volatile private var running = false
    @Volatile private var cancelled = false

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
        session = prefs.activeSession()
        setContentView(R.layout.activity_magnet)
        bindCommon()

        val extraAction = intent?.getStringExtra(EXTRA_ACTION)
        when (extraAction) {
            "play" -> preparePlayOnly(ACTION_PLAY)
            "choose" -> showActionsPage()
            else -> {
                if (prefs.magnetRemember && prefs.magnetAction > 0) {
                    preparePlayOnly(prefs.magnetAction)
                } else {
                    showActionsPage()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (!resolveLink(intent)) finish()
    }

    // ---------------------------------------------------------------- данные

    /** Метаданные, которые передаёт вызывающее приложение (как в старом клиенте). */
    private fun readExtras(intent: Intent?) {
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

    // ---------------------------------------------------------------- страницы

    private fun showActionsPage() {
        findViewById<View>(R.id.magnet_page_actions).visibility = View.VISIBLE
        findViewById<View>(R.id.magnet_page_files).visibility = View.GONE

        chosenAction = when (prefs.magnetAction) {
            ACTION_ADD, ACTION_PLAY, ACTION_ADD_PLAY -> prefs.magnetAction
            else -> ACTION_ADD_PLAY
        }
        buildUserChips()
        buildActionChips()

        findViewById<CheckBox>(R.id.magnet_remember).apply {
            isChecked = prefs.magnetRemember
        }
        findViewById<View>(R.id.magnet_run).setOnClickListener {
            if (running) return@setOnClickListener
            val remember = findViewById<CheckBox>(R.id.magnet_remember)
            if (remember.isChecked) {
                prefs.magnetAction = chosenAction
                prefs.magnetRemember = true
            } else {
                prefs.magnetRemember = false
            }
            execute(chosenAction, session)
        }
    }

    /** Компактный режим: действие уже известно, сразу переходим к файлам. */
    private fun preparePlayOnly(action: Int) {
        chosenAction = action
        findViewById<View>(R.id.magnet_page_actions).visibility = View.GONE
        findViewById<View>(R.id.magnet_page_files).visibility = View.VISIBLE
        execute(action, session)
    }

    private fun showFilesPage(files: List<SiloApi.TorrentFile>) {
        findViewById<View>(R.id.magnet_page_actions).visibility = View.GONE
        findViewById<View>(R.id.magnet_page_files).visibility = View.VISIBLE

        // На странице файлов не показываем статусы и прогресс
        findViewById<TextView>(R.id.magnet_status)?.visibility = View.GONE

        val tabs = findViewById<LinearLayout>(R.id.magnet_tabs)
        val list = findViewById<RecyclerView>(R.id.magnet_files)
        if (filesSection == null) {
            filesSection = FilesSection(this, tabs, list, onFileClick = { file ->
                if (!running) return@FilesSection
                pickedFile = file
                pickLatch.countDown()
            })
        }
        filesSection?.submit(files, preferredFileId = preferredFileId(files))
        filesSection?.focusPreferred()
    }

    /** Последний воспроизведённый файл, иначе последний просмотренный. */
    private fun preferredFileId(files: List<SiloApi.TorrentFile>): Int? =
        prefs.lastFile(currentHash).takeIf { it >= 0 }
            ?: files.lastOrNull { it.viewed }?.id

    private var currentHash: String = ""

    private fun buildUserChips() {
        val container = findViewById<LinearLayout>(R.id.magnet_users)
        container.removeAllViews()
        val sessions = prefs.getSessions()
        if (sessions.isEmpty()) {
            container.addView(chipView(getString(R.string.sidebar_no_user), false) {})
            return
        }
        sessions.forEach { item ->
            container.addView(chipView(item.username, item.id == session?.id) {
                session = item
                prefs.activeUserId = item.id
                buildUserChips()
            })
        }
    }

    private fun buildActionChips() {
        val container = findViewById<LinearLayout>(R.id.magnet_actions_row)
        container.removeAllViews()
        val options = listOf(
            ACTION_PLAY to getString(R.string.magnet_action_play),
            ACTION_ADD_PLAY to getString(R.string.magnet_action_add_play),
            ACTION_ADD to getString(R.string.magnet_action_add),
        )
        var focusTarget: View? = null
        options.forEach { (value, label) ->
            val chip = chipView(label, value == chosenAction) {
                chosenAction = value
                buildActionChips()
                // Возвращаем фокус на выбранную плашку
                container.getChildAt(options.indexOfFirst { it.first == value })?.requestFocus()
            }
            if (value == chosenAction) focusTarget = chip
            container.addView(chip)
        }
        focusTarget?.post { focusTarget.requestFocus() }
    }

    private fun chipView(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        val chip = TextView(this).apply {
            this.text = text
            setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    this@MagnetActivity,
                    if (selected) R.color.silo_amber else R.color.silo_text,
                )
            )
            textSize = 14f
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = androidx.core.content.ContextCompat.getDrawable(
                this@MagnetActivity,
                if (selected) R.drawable.bg_tab else R.drawable.bg_tab_unselected,
            )
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
        }
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
        if (chosen == null && prefs.getSessions().isNotEmpty()) {
            toast(getString(R.string.main_not_logged))
            finishSafely()
            return
        }
        running = true
        cancelled = false

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
            if (hash == null) {
                ui { toast(getString(R.string.magnet_error, error ?: "")); finishSafely() }
                return@thread
            }
            currentHash = hash

            if (action == ACTION_ADD) {
                ui { toast(getString(R.string.magnet_added)); finishSafely() }
                return@thread
            }

            // Ждём метаданные
            setStatus(getString(R.string.magnet_metadata))
            var details: SiloApi.TorrentDetails? = null
            val deadline = System.currentTimeMillis() + META_WAIT_MS
            while (System.currentTimeMillis() < deadline) {
                val d = runCatching { api.getTorrent(token, hash) }.getOrNull()
                if (d != null && d.files.any { isPlayable(it.name) }) {
                    details = d
                    break
                }
                Thread.sleep(POLL_MS)
            }
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
                ui { showFilesPage(playable) }
                pickLatch.await()
                pickedFile
            }
            if (file == null || cancelled) {
                ui { finishSafely() }
                return@thread
            }

            prefs.setLastFile(hash, file.id)
            ui {
                PreloadDialog.prepare(this, api, token, hash, file) {
                    // Отмечаем просмотр (работает для раздач в библиотеке)
                    thread {
                        runCatching { api.setFileViewed(token, hash, file.id, true) }
                    }
                    Players.play(
                        context = this,
                        url = api.streamUrl(token, hash, file.id),
                        mime = Players.mimeOf(file.name),
                        title = d.title.ifBlank { torrentName() },
                        poster = d.poster,
                        playlist = playable.map { api.streamUrl(token, hash, it.id) to it.name },
                        startIndex = playable.indexOfFirst { it.id == file.id }.coerceAtLeast(0),
                    )
                    finishSafely()
                }
            }
        }
    }

    private fun setStatus(text: String) = ui {
        val status = findViewById<TextView>(R.id.magnet_status) ?: return@ui
        status.visibility = View.VISIBLE
        status.text = text
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
