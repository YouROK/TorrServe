package ru.yourok.torrserve.ui.dialogs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.app.Consts.PLAYERS_BLACKLIST
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Mime
import ru.yourok.torrserve.utils.TorrentHelper
import java.util.Locale

object PlayersDialog {
    fun show(context: Context, torrent: Torrent, index: Int, onSelect: (player: String) -> Unit) {
        val file = TorrentHelper.findFile(torrent, index) ?: throw Exception("file in torrent not found")
        val link = TorrentHelper.getTorrentPlayLink(torrent, index)
        val mime = Mime.getMimeType(file.path)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse(link), mime)

        val resInfo =
            App.context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        val filteredList: MutableList<ResolveInfo> = mutableListOf()
        for (info in resInfo) {
            if (!PLAYERS_BLACKLIST.contains(info.activityInfo.packageName.lowercase(Locale.getDefault()))) {
                filteredList.add(info)
            }
        }

        if (filteredList.isEmpty()) {
            App.toast(R.string.error_app_not_found, false)
            return
        }

        val listAdapter = AppListAdapter(context, filteredList)
        val builder = AlertDialog.Builder(context)
        val appTitleView = LayoutInflater.from(context).inflate(R.layout.app_list_title, null)
        val switch = appTitleView.findViewById<SwitchCompat>(R.id.useDefault)

        builder.setCustomTitle(appTitleView)
        builder.setAdapter(listAdapter) { dlg, which ->
            val setDefaultPlayer = switch.isChecked
            val selectedPlayer = listAdapter.getItemPackage(which)
            onSelect(selectedPlayer)
            if (setDefaultPlayer) Settings.setPlayer(selectedPlayer)
            dlg.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
        dialog.listView.requestFocus()
    }
}