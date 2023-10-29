package ru.yourok.torrserve.ui.dialogs

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import ru.yourok.torrserve.R
import java.util.Locale


class AppListAdapter internal constructor(context: Context, private val appsInfo: List<ResolveInfo>) : BaseAdapter() {
    private val mLayoutInflater: LayoutInflater = LayoutInflater.from(context)
    private val pm: PackageManager = context.packageManager

    override fun getCount(): Int {
        return appsInfo.size
    }

    override fun getItem(position: Int): ResolveInfo {
        return appsInfo[position]
    }

    fun getItemPackage(position: Int): String {
        return getItem(position).activityInfo.packageName.lowercase(Locale.getDefault())
    }

    private fun getItemLabel(position: Int): String {
        var loadLabel = getItem(position).loadLabel(pm)
        if (loadLabel.isNullOrEmpty()) {
            loadLabel = getItemPackage(position)
        }
        return loadLabel.toString()
    }

    private fun getItemIcon(position: Int): Drawable {
        return getItem(position).loadIcon(pm)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, cv: View?, parent: ViewGroup?): View {
        val view = cv ?: mLayoutInflater.inflate(R.layout.app_list_item, null)
        val image = view?.findViewById<ImageView>(R.id.imageViewIcon)
        val textViewMain = view?.findViewById<TextView>(R.id.textViewMain)
        val textViewSecond = view?.findViewById<TextView>(R.id.textViewSecond)
        image?.setImageDrawable(getItemIcon(position))
        textViewMain?.text = getItemLabel(position)
        textViewSecond?.text = getItemPackage(position)
        return view!!
    }
}