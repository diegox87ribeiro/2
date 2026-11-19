package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.ui.auth.findActivity
import com.example.ui.auth.getApkSigningSha1
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches INVEST AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("INVEST AI", context.getString(R.string.app_name))
    }

    @Test
    fun `main activity enforces Google Sign-In auth gate when unauthenticated`() {
        composeRule.onNodeWithText("INVEST AI").assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
    }

    @Test
    fun `crash reporter stores and consumes last crash trace synchronously`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("invest_ai_crash_diag", Context.MODE_PRIVATE)
        prefs.edit().putString("last_crash_trace", "java.lang.IllegalStateException: Test crash").commit()

        val consumed = CrashReporter.consumeLastCrash(context)
        assertEquals("java.lang.IllegalStateException: Test crash", consumed)
        assertEquals(null, CrashReporter.consumeLastCrash(context))
    }

    @Test
    fun `findActivity unwraps ContextWrapper around activity`() {
        val wrapped = android.content.ContextWrapper(composeRule.activity)
        assertEquals(composeRule.activity, wrapped.findActivity())
    }

    @Test
    fun `getApkSigningSha1 handles Robolectric context without throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Must execute safely without throwing even when Robolectric has no signing cert
        val sha1 = getApkSigningSha1(context)
        if (sha1 != null) {
            assertEquals(true, sha1.contains(":"))
        }
    }
}
