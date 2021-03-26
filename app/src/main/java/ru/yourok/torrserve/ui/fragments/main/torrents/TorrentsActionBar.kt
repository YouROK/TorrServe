package ru.yourok.torrserve.ui.fragments.main.torrents

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.AbsListView
import android.widget.AbsListView.MultiChoiceModeListener
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.channels.UpdaterCards
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.TorrentHelper.getTorrentMagnet
import java.lang.Thread.sleep
import kotlin.concurrent.thread


class TorrentsActionBar(private val listView: AbsListView) : MultiChoiceModeListener {

    override fun onItemCheckedStateChanged(actionMode: ActionMode, i: Int, l: Long, b: Boolean) {
        val selectedCount = listView.checkedItemCount
        when (selectedCount) {
            0 -> actionMode.setSubtitle(null)
            else -> actionMode.setTitle(selectedCount.toString())
        }
    }

    override fun onCreateActionMode(actionMode: ActionMode, menu: Menu?): Boolean {
        val inflater: MenuInflater = actionMode.getMenuInflater()
        inflater.inflate(R.menu.torrents_action_menu, menu)
        return true
    }

    override fun onPrepareActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    override fun onActionItemClicked(actionMode: ActionMode, item: MenuItem): Boolean {
        val selected = selectedItems
        when (item.itemId) {
            R.id.itemShareMagnet -> {
                val msg = selected.map { getTorrentMagnet(it) }.joinToString("\n\n")
                if (msg.isNotEmpty()) {
                    val share = Intent(Intent.ACTION_SEND)
                    share.setType("text/plain")
                    share.putExtra(Intent.EXTRA_TEXT, msg)
                    share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val intent = Intent.createChooser(share, "")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    App.context.startActivity(intent)
                }
            }
            R.id.itemCopyMagnet -> {
                val msg = selected.map { getTorrentMagnet(it) }.joinToString("\n\n")
                if (msg.isNotEmpty()) {
                    val clipboard = App.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("magnets", msg)
                    clipboard.primaryClip = clip
                    App.Toast(App.context.getString(R.string.copy_to_clipboard))
                }
            }
            R.id.itemRemoveTorrent -> {
                thread {
                    selected.forEach {
                        try {
                            Api.remTorrent(it.hash)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    sleep(1000)
                    UpdaterCards.updateCards()
                }
            }
            R.id.itemRemoveViewed -> {
                thread {
                    selected.forEach {
                        try {
                            Api.remViewed(it.hash)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        actionMode.finish()
        return false
    }

    override fun onDestroyActionMode(actionMode: ActionMode?) {
    }

    private val selectedItems: List<Torrent>
        get() {
            val selectedFiles: MutableList<Torrent> = ArrayList()
            val sparseBooleanArray = listView.checkedItemPositions
            for (i in 0 until sparseBooleanArray.size()) {
                if (sparseBooleanArray.valueAt(i)) {
                    val selectedItem = listView.getItemAtPosition(sparseBooleanArray.keyAt(i)) as Torrent
                    selectedFiles.add(selectedItem)
                }
            }
            return selectedFiles
        }

}