package ru.yourok.torrserve.server.updater

import org.json.JSONArray
import ru.yourok.torrserve.serverloader.ServerFile
import ru.yourok.torrserve.serverloader.Updater
import ru.yourok.torrserve.utils.Http
import java.io.FileOutputStream
import java.io.IOException

data class Release(val Version: String, val Links: Map<String, String>)

object Releases {
    fun get(): List<Release> {
        val strJS = Http("http://tor-serve.surge.sh/releases.json").read()
        if (strJS.isNotEmpty()) {
            val js = JSONArray(strJS)
            val ret = mutableListOf<Release>()
            for (i in 0 until js.length()) {
                val relJs = js.getJSONObject(i)
                val ver = relJs.optString("Version", "")
                val linksJs = relJs.getJSONObject("Links")
                val links = mutableMapOf<String, String>()
                for (i in 0 until linksJs.names().length()) {
                    val key = linksJs.names().getString(i)
                    val v = linksJs.getString(key)
                    links[key] = v
                }

                if (ver.isNotEmpty() && links != null) {
                    ret.add(Release(ver, links))
                }
            }
            return ret
        }
        return emptyList()
    }

    fun updateCustomServer(link: String, onProgress: ((prc: Int) -> Unit)?) {
        val arch = Updater.getArch()
        if (arch.isEmpty())
            throw IOException("error get arch")

        val http = Http(link)
        http.getEntity().apply {
            this ?: throw IOException("error get server: $link")
            content ?: throw IOException("error get server: $link")

            ServerFile.deleteServer()
            FileOutputStream(ServerFile.get()).use { fileOut ->
                if (onProgress == null)
                    content.copyTo(fileOut)
                else {
                    val buffer = ByteArray(65535)
                    val length = contentLength + 1
                    var offset: Long = 0
                    while (true) {
                        val readed = content.read(buffer)
                        offset += readed
                        val prc = (offset * 100 / length).toInt()
                        onProgress(prc)
                        if (readed <= 0)
                            break
                        fileOut.write(buffer, 0, readed)
                    }
                    fileOut.flush()
                }
                fileOut.flush()
                fileOut.close()
                if (!ServerFile.get().setExecutable(true))
                    throw IOException("error set exec permission")
            }
        }
        ServerFile.run()
    }
}