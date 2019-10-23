package ru.yourok.torrserve.atv.channels

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.media.tv.TvContract.PreviewPrograms.ASPECT_RATIO_2_3
import android.media.tv.TvContract.PreviewPrograms.AVAILABILITY_AVAILABLE
import android.net.Uri
import android.support.media.tv.Channel
import android.support.media.tv.ChannelLogoUtils
import android.support.media.tv.PreviewProgram
import android.support.media.tv.TvContractCompat
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.atv.channels.providers.Torrent


class ChannelProvider(private val name: String) {

    fun create(): Long {
        var channelId = findChannel()
        if (channelId != -1L)
            return channelId

        val builder = Channel.Builder()
        builder.setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName(name)
                .setAppLinkIntentUri(Uri.parse("torrserve://${BuildConfig.APPLICATION_ID}/open_main_list"))

        val channelUri = App.getContext().contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                builder.build().toContentValues()
        )
        channelId = ContentUris.parseId(channelUri)
        val bitmap = BitmapFactory.decodeResource(App.getContext().resources, R.mipmap.ic_launcher)
        ChannelLogoUtils.storeChannelLogo(App.getContext(), channelId, bitmap)
        TvContractCompat.requestChannelBrowsable(App.getContext(), channelId)
        return channelId
    }

    fun update(list: List<Torrent>) {
        val channelId = create()
        App.getContext().contentResolver.delete(
                TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                null,
                null
        )

        val channel = Channel.Builder()
        channel.setDisplayName(name)
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setAppLinkIntentUri(Uri.parse("torrserve://${BuildConfig.APPLICATION_ID}/open_main_list"))
                .build()

        App.getContext().contentResolver.update(
                TvContractCompat.buildChannelUri(channelId),
                channel.build().toContentValues(), null, null
        )

        list.forEach {
            val prg = getProgram(channelId, it, list.size)
            App.getContext().contentResolver.insert(
                    Uri.parse("content://android.media.tv/preview_program"),
                    prg.toContentValues()
            )
        }
    }

    private fun getProgram(channelId: Long, torr: Torrent, size: Int): PreviewProgram {

        val info = mutableListOf<String>()
        var overview = ""
        var id = ""
        var vote_average = 0.0
        var runtime = 0
        var year = ""
        var poster = torr.poster

        torr.entity?.let { ent ->
            ent.vote_average?.let {
                if (it > 0.0) {
                    info.add("%.1f".format(it))
                    vote_average = it
                }
            }

            if (ent.media_type == "tv")
                ent.number_of_seasons?.let { info.add("S$it") }

            ent.genres?.joinToString(", ") { it.name.capitalize() }?.let {
                info.add(it)
            }

            ent.overview?.let { overview = it }
            ent.id?.let { id = it.toString() }
            ent.runtime?.let { runtime = it }
            ent.year?.let { year = it }
            ent.poster_path?.let { poster = it }
        }


        val preview = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setTitle(torr.name)
                .setAvailability(AVAILABILITY_AVAILABLE)
                .setDescription(overview)
                .setGenre(info.joinToString(" · "))
                .setReviewRating((vote_average.div(2)).toString())
                .setIntent(Utils.buildPendingIntent(torr))
                .setInternalProviderId(id)
                .setWeight(size)
                .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                .setDurationMillis(runtime.times(60000))
                .setReleaseDate(year)
                .setSearchable(true)
                .setLive(false)
                .setPosterArtUri(Uri.parse(poster))
                .setPosterArtAspectRatio(ASPECT_RATIO_2_3)

        return preview.build()
    }

    private val CHANNELS_PROJECTION = arrayOf(
            TvContractCompat.Channels._ID,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContractCompat.Channels.COLUMN_BROWSABLE
    )

    private fun findChannel(): Long {
        val cursor = App.getContext().contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                CHANNELS_PROJECTION,
                null,
                null,
                null
        )

        cursor?.let {
            if (it.moveToFirst())
                do {
                    val channel = Channel.fromCursor(it)
                    if (name.equals(channel.displayName)) {
                        cursor.close()
                        return channel.id
                    }
                } while (it.moveToNext())
            cursor.close()
        }
        return -1
    }
}