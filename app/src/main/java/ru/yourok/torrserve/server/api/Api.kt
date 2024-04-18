package ru.yourok.torrserve.server.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.server.models.ffp.FFPModel
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.server.models.torrent.TorrentDetails
import ru.yourok.torrserve.settings.BTSets
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Net
import java.io.InputStream
import java.net.URLEncoder

object Api {
    /// all getAuth / postAuth calls can throw network exceptions
    class ApiException(msg: String, val code: Int) : Exception(msg)

    /// Server
    fun echo(): String {
        return try {
            val host = Net.getHostUrl("/echo")
            Net.getAuth(host)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) println(e.message)
            ""
        }
    }

    /* used for remote server version check */
    fun remoteEcho(url: String): String {
        return try {
            val host = "$url/echo"
            Net.getAuth(host, 3000) // fast response, in ms
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) println(e.message)
            ""
        }
    }

    fun shutdown(): String {
        return try {
            val host = Net.getHostUrl("/shutdown")
            Net.getAuth(host)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) println(e.message)
            ""
        }
    }

    /// Torrents
    fun addTorrent(link: String, title: String, poster: String, category: String, data: String, save: Boolean): Torrent {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("add", link = link, title = title, poster = poster, category = category, data = data, save_to_db = save).toString()
        val resp = postJson(host, req)
        return Gson().fromJson(resp, Torrent::class.java)
    }

    fun getTorrent(hash: String): Torrent {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("get", hash).toString()
        val resp = postJson(host, req)
        return Gson().fromJson(resp, Torrent::class.java)
    }

    fun remTorrent(hash: String) {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("rem", hash).toString()
        postJson(host, req)
    }

    fun listTorrent(): List<Torrent> {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("list").toString()
        val resp = postJson(host, req)
        return if (Settings.sortTorrByTitle())
            Gson().fromJson(resp, Array<Torrent>::class.java).toList().sortedWith(compareBy { it.title })
        else
            Gson().fromJson(resp, Array<Torrent>::class.java).toList()
    }

    fun dropTorrent(hash: String) {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("drop", hash).toString()
        postJson(host, req)
    }

    fun uploadTorrent(file: InputStream, title: String, poster: String, category: String, data: String, save: Boolean): Torrent {
        val host = Net.getHostUrl("/torrent/upload")
        val resp = Net.uploadAuth(host, title, poster, category, data, file, save)
        return Gson().fromJson(resp, Torrent::class.java)
    }

    // Settings
    fun getSettings(): BTSets {
        val host = Net.getHostUrl("/settings")
        val req = Request("get").toString()
        val resp = postJson(host, req)
        return Gson().fromJson(resp, BTSets::class.java)
    }

    fun setSettings(sets: BTSets) {
        val host = Net.getHostUrl("/settings")
        val req = SettingsReq("set", sets).toString()
        postJson(host, req)
    }

    fun defSettings() {
        val host = Net.getHostUrl("/settings")
        val req = Request("def").toString()
        postJson(host, req)
    }

    // Viewed
    fun listViewed(hash: String): List<Viewed> {
        val host = Net.getHostUrl("/viewed")
        val req = ViewedReq("list", hash).toString()
        val resp = postJson(host, req)
        if (resp.isBlank())
            return emptyList()
        return Gson().fromJson(resp, Array<Viewed>::class.java).toList()
    }

    fun setViewed(hash: String, index: Int) {
        val host = Net.getHostUrl("/viewed")
        val req = ViewedReq("set", hash, index).toString()
        postJson(host, req)
    }

    fun remViewed(hash: String, id: Int = -1) {
        val host = Net.getHostUrl("/viewed")
        val req = if (id > 0) ViewedReq("rem", hash, id).toString() else ViewedReq("rem", hash).toString()
        postJson(host, req)
    }

    fun getFFP(hash: String, id: Int): FFPModel? {
        val host = Net.getHostUrl("/ffp/${hash}/${id}")
        val resp = Net.getAuth(host, 30000) // long response, in ms
        if (resp.isBlank())
            return null
        return Gson().fromJson(resp, FFPModel::class.java)
    }

    fun searchTorrents(query: String): List<TorrentDetails>? {
        val host = Net.getHostUrl("/search?query=${URLEncoder.encode(query, "UTF-8")}")
        val resp = Net.getAuth(host)
        if (resp.isBlank())
            return null
        return Gson().fromJson(resp, Array<TorrentDetails>::class.java).toList()
    }

    private fun postJson(url: String, json: String): String {
        return Net.postAuth(url, json)
    }

    suspend fun getMatrixVersionInt(): Int {
        return try {
            var verStr: String
            withContext(Dispatchers.IO) {
                verStr = echo()
            }
            val isMatrix = verStr.contains("MatriX", true)
            val numbers = Regex("[0-9]+").findAll(verStr)
                .map(MatchResult::value)
                .toList()
            val verMajor = numbers.firstOrNull()?.toIntOrNull() ?: 0
            //val verMinor = numbers.getOrNull(1)?.toIntOrNull() ?: 0
            //Log.i("getMatrixVersionInt", "$verMajor")
            return if (isMatrix) verMajor else 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun haveCategories(): Boolean {
        var vi = 0
        coroutineScope {
            val data = async(Dispatchers.IO) {
                getMatrixVersionInt()
            }
            val result = data.await()
            vi = result
        }
        return vi > 131 // MatriX.132 add Categories
    }
}