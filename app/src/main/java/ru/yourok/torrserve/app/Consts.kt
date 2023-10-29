package ru.yourok.torrserve.app

object Consts {
    private const val releaseHost = "https://releases.yourok.ru/torr"
    const val ad_link = "$releaseHost/ad"
    const val updateApkPath = "$releaseHost/apk_release.json"
    const val updateServerPath = "$releaseHost/server_release.json"
    val excludedApps = hashSetOf(
        "com.android.gallery3d",
        "com.android.tv.frameworkpackagestubs",
        "com.estrongs.android.pop",
        "com.ghisler.android.totalcommander",
        "com.google.android.apps.photos",
        "com.google.android.tv.frameworkpackagestubs",
        "com.instantbits.cast.webvideo",
        "com.lonelycatgames.xplore",
        "com.mixplorer.silver",
        "nextapp.fx",
        "pl.solidexplorer2"
    )
}