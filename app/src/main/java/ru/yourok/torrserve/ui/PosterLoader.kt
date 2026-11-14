package ru.yourok.torrserve.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Загрузчик постеров: память + диск, без «мигания».
 * Картинка не сбрасывается, пока не загрузится новая, поэтому при обновлении карточки
 * по SSE постер остаётся на месте.
 */
object PosterLoader {

    private val cache = LruCache<String, Bitmap>(192)
    private val executor = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private val pending = Collections.synchronizedSet(HashSet<String>())
    private var diskDir: File? = null

    fun init(context: Context) {
        if (diskDir == null) {
            diskDir = File(context.cacheDir, "posters").apply { mkdirs() }
        }
    }

    fun load(context: Context?, url: String, view: ImageView) {
        init(context ?: view.context)
        if (url.isBlank()) {
            view.tag = null
            view.setImageResource(ru.yourok.torrserve.R.drawable.bg_poster_placeholder)
            return
        }
        // Чужой URL в этой ячейке - старую картинку не показываем
        if (view.tag != url) {
            view.tag = url
            view.setImageResource(ru.yourok.torrserve.R.drawable.bg_poster_placeholder)
        }

        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }

        if (!pending.add(url)) return
        executor.execute {
            val bmp = readDisk(url) ?: download(url)
            pending.remove(url)
            if (bmp == null) return@execute
            cache.put(url, bmp)
            writeDisk(url, bmp)
            main.post { if (view.tag == url) view.setImageBitmap(bmp) }
        }
    }

    private fun download(url: String): Bitmap? = try {
        URL(url).openStream().use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    private fun fileFor(url: String): File? {
        val dir = diskDir ?: return null
        val name = MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, name)
    }

    private fun readDisk(url: String): Bitmap? = try {
        fileFor(url)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    } catch (e: Exception) {
        null
    }

    private fun writeDisk(url: String, bitmap: Bitmap) {
        try {
            fileFor(url)?.outputStream()?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (e: Exception) {
            // кэш на диске необязателен
        }
    }
}
