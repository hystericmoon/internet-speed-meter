package com.example.speedmeter.update

import com.example.speedmeter.BuildConfig
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric for a real org.json; the plain android.jar stubs throw.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UpdateInfoTest {

    private fun manifest(versionCode: Int) = """
        {
          "versionCode": $versionCode,
          "versionName": "0.1.$versionCode",
          "commit": "a1b2c3d",
          "apkUrl": "https://example.com/app.apk",
          "sha256": "ABCDEF0123"
        }
    """

    @Test
    fun `parses the manifest CI writes`() {
        val info = UpdateInfo.parse(manifest(42))
        assertEquals(42, info.versionCode)
        assertEquals("0.1.42", info.versionName)
        assertEquals("a1b2c3d", info.commit)
        assertEquals("https://example.com/app.apk", info.apkUrl)
        assertEquals("abcdef0123", info.sha256) // lowercased to match our digest output
    }

    @Test
    fun `only a higher versionCode counts as newer`() {
        // Relative to the build under test: CI stamps the run number into VERSION_CODE.
        assertTrue(UpdateInfo.parse(manifest(BuildConfig.VERSION_CODE + 1)).isNewer)
        assertFalse(UpdateInfo.parse(manifest(BuildConfig.VERSION_CODE)).isNewer)
        assertFalse(UpdateInfo.parse(manifest(BuildConfig.VERSION_CODE - 1)).isNewer)
    }

    @Test(expected = JSONException::class)
    fun `missing apkUrl is rejected`() {
        UpdateInfo.parse("""{"versionCode": 2, "versionName": "x", "sha256": "y"}""")
    }
}
