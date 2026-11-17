package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.JournalEntry
import com.example.data.model.JournalMood
import com.example.data.security.VaultPreferencesRepository
import com.example.data.social.ShareIntentParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches FolioNote`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FolioNote", appName)
    }

    @Test
    fun `share intent parser detects Instagram and YouTube links`() {
        val igIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Morning Inspiration")
            putExtra(Intent.EXTRA_TEXT, "Check this reel https://www.instagram.com/reel/ABC123/")
        }
        val igDraft = ShareIntentParser.parse(igIntent)
        assertNotNull(igDraft)
        assertEquals("Instagram", igDraft?.platform)
        assertEquals("https://www.instagram.com/reel/ABC123/", igDraft?.url)

        val ytIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://youtu.be/dQw4w9WgXcQ")
        }
        val ytDraft = ShareIntentParser.parse(ytIntent)
        assertNotNull(ytDraft)
        assertEquals("YouTube", ytDraft?.platform)
    }

    @Test
    fun `vault preferences stores salted hash and verifies PIN`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = VaultPreferencesRepository(context)
        repo.clearPin()
        assertFalse(repo.vaultSettings.first().hasPinConfigured)

        repo.setPin("2580", enableBiometric = true)
        val settings = repo.vaultSettings.first()
        assertTrue(settings.hasPinConfigured)
        assertTrue(settings.vaultLockEnabled)
        assertTrue(settings.biometricEnabled)

        assertTrue(repo.verifyPin("2580"))
        assertFalse(repo.verifyPin("0000"))

        repo.clearPin()
        assertFalse(repo.vaultSettings.first().hasPinConfigured)
    }

    @Test
    fun `journal entry toCreateMap omits id and null optional fields`() {
        val entry = JournalEntry(
            id = "should_not_appear",
            userId = "uid_1",
            title = "Quiet Focus",
            content = "Journaling without distractions.",
            mood = JournalMood.FOCUSED.name,
            tags = listOf("focus", "clarity"),
            pinned = true,
            sharedUrl = null,
            sharedPlatform = null,
            totalSocialMinutes = 24L
        )
        val payload = entry.toCreateMap("uid_1")
        assertFalse(payload.containsKey("id"))
        assertNull(payload["sharedUrl"])
        assertEquals("uid_1", payload["userId"])
        assertEquals("FOCUSED", payload["mood"])
        assertEquals(true, payload["pinned"])
        assertEquals(24L, payload["totalSocialMinutes"])
    }
}
