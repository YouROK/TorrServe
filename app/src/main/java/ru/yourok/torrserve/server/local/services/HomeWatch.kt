package ru.yourok.torrserve.server.local.services

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
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
//        if (BuildConfig.DEBUG)
//            Log.d(TAG, "onReceive: $action" + " intent: " + intent.toUri(0))

        when (action) {
            TvContractCompat.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED -> {
                val previewProgramId = intent.getLongExtra(TvContractCompat.EXTRA_PREVIEW_PROGRAM_ID, -1L)
                val previewInternalId = intent.getLongExtra(TvContractCompat.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "onReceive: ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED, $previewProgramId, $previewInternalId")
                val hash = ChannelProvider(App.context.getString(R.string.torrents)).findProgramHashById(previewProgramId)
                if (hash.isNotBlank()) {
                    if (BuildConfig.DEBUG)
                        Log.d(TAG, "remove hash: " + hash)
                    Api.remTorrent(hash)
                }
                UpdaterCards.updateCards()
            }
            TvContractCompat.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED -> {
                val watchNextProgramId = intent.getLongExtra(TvContractCompat.EXTRA_PREVIEW_PROGRAM_ID, -1L)
                val watchNextInternalId = intent.getLongExtra(TvContractCompat.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "onReceive: ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED, $watchNextProgramId, $watchNextInternalId")
            }
        }
    }

    companion object {
        private const val TAG = "HomeWatch"
    }
}