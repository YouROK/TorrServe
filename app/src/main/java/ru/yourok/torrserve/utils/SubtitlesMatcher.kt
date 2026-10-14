package ru.yourok.torrserve.utils

import ru.yourok.torrserve.server.models.torrent.FileStat
import java.io.File
import java.util.Locale
import java.util.MissingResourceException

data class SubtitlesMediaItem(
    val uri: String,
    val mimeType: String,
    val language: String?,
    val label: String
)

object SubtitlesMatcher {

    private val delimiters = setOf('_', '.', ' ', '-')

    /**
     * Splits [files] into the subtitles that belong to the video named [base] (resolved to
     * [SubtitlesMediaItem]s) and the files that remain for the next video.
     */
    fun findSubtitles(
        base: String,
        files: List<FileStat>,
        playLinkResolver: (Int) -> String
    ): Pair<List<SubtitlesMediaItem>, List<FileStat>> {
        val (matching, remaining) = files.partition { f ->
            val subtitleFileName = f.path.substringAfterLast(File.separatorChar)
            isSubtitleMatch(base, subtitleFileName.substringBeforeLast('.'))
        }
        return matching.map { parseSubtitleFile(it, playLinkResolver) } to remaining
    }

    private fun isSubtitleMatch(
        videoBaseName: String,
        subtitleBaseName: String
    ): Boolean {
        if (!subtitleBaseName.startsWith(videoBaseName)) {
            return false
        }

        if (videoBaseName == subtitleBaseName) {
            return true
        }

        if (subtitleBaseName.length > videoBaseName.length) {
            val nextChar = subtitleBaseName[videoBaseName.length]
            return nextChar in delimiters
        }

        return false
    }

    private fun parseSubtitleFile(
        subtitleFile: FileStat,
        playLinkResolver: (Int) -> String
    ): SubtitlesMediaItem {
        val fileName = subtitleFile.path.substringAfterLast(File.separatorChar)
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val baseNameWithTags = fileName.substringBeforeLast('.')
        //[Movie_Name].[Language_Code].[Modifier].ext
        val tags = extractSubtitleTags(baseNameWithTags)

        return SubtitlesMediaItem(
            uri = playLinkResolver(subtitleFile.id),
            mimeType = Mime.subtitleMimeType(extension),
            language = tags.first,
            label = tags.second
        )
    }

    private fun extractSubtitleTags(baseNameWithTags: String): Pair<String?, String> {
        val tags = mutableListOf<String>()

        var i = baseNameWithTags.length - 1
        val currentTag = StringBuilder()

        while (i >= 0 && tags.size < 2) {
            val char = baseNameWithTags[i]
            if (char in delimiters) {
                if (currentTag.isNotEmpty()) {
                    tags.add(0, currentTag.reverse().toString())
                    currentTag.clear()
                }
            } else {
                currentTag.append(char)
            }
            i--
        }

        if (currentTag.isNotEmpty()) {
            tags.add(0, currentTag.reverse().toString())
        }

        var label: String? = null
        var lang: String? = null
        when (tags.size) {
            1 -> {
                if (isLanguage(tags[0])) {
                    lang = tags[0]
                } else {
                    label = tags[0]
                }
            }

            2 -> {
                val firstTag = tags[0]
                val secondTag = tags[1]

                if (isLanguage(firstTag)) {
                    lang = firstTag
                    label = secondTag
                } else if (isLanguage(secondTag)) {
                    lang = secondTag
                    label = firstTag
                } else {
                    label = secondTag
                }
            }
        }

        return Pair(lang, label ?: "Unknown")
    }

    private fun isLanguage(tag: String): Boolean =
        tag.length == 3 && tag.uppercase() in iso3LanguageCodes
}

private val iso3LanguageCodes by lazy {
    Locale.getAvailableLocales()
        .mapNotNull { locale ->
            try {
                locale.isO3Language.uppercase() // ISO 639-2/3 code
            } catch (_: MissingResourceException) {
                null
            }
        }
        .toSet()
}
