package ru.yourok.torrserve.ui.fragments.play

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.ui.fragments.TSFragment

class TorrentFilesFragment : TSFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.torrent_file_fragment, container, false)
        TorrService.start()
        return vi
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        view?.apply {
            findViewById<Button>(R.id.btnPlaylist).setOnClickListener { }
            findViewById<Button>(R.id.btnPlaylistContinue).setOnClickListener { }

            findViewById<ListView>(R.id.lvTorrentFiles).apply {

            }
        }
    }
}