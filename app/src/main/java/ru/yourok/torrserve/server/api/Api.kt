package ru.yourok.torrserve.server.api

import com.google.gson.Gson
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
    private const val duration = 30000 // total request timeout duration, in ms

    /// Server
    fun echo(): String {
        return try {
            val host = Net.getHostUrl("/echo")
            Net.getAuth(host)
        } catch (e: Exception) {
            println(e.message)
            ""
        }
    }

    fun remoteEcho(url: String): String {
        return try {
            val host = "$url/echo"
            Net.getAuth(host)
        } catch (e: Exception) {
            println(e.message)
            ""
        }
    }

    fun shutdown(): String {
        return try {
            val host = Net.getHostUrl("/shutdown")
            Net.getAuth(host)
        } catch (e: Exception) {
            println(e.message)
            ""
        }
    }

    /// Torrents
    fun addTorrent(link: String, title: String, poster: String, data: String, save: Boolean): Torrent {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("add", link = link, title = title, poster = poster, data = data, save_to_db = save).toString()
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
        return if (Settings.sortTorrents())
            Gson().fromJson(resp, Array<Torrent>::class.java).toList().sortedWith(compareBy { it.title })
        else
            Gson().fromJson(resp, Array<Torrent>::class.java).toList()
    }

    fun dropTorrent(hash: String) {
        val host = Net.getHostUrl("/torrents")
        val req = TorrentReq("drop", hash).toString()
        postJson(host, req)
    }

    fun uploadTorrent(file: InputStream, title: String, poster: String, data: String, save: Boolean): Torrent {
        val host = Net.getHostUrl("/torrent/upload")
        val resp = Net.uploadAuth(host, title, poster, data, file, save)
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

    fun remViewed(hash: String) {
        val host = Net.getHostUrl("/viewed")
        val req = ViewedReq("rem", hash).toString()
        postJson(host, req)
    }

    fun getFFP(hash: String, id: Int): FFPModel? {
        val host = Net.getHostUrl("/ffp/${hash}/${id}")
        val resp = Net.getAuth(host, duration)
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
}