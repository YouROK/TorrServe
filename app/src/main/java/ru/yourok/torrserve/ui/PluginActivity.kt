package ru.yourok.torrserve.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Prefs

/**
 * Экран плагина: веб-страница плагина Silo в WebView.
 * Токен активного пользователя кладём в cookie silo_token - плагин ходит к API от его имени.
 */
class PluginActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var titleView: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugin)

        val prefs = Prefs(this)
        val href = intent.getStringExtra(EXTRA_HREF).orEmpty()
        val name = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val url = prefs.serverUrl.trimEnd('/') + href

        titleView = findViewById(R.id.plugin_title)
        titleView.text = name
        findViewById<android.view.View>(R.id.plugin_back).setOnClickListener { goBack() }

        web = findViewById(R.id.plugin_web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }

        // Токен в cookie: страницы плагина обращаются к /api/* от имени пользователя
        val token = prefs.activeToken
        if (token.isNotBlank()) {
            CookieManager.getInstance().setCookie(prefs.serverUrl, "silo_token=$token; Path=/")
            CookieManager.getInstance().setCookie(url, "silo_token=$token; Path=/")
            CookieManager.getInstance().flush()
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                // Внешние ссылки открываем в браузере
                return if (target.toString().startsWith(prefs.serverUrl)) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, target))
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                titleView.text = view.title?.takeIf { it.isNotBlank() } ?: name
            }
        }

        web.loadUrl(url)
    }

    private fun goBack() {
        if (web.canGoBack()) web.goBack() else finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Есть история в WebView - шаг назад, иначе отдаём обработку системе
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_HREF = "href"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, href: String, title: String) =
            Intent(context, PluginActivity::class.java)
                .putExtra(EXTRA_HREF, href)
                .putExtra(EXTRA_TITLE, title)
    }
}
