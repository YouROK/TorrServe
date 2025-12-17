package ru.yourok.torrserve.atv.channels

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.activities.main.MainActivity
import ru.yourok.torrserve.utils.Format
import java.nio.charset.Charset
import java.util.Locale


class ChannelProvider(private val iName: String, private val dName: String) {

    private fun create(): Long {
        var channelId = findChannel()
        if (channelId != -1L)
            return channelId

        val builder = Channel.Builder()
        builder.setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(dName)
            .setInternalProviderData(iName)
            .setAppLinkIntentUri(Uri.parse("torrserve://${BuildConfig.APPLICATION_ID}/open_main_list"))

        val channelUri = App.context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            builder.build().toContentValues()
        )
        channelId = channelUri?.let { ContentUris.parseId(it) } ?: 0
        val bitmap = BitmapFactory.decodeResource(App.context.resources, R.drawable.ts_round)
        ChannelLogoUtils.storeChannelLogo(App.context, channelId, bitmap)
        TvContractCompat.requestChannelBrowsable(App.context, channelId)
        return channelId
    }

    fun update(list: List<Torrent>) {
        val channelId = create()
        App.context.contentResolver.delete(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            null,
            null
        )
        val channel = Channel.Builder()
        channel.setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(dName)
            .setInternalProviderData(iName)
            .setAppLinkIntentUri(Uri.parse("torrserve://${BuildConfig.APPLICATION_ID}/open_main_list"))
            .build()

        App.context.contentResolver.update(
            TvContractCompat.buildChannelUri(channelId),
            channel.build().toContentValues(), null, null
        )

        if (list.isNotEmpty())
            list.forEachIndexed { index, torrent ->
                val prg = getProgram(channelId, torrent, list.size - index)
                App.context.contentResolver.insert(
                    Uri.parse("content://android.media.tv/preview_program"),
                    prg.toContentValues()
                )
            }
        else
            App.context.contentResolver.insert(
                Uri.parse("content://android.media.tv/preview_program"),
                emptyProgram(channelId).toContentValues()
            )

        //remove stale channels with null data
        list().filter { it.internalProviderDataByteArray == null }.forEach {
            rem(it)
        }

    }

    @SuppressLint("RestrictedApi")
    private val PROGRAMS_PROJECTION = arrayOf(
        TvContractCompat.PreviewPrograms._ID,
        TvContractCompat.PreviewPrograms.COLUMN_SHORT_DESCRIPTION
    )

    @SuppressLint("RestrictedApi")
    fun findProgramHashById(id: Long): String {
        val cursor = App.context.contentResolver.query(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            PROGRAMS_PROJECTION,
            null,
            null,
            null
        )
        cursor?.let {
            if (it.moveToFirst())
                do {
                    val program = PreviewProgram.fromCursor(it)
                    if (id == program.id) {
                        cursor.close()
                        return program.description
                    }
                } while (it.moveToNext())
            cursor.close()
        }
        return ""
    }

    @SuppressLint("RestrictedApi")
    private fun emptyProgram(channelId: Long): PreviewProgram {
        val vintent = Intent(App.context, MainActivity::class.java)
        vintent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val resourceId = R.drawable.emptyposter
        val ep = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(App.context.resources.getResourcePackageName(resourceId))
            .appendPath(App.context.resources.getResourceTypeName(resourceId))
            .appendPath(App.context.resources.getResourceEntryName(resourceId))
            .build()
        val preview = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setTitle(App.context.getString(R.string.app_name))
            .setAvailability(TvContractCompat.PreviewProgramColumns.AVAILABILITY_FREE)
            .setDescription(App.context.getString(R.string.open_torrserve))
            .setReviewRating("5")
            .setIntent(vintent)
            .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setSearchable(true)
            .setLive(false)
            .setPosterArtUri(ep)
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)

        return preview.build()
    }

    @SuppressLint("RestrictedApi")
    private fun getProgram(channelId: Long, torr: Torrent, size: Int): PreviewProgram {
        val info = mutableListOf<String>()
        var posterUri = Uri.parse(torr.poster)
        if (posterUri.toString().isEmpty()) {
            val resourceId = R.drawable.emptyposter
            posterUri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(App.context.resources.getResourcePackageName(resourceId))
                .appendPath(App.context.resources.getResourceTypeName(resourceId))
                .appendPath(App.context.resources.getResourceEntryName(resourceId))
                .build()
        }
        val type = if (torr.category.equals("tv", true)) TvContractCompat.PreviewPrograms.TYPE_TV_SERIES else TvContractCompat.PreviewPrograms.TYPE_MOVIE
        val preview = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setTitle(torr.title)
            .setAvailability(TvContractCompat.PreviewProgramColumns.AVAILABILITY_AVAILABLE)
            .setGenre(info.joinToString(" · "))
            .setIntent(Utils.buildPendingIntent(torr))
            .setWeight(size)
            .setType(type)
            .setSearchable(true)
            .setLive(false)
            .setPosterArtUri(posterUri)
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
            .setDescription(buildDescription(torr))

        return preview.build()
    }

    private val CHANNELS_PROJECTION = arrayOf(
        TvContractCompat.Channels._ID,
        TvContractCompat.Channels.COLUMN_DISPLAY_NAME,
        TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
        TvContractCompat.Channels.COLUMN_BROWSABLE
    )

    private fun findChannel(): Long {
        val cursor = App.context.contentResolver.query(
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
                    if (iName == channel.internalProviderDataByteArray?.toString(Charset.defaultCharset())) {
                        cursor.close()
                        return channel.id
                    }
                } while (it.moveToNext())
            cursor.close()
        }
        return -1
    }

    private fun list(): List<Channel> {
        val chnl = mutableListOf<Channel>()

        val cursor = App.context.contentResolver.query(
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
                    chnl.add(channel)
                } while (it.moveToNext())
            cursor.close()
        }
        return chnl
    }

    private fun rem(ch: Channel) {
        App.context.contentResolver.delete(TvContractCompat.buildChannelUri(ch.id), null, null)
    }

    private fun buildDescription(torr: Torrent): String {
        var retStr = ""
        if (torr.title.isNotBlank())
            retStr = torr.title
        else if (torr.name.isNotBlank())
            retStr = torr.name
        else
            retStr = torr.hash.uppercase(Locale.getDefault())

        if (torr.torrent_size > 0)
            retStr += " • ${Format.byteFmt(torr.torrent_size)}"

        return retStr
    }
}
