package ru.yourok.torrserve.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.urlEncode
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.FileStat
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.dialogs.InfoDialog
import java.io.File
import java.util.Locale

object TorrentHelper {
    const val TorrentSTAdded = 0
    const val TorrentSTGettingInfo = 1
    const val TorrentSTPreload = 2
    const val TorrentSTWorking = 3
    const val TorrentSTClosed = 4
    const val TorrentInDB = 5

    fun getPlayableFiles(torr: Torrent): List<FileStat> {
        if (torr.file_stats.isNullOrEmpty())
            return emptyList()

        val files = torr.file_stats
        val retList = mutableListOf<FileStat>()

        files?.forEach {
            val path = it.path
            if (Mime.getMimeType(path) != "*/*") {
                val size = it.length
                if (File(path).extension.lowercase() == "m2ts" ||
                    File(path).extension.lowercase() == "mts" ||
                    File(path).extension.lowercase() == "ts"
                ) {
                    if (size > 1073741824L)
                        retList.add(it)
                } else
                    retList.add(it)
            } else if (path.lowercase(Locale.getDefault()).contains("bdmv/index.bdmv")) {
                retList.add(it)
            }
        }
        return retList
    }

    fun waitFiles(hash: String): Torrent? {
        var count = 0
        while (true) {
//            if (BuildConfig.DEBUG) Log.d("*****", "waitFiles($count) for $hash")
            try {
                val torr = Api.getTorrent(hash)
//                if (torr.file_stats != null) {
                if ((torr.file_stats?.size ?: 0) > 0 || count > 59)
                    return torr
//                    else
//                        count++
//                }
                count++
                Thread.sleep(1000)
            } catch (e: Exception) {
                e.printStackTrace()
                count++
                if (count > 59)
                    return null
                Thread.sleep(1000)
            }
        }
    }

    fun getTorrentMagnet(torr: Torrent): String {
        return "magnet:?xt=urn:btih:${torr.hash}&dn=${torr.title.urlEncode()}"
    }

    fun getTorrentPlayLink(torr: Torrent, index: Int): String {
        val file = findFile(torr, index)
        val name = file?.let { File(it.path).name } ?: torr.title
        return Net.getHostUrl("/stream/${name.urlEncode()}?link=${torr.hash}&index=${index}&play")
    }

    fun getFileLink(torr: Torrent, file: FileStat?): String {
        val name = file?.let { File(it.path).name } ?: torr.title
        return Net.getHostUrl("/stream/${name.urlEncode()}?link=${torr.hash}&index=${file?.id}&play")
    }

    fun getTorrentPreloadLink(torr: Torrent, index: Int): String {
        return Net.getHostUrl("/stream/${torr.title.urlEncode()}?link=${torr.hash}&index=${index}&preload")
    }

    suspend fun preloadTorrent(torr: Torrent, index: Int) = withContext(Dispatchers.IO) {
        try {
            val link = getTorrentPreloadLink(torr, index)
            Net.getAuth(link)
        } catch (e: Exception) {
            e.printStackTrace() // FIXME: why there is timeout on Net.getAuth?
            // e.message?.let { App.toast(it) }
        }
    }

    fun findFile(torrent: Torrent, index: Int): FileStat? {
        torrent.file_stats?.forEach {
            if (it.id == index)
                return it
        }
        return null
    }

    fun findIndex(torrent: Torrent, file: FileStat): Int {
        torrent.file_stats?.forEachIndexed { index, fileStat ->
            if (fileStat.id == file.id)
                return index
        }
        return -1
    }

    suspend fun showFFPInfo(context: Context, torrLink: String, torrent: Torrent) {
        val probe = try { // stats 1st torrent file
            if (torrLink.isNotBlank())
                App.toast("${context.getString(R.string.stat_string_info)} …", true)
            val files = getPlayableFiles(torrent)
            if (files.isNotEmpty()) {
                Api.getFFP(torrent.hash, files.first().id)
            } else {
                null
            }
        } catch (e: Exception) {
            App.toast(e.message ?: context.getString(R.string.error_retrieve_data))
            null
        }
        probe?.let { ffp ->
            val format = ffp.format
            val streams = ffp.streams
            val videoDesc = mutableListOf<String>()
            val audioDesc = mutableListOf<String>()
            val subsDesc = mutableListOf<String>()
            try {
                streams.forEach { st -> // count in format.nb_streams
                    when (st.codec_type) {
                        "video" -> {
                            if (st.codec_name != "mjpeg" && st.codec_name != "png") { // exclude posters
                                videoDesc.add("${st.width}x${st.height}")
                                videoDesc.add(st.codec_long_name.ifEmpty { st.codec_name.uppercase() })
                            }
                        }

                        "audio" -> {
                            var audio = ""
                            st.tags?.let {
                                it.language?.let { lang ->
                                    audio = if (!it.title.isNullOrBlank())
                                        "[" + lang.uppercase() + "] " + it.title.uppercase().cleanup()
                                    else
                                        "[" + lang.uppercase() + "]"
                                } ?: { audio = it.title?.uppercase()?.cleanup().toString() }
                            }
                            val channels = st.channel_layout ?: (st.channels.toString() + "CH")
                            if (audio.isNotBlank())
                                audioDesc.add(audio + " " + st.codec_name.uppercase() + "/" + channels)
                            else
                                audioDesc.add(st.codec_name.uppercase() + "/" + channels)
                        }

                        "subtitle" -> {
                            var titles = ""
                            st.tags?.let {
                                it.language?.let { lang ->
                                    titles = if (it.title.isNullOrBlank())
                                        "[" + lang.uppercase() + "]"
                                    else
                                        "[" + lang.uppercase() + "] " + it.title.cleanup()
                                } ?: { titles = it.title?.cleanup().toString() }
                                subsDesc.add(titles)
                            }
                        }

                        else -> {
                            // TODO
                        }
                    }
                }
                val title = format.tags?.title ?: torrent.title
                val size = Format.byteFmt(ffp.format.size.toDouble())
                val duration = Format.durFmt(ffp.format.duration.toDouble())
                val bitrate = Format.speedFmt(ffp.format.bit_rate.toDouble() / 8)
                withContext(Dispatchers.Main) {
                    InfoDialog(context).show(torrLink, title.trim(), format.format_long_name, videoDesc.joinToString(" ● "), audioDesc.joinToString(" ● "), subsDesc.joinToString(" ● "), size, duration, bitrate)
                }
            } catch (e: Exception) {
                e.message?.let { App.toast(it) }
            }
        }
    }

    private fun String.cleanup(): String {
        return this
            .replace("[", "")
            .replace("]", "")
            .trim()
    }
}