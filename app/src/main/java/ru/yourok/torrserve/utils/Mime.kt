package ru.yourok.torrserve.utils

import java.io.File
import java.util.Locale

/**
 * Created by yourok on 03.03.18.
 */
object Mime {

    fun getMimeType(filepath: String): String {
        val ext = File(filepath).extension.lowercase(Locale.getDefault())
        if (extVideo.contains(ext))
            return "video/*"
        if (extAudio.contains(ext))
            return "audio/*"
        return "*/*"
    }

    val extVideo = listOf(
        "3g2",
        "3gp",
        "aaf",
        "asf",
        "avchd",
        "avi",
        "drc",
        "flv",
        "iso",
        "m2v",
        "m2ts",
        "m4p",
        "m4v",
        "mkv",
        "mng",
        "mov",
        "mp2",
        "mp4",
        "mpe",
        "mpeg",
        "mpg",
        "mpv",
        "mxf",
        "nsv",
        "ogg",
        "ogv",
        "ts",
        "qt",
        "rm",
        "rmvb",
        "roq",
        "svi",
        "vob",
        "webm",
        "wmv",
        "yuv"
    )

    val extAudio = listOf(
        "aac",
        "aiff",
        "ape",
        "au",
        "flac",
        "gsm",
        "it",
        "m3u",
        "m4a",
        "mid",
        "mod",
        "mp3",
        "mpa",
        "pls",
        "ra",
        "s3m",
        "sid",
        "wav",
        "wma",
        "xm"
    )
}