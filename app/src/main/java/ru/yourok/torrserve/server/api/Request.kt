package ru.yourok.torrserve.server.api

import com.google.gson.Gson

open class Request(val action: String) {
    override fun toString(): String {
        return Gson().toJson(this)
    }
}

class TorrentReq(
    action: String,
    val hash: String = "",
    val link: String = "",
    val title: String = "",
    val poster: String = "",
    val category: String = "",
    val data: String = "",
    val save_to_db: Boolean = false,
) : Request(action)

class ViewedReq(
    action: String,
    val hash: String = "",
    val file_index: Int = -1,
    val timecode: Double = 0.0 // playback position, seconds
) : Request(action)

data class Viewed(
    val hash: String,
    val file_index: Int,
    val timecode: Double = 0.0 // playback position, seconds
)