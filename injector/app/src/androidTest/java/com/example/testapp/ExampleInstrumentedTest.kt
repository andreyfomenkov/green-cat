package com.example.testapp

import android.Manifest
import android.app.Instrumentation
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class HelloWorldEspressoTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    lateinit var instrumentation: Instrumentation
    lateinit var device: UiDevice

    @Before
    fun setup() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
    }

    @Test
    fun testStoragePermissionRevoked() {
        delay(2)

        val handler = Handler(Looper.getMainLooper())
        onView(withText("ОТКРЫТЬ PICKER")).check(matches(isDisplayed()))
        onView(withId(R.id.message_text)).check(matches(isDisplayed()))
        onView(withId(R.id.message_text)).check(matches(withText("Нет доступа к хранилищу")))

        handler.post(object : Runnable {

            override fun run() {
                val allowButton = device.findObject(UiSelector().text("ALLOW"))

                if (allowButton.exists()) {
                    allowButton.click()
                } else {
                    handler.postDelayed(this, 500L)
                }
            }
        })
        onView(withText("ОТКРЫТЬ PICKER")).perform(click())

        delay(7)
        onView(withId(R.id.message_text)).check(matches(withText("Есть доступ к хранилищу")))
    }

    private fun toggleStoragePermission() {
        openApplicationSettings()
        delay(2)
        findPermissionsOption().click()
        delay(2)
        findStorageItem().click()
        delay(2)
        device.pressBack()
        delay(2)
        device.pressBack()
    }

    private fun findPermissionsOption(): UiObject {
        device.findObject(UiSelector().textStartsWith("Permission")).run {
            if (exists()) {
                return this
            }
        }
        device.findObject(UiSelector().textStartsWith("Разрешени")).run {
            if (exists()) {
                return this
            }
        }
        throw RuntimeException("Can't find permissions option")
    }

    private fun findStorageItem(): UiObject {
        device.findObject(UiSelector().textStartsWith("Storage")).run {
            if (exists()) {
                return this
            }
        }
        device.findObject(UiSelector().textStartsWith("Память")).run {
            if (exists()) {
                return this
            }
        }
        throw RuntimeException("Can't find storage item")
    }

    private fun openApplicationSettings() {
        val context = instrumentation.context
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", "com.example.testapp", null)
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun delay(sec: Long) = device.waitForIdle(sec * 1000L)

    private fun hasStoragePermissions() =
        ContextCompat.checkSelfPermission(instrumentation.context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
}