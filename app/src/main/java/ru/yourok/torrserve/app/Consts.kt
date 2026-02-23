package ru.yourok.torrserve.app

object Consts {
    private const val REL_HOST = "https://releases.yourok.ru/torr"
    const val AD_LINK = "$REL_HOST/ad"
    const val UPDATE_APK_PATH = "$REL_HOST/apk_release.json"
    const val UPDATE_SERVER_PATH = "$REL_HOST/server_release.json"
    val PLAYERS_BLACKLIST = hashSetOf(
        "com.android.gallery3d",
        "com.android.tv.frameworkpackagestubs",
        "com.estrongs.android.pop",
        "com.estrongs.android.pop.pro",
        "com.ghisler.android.totalcommander",
        "com.google.android.apps.photos",
        "com.google.android.tv.frameworkpackagestubs",
        "com.instantbits.cast.webvideo",
        "com.lonelycatgames.xplore",
        "com.mitv.videoplayer",
        "com.mixplorer.silver",
        "com.opera.browser",
        "com.tcl.browser",
        "com.tcl.ui_mediacenter",
        "nextapp.fx",
        "org.droidtv.contentexplorer",
        "pl.solidexplorer2",
        // more to add...
    )
}