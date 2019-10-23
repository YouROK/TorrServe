package ru.yourok.torrserve.server.torrent

import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.utils.Mime
import java.io.File
import kotlin.concurrent.thread

object Torrent {
    const val TorrentSTAdded = 0
    const val TorrentSTGettingInfo = 1
    const val TorrentSTPreload = 2
    const val TorrentSTWorking = 3
    const val TorrentSTClosed = 4

    fun getFiles(torr: JSObject): List<JSObject> {
        if (torr.js.has("Files")) {
            val arr = torr.js.getJSONArray("Files")
            val flist = mutableListOf<JSObject>()
            for (i in 0 until arr.length())
                flist.add(JSObject(arr.getJSONObject(i)))
            return flist.toList()
        } else
            return listOf()
    }

    fun getPlayableFiles(torr: JSObject): List<JSObject> {
        val files = getFiles(torr)
        val retList = mutableListOf<JSObject>()
        files.forEach {
            val name = it.get("Name", "")
            if (Mime.getMimeType(name) != "*/*") {
                val size = it.getLong("Size", 0L)
                if (File(name).extension.toLowerCase() == "m2ts") {
                    if (size > 1073741824L)
                        retList.add(it)
                } else
                    retList.add(it)
            } else if (name.toLowerCase().contains("bdmv/index.bdmv")) {
                retList.add(it)
            }
        }
        return retList
    }

    fun waitInfo(hash: String, onProgress: (stat: JSObject) -> Unit) {
        var count = 0
        while (true) {
            try {
                val stat = Api.torrentStat(hash)
                val stTorr = stat.getInt("TorrentStatus", -1)
                onProgress(stat)

                if (stTorr != TorrentSTGettingInfo) {
                    if (getFileStats(stat) > 0 || count > 9)
                        break
                    else
                        count++
                }
                Thread.sleep(100)
            } catch (e: Exception) {
                Thread.sleep(1000)
            }
        }
    }

    fun getFileStats(stat: JSObject): Int {
        if (!stat.js.has("FileStats"))
            return -1
        val arr = stat.js.getJSONArray("FileStats")
        return arr.length()
    }

    fun preload(torr: JSObject, file: JSObject, onProgress: (stat: JSObject) -> Unit, onError: (msg: String) -> Unit) {
        val th = thread {
            try {
                val link = file.getString("Preload", "")
                if (link.isEmpty()) {
                    onError("Empty preload link")
                } else
                    Api.torrentPreload(link)
            } catch (e: Exception) {
                onError(e.message ?: "Error connect to server")
            }
        }
        Thread.sleep(1000)
        val hash = torr.getString("Hash", "")
        if (hash.isEmpty()) {
            onError("Error open torrent, hash empty")
            return
        }
        while (true) {
            try {
                val stat = Api.torrentStat(hash)
                thread {
                    onProgress(stat)
                }
                val stTorr = stat.getInt("TorrentStatus", -1)
                val preloadedBytes = stat.getLong("PreloadedBytes", 0L)
                val preloadSize = stat.getLong("PreloadSize", 0L)

                if (stTorr == TorrentSTWorking || preloadedBytes > preloadSize)
                    break
                Thread.sleep(100)
            } catch (e: Exception) {
                Thread.sleep(1000)
            }
        }
        th.join(15000)
    }
}