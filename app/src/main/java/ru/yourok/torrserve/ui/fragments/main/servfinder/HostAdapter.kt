package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.CImageSpan
import ru.yourok.torrserve.utils.Format
import ru.yourok.torrserve.utils.SpanFormat
import ru.yourok.torrserve.utils.ThemeUtil

class HostAdapter : RecyclerView.Adapter<HostAdapter.ViewHolder>() {
    val hosts = mutableListOf<ServerIp>()

    var onClick: ((String) -> Unit)? = null

    fun insert(servIp: ServerIp) {
        try {
            if (hosts.find { it.host == servIp.host } == null) {
                hosts.add(0, servIp)
                notifyItemInserted(0)
            }
        } catch (_: Exception) {
        }
    }

    fun add(servIp: ServerIp) {
        try {
            if (hosts.find { it.host == servIp.host } == null) {
                hosts.add(servIp)
                notifyItemInserted(hosts.size - 1)
            }
        } catch (_: Exception) {
        }
    }

    fun clear() {
        try {
            hosts.clear()
            notifyDataSetChanged()
        } catch (_: Exception) {
        }
    }


    class ViewHolder(val view: View, private val adapter: HostAdapter) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener {
                adapter.onClick?.invoke(adapter.hosts[adapterPosition].host)
            }
            view.setOnLongClickListener {
                val lst = Settings.getHosts().toMutableList()
                if (lst.remove(adapter.hosts[adapterPosition].host)) {
                    Settings.setHosts(lst)
                    delete(adapter, adapter.hosts[adapterPosition])
                }
                true
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val vi = LayoutInflater.from(parent.context).inflate(R.layout.host_item, parent, false)
        return ViewHolder(vi, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // round labels model
        val radius = Format.dp2px(2.0f).toFloat()
        val shapeAppearanceModel = ShapeAppearanceModel()
            .toBuilder()
            .setAllCorners(CornerFamily.ROUNDED, radius)
            .build()
        val hostColor = ColorStateList.valueOf(ThemeUtil.getColorFromAttr(holder.view.context, R.attr.colorHost))
        val versionColor = ColorStateList.valueOf(ThemeUtil.getColorFromAttr(holder.view.context, R.attr.colorPrimary))
        val labelsTextColor = ThemeUtil.getColorFromAttr(holder.view.context, R.attr.colorSurface)
        val hostView = holder.view.findViewById<TextView>(R.id.tvHost)

        hostView.apply {
            text = if (hosts[position].host.startsWith("https", true)) { // show https badge
                val sIcon = SpannableString(" ")
                AppCompatResources.getDrawable(holder.view.context, R.drawable.ssl)?.let { icon ->
                    icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                    sIcon.setSpan(CImageSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                SpanFormat.format("${hosts[position].host.removePrefix("https://")}  %s", sIcon)
            } else hosts[position].host.removePrefix("http://")
            val shapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
            shapeDrawable.fillColor = hostColor.withAlpha(10)
            shapeDrawable.setStroke(2.0f, hostColor.withAlpha(240))
            background = shapeDrawable
            setTextColor(hostColor)
        }

        val version = hosts[position].version
        val onlineView = holder.view.findViewById<TextView>(R.id.tvOnline)
        val onlineColor = AppCompatResources.getColorStateList(holder.view.context, R.color.green)
        // set online and dim by added version
        if (version.isNotBlank() && (version.startsWith("1.2.") || version.startsWith("MatriX"))) {
            onlineView?.apply {
                visibility = View.VISIBLE
                hostView.alpha = 1.0f
                val shapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
                shapeDrawable.fillColor = onlineColor.withAlpha(10)
                shapeDrawable.setStroke(2.0f, onlineColor)
                background = shapeDrawable
                setTextColor(onlineColor)
                text = holder.view.context.getString(R.string.online).lowercase()
            }
        } else {
            onlineView?.visibility = View.INVISIBLE
            hostView.alpha = 0.6f
        }
        val status = hosts[position].status
        holder.view.findViewById<TextView>(R.id.tvStatus).apply {
            if (status.isNotBlank()) {
                text = status
            } else {
                visibility = View.GONE
            }
        }

        holder.view.findViewById<TextView>(R.id.tvVersion).apply {
            if (version.isNotBlank()) {
                text = version
                val shapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
                shapeDrawable.fillColor = versionColor.withAlpha(160)
                shapeDrawable.setStroke(2.0f, versionColor.withAlpha(100))
                background = shapeDrawable
                setTextColor(labelsTextColor)
            } else {
                visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = hosts.size

    companion object {
        fun delete(hostAdapter: HostAdapter, servIp: ServerIp) {
            try {
                val pos = hostAdapter.hosts.indexOf(servIp)
                if (pos != -1) {
                    hostAdapter.hosts.removeAt(pos)
                    hostAdapter.notifyItemRemoved(pos)
                }
            } catch (_: Exception) {
            }
        }
    }

}