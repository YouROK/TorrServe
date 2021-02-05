package ru.yourok.torrserve.server.local.services

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.os.Build
import android.util.Log
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.atv.channels.UpdaterCards

@TargetApi(Build.VERSION_CODES.O)
class HomeWatch : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == null || !Utils.isGoogleTV()) return
//        if (BuildConfig.DEBUG)
//            Log.d(TAG, "onReceive: $action" + " intent: " + intent.toUri(0))

        when (action) {
            TvContract.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED -> {
                val previewProgramId = intent.getLongExtra(TvContract.EXTRA_PREVIEW_PROGRAM_ID, -1L)
                val previewInternalId = intent.getLongExtra(TvContract.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "onReceive: ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED, $previewProgramId, $previewInternalId")
                UpdaterCards.updateCards()
            }
            TvContract.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED -> {
                val watchNextProgramId = intent.getLongExtra(TvContract.EXTRA_PREVIEW_PROGRAM_ID, -1L)
                val watchNextInternalId = intent.getLongExtra(TvContract.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "onReceive: ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED, $watchNextProgramId, $watchNextInternalId")
            }
        }
    }

    companion object {
        private const val TAG = "HomeWatch"
    }
}