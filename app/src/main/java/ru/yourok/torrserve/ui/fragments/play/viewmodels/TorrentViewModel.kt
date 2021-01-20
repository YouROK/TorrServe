package ru.yourok.torrserve.ui.fragments.play.viewmodels

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent

class TorrentViewModel : ViewModel() {
    private val retdata: MutableLiveData<Torrent> = MutableLiveData()

    fun loadTorrent(link: String, hash: String, title: String, poster: String, data: String, save: Boolean): LiveData<Torrent>? {
        if (hash.isNotEmpty())
            loadHash(hash)
        else if (link.isNotEmpty())
            loadLink(link, title, poster, data, save)
        else return null
        return retdata
    }

    private fun loadHash(hash: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val torr = Api.getTorrent(hash)
            withContext(Dispatchers.Main) {
                retdata.value = torr
            }
        }
    }

    private fun loadLink(link: String, title: String, poster: String, data: String, save: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scheme = Uri.parse(link).scheme
                val torr = if (ContentResolver.SCHEME_ANDROID_RESOURCE == scheme || ContentResolver.SCHEME_FILE == scheme) {
                    uploadFile(link, title, poster, data, save)
                    throw Exception("not released")
                } else
                    Api.addTorrent(link, title, poster, data, save)
                withContext(Dispatchers.Main) {
                    retdata.value = torr
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    //TODO message
                    App.Toast(e.message ?: return@withContext)
                }
            }
        }
    }

    private fun uploadFile(link: String, title: String, poster: String, data: String, save: Boolean) {
        val fis = App.context.contentResolver.openInputStream(Uri.parse(link))
        Api.uploadTorrent(fis, title, poster, data, save)
    }
}