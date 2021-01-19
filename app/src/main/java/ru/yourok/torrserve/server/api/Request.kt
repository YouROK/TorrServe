package ru.yourok.torrserve.server.api

import com.google.gson.Gson
import ru.yourok.torrserve.settings.BTSets

open class Request(val action: String) {
    override fun toString(): String {
        return Gson().toJson(this)
    }
}

class SettingsReq(
    action: String,
    val Sets: BTSets
) : Request(action)

class TorrentReq(
    action: String,
    val hash: String = "",
    val link: String = "",
    val title: String = "",
    val poster: String = "",
    val data: String = "",
    val save_to_db: Boolean = false,
) : Request(action)

class ViewedReq(
    action: String,
    val hash: String = "",
    val file_index: Int = 0
) : Request(action)

data class Viewed(
    val hash: String,
    val file_index: Int
)