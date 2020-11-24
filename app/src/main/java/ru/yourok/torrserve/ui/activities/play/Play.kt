package ru.yourok.torrserve.ui.activities.play

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

object Play {
    fun PlayActivity.play(save: Boolean) {
        lifecycleScope.launch {
            torrentSave = save
            val torr = LoadTorrent.load(this@play)

        }
    }
}