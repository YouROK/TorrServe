package ru.yourok.torrserve.ui.fragments.play

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.server.models.ffp.Format
import ru.yourok.torrserve.server.models.torrent.FileStat
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.play.PlayActivity
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.ui.fragments.play.viewmodels.InfoTorrent
import ru.yourok.torrserve.ui.fragments.play.viewmodels.InfoViewModel
import ru.yourok.torrserve.utils.ByteFmt
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File
import java.util.*

open class InfoFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.info_fragment, container, false)
        lifecycleScope.launch {
            (activity as? PlayActivity)?.showProgress()
            vi.findViewById<TextView>(R.id.tvTitle).setText(R.string.loading_torrent)
        }
        TorrService.start()
        return vi
    }

    suspend fun startInfo(hash: String) = withContext(Dispatchers.Main) {
        try {
            viewModel = ViewModelProvider(this@InfoFragment)[InfoViewModel::class.java]
            if (isActive) {
                val data = (viewModel as InfoViewModel).setTorrent(hash)
                data.observe(this@InfoFragment) {
                    updateUI(it, (requireActivity() as PlayActivity).torrentFileIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var poster = " "
    private var format: Format? = null

    // textView.text = "" // Remove old text
    // textView.append("Red Text", Color.RED)
    // textView.append("Blue Bold Text", Color.BLUE, true)
    private fun TextView.append(string: String?, @ColorInt color: Int = 0, bold: Boolean = false) {
        if (string == null || string.isEmpty()) {
            return
        }
        val spannable: Spannable = SpannableString(string)
        if (color != 0)
            spannable.setSpan(
                ForegroundColorSpan(color),
                0,
                spannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        if (bold)
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                spannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        append(spannable)
    }

    @ColorInt
    fun getColorFromAttr(
        context: Context,
        @AttrRes attrColor: Int,
        typedValue: TypedValue = TypedValue(),
        resolveRefs: Boolean = true
    ): Int {
        context.theme.resolveAttribute(attrColor, typedValue, resolveRefs)
        return typedValue.data
    }

    private fun updateUI(info: InfoTorrent, index: Int) {
        lifecycleScope.launch {
            if (info.torrent == null && info.error.isNotEmpty()) {
                view?.findViewById<TextView>(R.id.tvInfo)?.text = info.error
                return@launch
            }
            info.torrent?.let { torr ->
                view?.apply {
                    if (poster != torr.poster) {
                        poster = torr.poster
                        if (poster.isNotEmpty() && Settings.showCover())
                            findViewById<ImageView>(R.id.ivPoster)?.let {
                                it.visibility = View.VISIBLE
                                Glide.with(this)
                                    .asBitmap()
                                    .load(poster)
                                    .centerCrop()
                                    //.placeholder(ColorDrawable(0x3c000000))
                                    .apply(RequestOptions.bitmapTransform(RoundedCorners(6)))
                                    .transition(BitmapTransitionOptions.withCrossFade())
                                    .into(it)
                            }
                        else
                            findViewById<ImageView>(R.id.ivPoster)?.visibility = View.GONE
                    }

                    val title = torr.title
                    if (title.isEmpty())
                        findViewById<TextView>(R.id.tvTitle).visibility = View.GONE
                    else {
                        (activity as? PlayActivity)?.hideTitle()
                        findViewById<TextView>(R.id.tvTitle).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tvTitle).text = title
                    }

                    format?.let {
                        findViewById<TextView>(R.id.tvBitrate).text = it.bit_rate
                        findViewById<TextView>(R.id.tvBitrate).visibility = View.VISIBLE
                    } ?: let {
                        findViewById<TextView>(R.id.tvBitrate).visibility = View.GONE
                    }

                    val file: FileStat? = if (index >= 0) torr.file_stats?.get(index) else null
                    val color1 = 0 // ColorUtils.setAlphaComponent(getColorFromAttr(this.context, R.attr.colorOnBackground), 200)
                    val color2 = ColorUtils.setAlphaComponent(getColorFromAttr(this.context, R.attr.colorOnBackground), 220)
                    val tvFN = findViewById<TextView>(R.id.tvFileName)
                    val tvFS = findViewById<TextView>(R.id.tvFileSize)
                    file?.let {
                        var name = it.path
                        if (name.isNotEmpty())
                            name = File(name).name

                        tvFN.visibility = View.VISIBLE
                        tvFS.visibility = View.VISIBLE

                        tvFN.apply {
                            text = "" // name
                            append("$name", color2)
                        }

                        val size = it.length
                        if (size >= 0) {
                            // spannable
                            tvFS.apply {
                                text = "" // txt
                                append("${getString(R.string.size)} ", color1, true)
                                append("${ByteFmt.byteFmt(size)}", color2, true)
                            }
                        }
                    } ?: let {
                        tvFN.visibility = View.GONE
                        tvFS.visibility = View.GONE
                    }

                    var buffer = ""
                    var prc = 0.0
                    if (torr.preload_size > 0 && torr.preloaded_bytes > 0) {
                        prc = torr.preloaded_bytes.toDouble() * 100.0 / torr.preload_size.toDouble()
                        if (prc < 100.0)
                            buffer = "%.1f".format(prc) + "% "
                        buffer += ByteFmt.byteFmt(torr.preloaded_bytes)
                        if (prc < 100.0)
                            buffer += "/" + ByteFmt.byteFmt(torr.preload_size)
                    }

                    if (buffer.isNotEmpty()) {
                        // spannable
                        findViewById<TextView>(R.id.tvBuffer).apply {
                            text = "" // txt
                            append("${getString(R.string.buffer)} ", color1, true)
                            append("$buffer", color2, true)
                        }
                    }

                    if (torr.stat < TorrentHelper.TorrentSTWorking) {
                        if (prc > 0 && prc < 100)
                            (activity as? PlayActivity)?.showProgress(prc.toInt())
                        else
                            (activity as? PlayActivity)?.showProgress()
                    } else
                        (activity as? PlayActivity)?.hideProgress()

                    val peers = "${torr.connected_seeders} · ${torr.active_peers}/${torr.total_peers}"
                    if (peers.isNotEmpty()) {
                        // spannable
                        findViewById<TextView>(R.id.tvPeers).apply {
                            text = "" // txt
                            append("${getString(R.string.peers)} ", color1, true)
                            append("$peers", color2, true)
                        }
                    }

                    //val speed = ByteFmt.byteFmt(torr.download_speed) + getString(R.string.fmt_s)
                    val speed = ByteFmt.speedFmt(torr.download_speed)
                    if (speed.isNotEmpty() && torr.download_speed > 50.0) {
                        // spannable
                        findViewById<TextView>(R.id.tvSpeed).apply {
                            text = "" // txt
                            append("${getString(R.string.download_speed)} ", color1, true)
                            append("$speed", color2, true)
                        }
                    }

                    torr.bit_rate?.let { br ->
                        if (br.isNotBlank()) {
                            val bitRate = ByteFmt.speedFmt(br.toDouble())
                            findViewById<TextView>(R.id.tvBitrate).apply {
                                text = "" // txt
                                append("${getString(R.string.bit_rate)} ", color1, true)
                                append("$bitRate", color2, true)
                            }
                        }
                    }

                    view?.findViewById<TextView>(R.id.tvInfo)?.apply {
                        text = when (torr.stat_string.lowercase(Locale.getDefault())) {
                            "torrent added" -> getString(R.string.stat_string_added)
                            "torrent getting info" -> getString(R.string.stat_string_info)
                            "torrent preload" -> getString(R.string.stat_string_preload)
                            "torrent working" -> getString(R.string.stat_string_working)
                            "torrent closed" -> getString(R.string.stat_string_closed)
                            "torrent in db" -> getString(R.string.stat_string_in_db)
                            else -> torr.stat_string
                        }
                    }

                }
            }
        }
    }
}