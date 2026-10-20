package com.andsi.airlyrics.settings.store

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.content.Context

@RunWith(RobolectricTestRunner::class)
class AetherLyricServerStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("aetherlyric_server_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun testDefaultValues() {
        assertEquals("http://192.168.1.100:8000", AetherLyricServerStore.getServerUrl(context))
        assertFalse(AetherLyricServerStore.isServerEnabled(context))
        assertFalse(AetherLyricServerStore.isConnectionVerified(context))
        assertEquals("", AetherLyricServerStore.getAdminApiKey(context))
    }

    @Test
    fun testServerUrlPersistenceAndNormalization() {
        AetherLyricServerStore.setServerUrl(context, "http://192.168.1.100:8000/")
        assertEquals("http://192.168.1.100:8000", AetherLyricServerStore.getServerUrl(context))

        // 標記驗證成功後，若修改 URL 需自動重設驗證狀態
        AetherLyricServerStore.setConnectionVerified(context, true)
        assertTrue(AetherLyricServerStore.isConnectionVerified(context))

        AetherLyricServerStore.setServerUrl(context, "http://10.0.0.5:8000")
        assertFalse(AetherLyricServerStore.isConnectionVerified(context))
    }

    @Test
    fun testServerEnabledRequiresNonEmptyUrl() {
        // 設定有效 URL 後開啟
        AetherLyricServerStore.setServerUrl(context, "http://192.168.1.50:8000")
        AetherLyricServerStore.setServerEnabled(context, true)
        assertTrue(AetherLyricServerStore.isServerEnabled(context))

        // 清空 URL 後自動關閉
        AetherLyricServerStore.setServerUrl(context, "")
        assertFalse(AetherLyricServerStore.isServerEnabled(context))
    }

    @Test
    fun testMultiProfilesManagement() {
        val initialProfiles = AetherLyricServerStore.getProfiles(context)
        assertTrue(initialProfiles.isNotEmpty())

        val added = AetherLyricServerStore.addProfile(context, "家庭 NAS", "http://192.168.1.200:8000/")
        assertEquals("家庭 NAS", added.name)
        assertEquals("http://192.168.1.200:8000", added.url)

        AetherLyricServerStore.setActiveProfileId(context, added.id)
        assertEquals("http://192.168.1.200:8000", AetherLyricServerStore.getServerUrl(context))

        AetherLyricServerStore.setLastStatusMessage(context, "連線正常：伺服器在線")
        assertEquals("連線正常：伺服器在線", AetherLyricServerStore.getLastStatusMessage(context))
    }
}
