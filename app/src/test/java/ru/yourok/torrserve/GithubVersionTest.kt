package ru.yourok.torrserve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yourok.torrserve.server.api.GithubReleases

/** Версии из тегов релизов: сравнение и выбор последнего клиентского релиза. */
class GithubVersionTest {

    @Test
    fun versionOfStripsPrefixAndClientSuffix() {
        assertEquals(listOf(2), GithubReleases.versionOf("Silo.2"))
        assertEquals(listOf(2, 1), GithubReleases.versionOf("Silo.2.1"))
        assertEquals(listOf(7), GithubReleases.versionOf("Silo.7.Client"))
        assertEquals(listOf(145), GithubReleases.versionOf("MatriX.145"))
        assertNull(GithubReleases.versionOf(""))
        assertNull(GithubReleases.versionOf("v1"))
    }

    @Test
    fun compareUsesNumbersNotLexicographicOrder() {
        assertTrue(GithubReleases.compare(listOf(10), listOf(9)) > 0)
        assertTrue(GithubReleases.compare(listOf(9), listOf(10)) < 0)
        assertEquals(0, GithubReleases.compare(listOf(2), listOf(2, 0)))
    }

    @Test
    fun clientTagsAreNotServerTags() {
        val client = GithubReleases.Release("Silo.3.Client", "", "", 0)
        val server = GithubReleases.Release("Silo.3", "", "", 0)
        val old = GithubReleases.Release("MatriX.151", "", "", 0)
        assertTrue(client.isClient)
        assertFalse(server.isClient)
        assertFalse(old.isClient)
    }

    @Test
    fun isNewerComparesTagsOnly() {
        assertTrue(GithubReleases.isNewer("Silo.2.Client", "Silo.1.Client"))
        assertFalse(GithubReleases.isNewer("Silo.1.Client", "Silo.1.Client"))
        assertFalse(GithubReleases.isNewer("Silo.1.Client", "Silo.2.Client"))
        // Пустая строка (версия не прочиталась) не считается обновлением
        assertFalse(GithubReleases.isNewer("Silo.1.Client", ""))
    }
}
