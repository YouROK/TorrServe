package ru.yourok.torrserve.utils

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import ru.yourok.torrserve.app.App


class Player(var Name: String, var Package: String) {
    override fun toString(): String {
        return Name
    }
}

object Players {
    fun getList(): MutableList<Player> {
        val list = mutableListOf<Player>()
        list.addAll(getList("video/*"))
        list.addAll(getList("audio/*"))
        list.addAll(getFixedList())
        return list.distinctBy { it.Package }.sortedBy { it.Name }.toMutableList()
    }

    private fun getList(mime: String): List<Player> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost/torrent/file.avi"))
        intent.type = mime
        val apps = App.getContext().getPackageManager().queryIntentActivities(intent, 0)
        val list = mutableListOf<Player>()
        for (a in apps) {
            var name = a.loadLabel(App.getContext().packageManager)?.toString() ?: a.activityInfo.packageName
            list.add(Player(name, a.activityInfo.packageName))
        }
        return list
    }

    private fun getFixedList(): List<Player> {
        val pm = App.getContext().getPackageManager()
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val filtred = packages.filter {
            it.packageName == "org.xbmc.kodi" ||
                    it.packageName == "com.android.gallery3d" ||
                    it.packageName == "com.niklabs.pp" || // prefect player
                    it.packageName == "com.lonelycoder.mediaplayer" || //movian
                    it.packageName == "com.semperpax.spmc" || //spmc
                    it.packageName == "com.newin.nplayer.pro" || // nPlayer
                    it.packageName == "com.zidoo.zdmc" || // zdmc
                    it.packageName == "net.gtvbox.videoplayer" || //vimu
                    it.packageName.startsWith("tv.mrmc.mrmc") //mrmc
        }

        val list = mutableListOf<Player>()
        filtred.forEach {
            var name = it.name ?: it.loadLabel(App.getContext().packageManager)?.toString() ?: it.packageName
            var pkg = it.packageName
            list.add(Player(name, pkg))
        }
        return list
    }
}