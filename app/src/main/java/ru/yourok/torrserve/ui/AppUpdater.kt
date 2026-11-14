package ru.yourok.torrserve.ui

import ru.yourok.torrserve.server.api.GithubReleases

/**
 * Обновление самого приложения через GitHub Releases.
 *
 * Клиентские релизы помечаются тегом `Silo.N.Client` (например `Silo.1.Client`),
 * в релизе лежит файл `TorrServe_Silo.N.Client.apk`. Серверные теги `Silo.N`
 * сюда не попадают.
 */
object AppUpdater {

    /** Последний релиз клиента или null, если клиентских релизов ещё нет. */
    fun latest(): GithubReleases.Release? =
        GithubReleases.all()
            .filter { it.isClient }
            .maxWithOrNull { a, b ->
                GithubReleases.compare(
                    GithubReleases.versionOf(a.tag) ?: emptyList(),
                    GithubReleases.versionOf(b.tag) ?: emptyList(),
                )
            }

    /** true, если тег новее текущей версии приложения. */
    fun isNewer(tag: String, currentVersion: String): Boolean =
        GithubReleases.isNewer(tag, currentVersion)
}
