package ru.yourok.torrserve.server.local.services

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.atv.channels.ChannelProvider
import ru.yourok.torrserve.atv.channels.UpdaterCards
import ru.yourok.torrserve.server.api.Api

@TargetApi(Build.VERSION_CODES.O)
class HomeWatch : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == null || !Utils.isGoogleTV()) return

        when (action) {
            TvContractCompat.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED -> {
                val previewProgramId = intent.getLongExtra(TvContractCompat.EXTRA_PREVIEW_PROGRAM_ID, -1L)
                val hash = ChannelProvider(App.context.getString(R.string.torrents)).findProgramHashById(previewProgramId)
                if (hash.isNotBlank()) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            Api.remTorrent(hash)
                            // UpdaterCards.updateCards()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            TvContractCompat.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED -> {
                val watchNextInternalId = intent.getLongExtra(TvContractCompat.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "onReceive: ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED, $watchNextInternalId")
            }
        }
    }

    companion object {
        private const val TAG = "HomeWatch"
    }
}