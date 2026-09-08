package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.ImageViewCompat
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
import java.util.WeakHashMap

class HostAdapter : RecyclerView.Adapter<HostAdapter.ViewHolder>() {
    val hosts = mutableListOf<ServerIp>()
    private val feedbackAnimations = WeakHashMap<View, AnimatorSet>()

    var onClick: ((ServerIp) -> Unit)? = null

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
            val index = hosts.indexOfFirst { it.host == servIp.host }
            if (index == -1) {
                hosts.add(servIp)
                notifyItemInserted(hosts.size - 1)
            } else if (servIp.isMdns && !hosts[index].isMdns) {
                // A subnet scan can find the same endpoint first. Retain the mDNS source,
                // credentials, and indicator when its resolution arrives afterwards.
                hosts[index] = servIp
                notifyItemChanged(index)
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
                adapter.onClick?.invoke(adapter.hosts[adapterPosition])
            }
            view.setOnLongClickListener {
                val server = adapter.hosts[adapterPosition]
                if (server.isMdns) {
                    adapter.showDiscoveredFeedback(view)
                    return@setOnLongClickListener true
                }
                val lst = Settings.getHosts().toMutableList()
                if (lst.remove(server.host)) {
                    Settings.setHosts(lst)
                    delete(adapter, server)
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
        feedbackAnimations.remove(holder.view)?.cancel()
        holder.view.translationX = 0f
        holder.view.findViewById<ImageView>(R.id.ivMdns).apply {
            translationX = 0f
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(ThemeUtil.getColorFromAttr(context, R.attr.colorHost)))
        }
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
        holder.view.findViewById<ImageView>(R.id.ivMdns).visibility =
            if (hosts[position].isMdns) View.VISIBLE else View.GONE

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

    private fun showDiscoveredFeedback(view: View) {
        feedbackAnimations.remove(view)?.cancel()
        val icon = view.findViewById<ImageView>(R.id.ivMdns)
        icon.translationX = 0f
        val defaultColor = ThemeUtil.getColorFromAttr(view.context, R.attr.colorHost)
        val shake = ObjectAnimator.ofFloat(icon, View.TRANSLATION_X, 0f, Format.dp2px(5.0f).toFloat()).apply {
            duration = 100
            repeatCount = 9
            repeatMode = ObjectAnimator.REVERSE
        }
        val color = ValueAnimator.ofObject(ArgbEvaluator(), defaultColor, Color.RED, Color.RED, defaultColor).apply {
            duration = 1_000
            addUpdateListener {
                ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(it.animatedValue as Int))
            }
        }
        AnimatorSet().apply {
            playTogether(shake, color)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    icon.translationX = 0f
                    ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(defaultColor))
                    feedbackAnimations.remove(view)
                }
            })
            feedbackAnimations[view] = this
            start()
        }
    }

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
