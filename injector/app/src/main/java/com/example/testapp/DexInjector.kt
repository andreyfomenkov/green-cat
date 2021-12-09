package com.example.testapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dalvik.system.BaseDexClassLoader
import dalvik.system.PathClassLoader
import java.io.File

object DexInjector {

    @SuppressLint("SdCardPath")
    const val DEX_SDCARD_PATH = "/sdcard/greencat/delta.dex"

    private const val TAG = "GreenCat"
    private const val TOAST_SHOW_DELAY = 1500L

    @JvmStatic
    fun inject(application: Application): Throwable? {
        val dexFile = File(DEX_SDCARD_PATH)

        if (hasStoragePermissions(application) && !dexFile.exists()) {
            log("No such file: $DEX_SDCARD_PATH")
            return null
        }
        val classLoader = application.classLoader as? PathClassLoader
        checkNotNull(classLoader) { "Class loader is not instance of PathClassLoader" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            injectForAndroidNougat(classLoader)
        } else {
            throw NotImplementedError("This tool is not implemented for API level < N")
        }.also { error ->
            onInjectionResult(application, error)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("DiscouragedPrivateApi")
    private fun injectForAndroidNougat(classLoader: PathClassLoader) = try {
        var field = BaseDexClassLoader::class.java.getDeclaredField("pathList")
        field.isAccessible = true

        val pathList = field[classLoader]
        val method = classLoader.javaClass.getMethod("addDexPath", String::class.java)

        method.invoke(classLoader, DEX_SDCARD_PATH)
        field = pathList.javaClass.getDeclaredField("dexElements")
        field.isAccessible = true

        // Need to adjust when base application will have multiple DEX files inside APK
        val dexElements = field[pathList] as Array<*>
        log("Array dexElements size is ${dexElements.size}")

        val apk = dexElements[0]
        val dex = dexElements[1]
        val myArray = java.lang.reflect.Array.newInstance(field.type.componentType, 2)
        java.lang.reflect.Array.set(myArray, 0, dex)
        java.lang.reflect.Array.set(myArray, 1, apk)
        field[pathList] = myArray

        log("DEX file successfully loaded")
        null

    } catch (error: Throwable) {
        err("Failed to load DEX file", error)
        error
    }

    private fun onInjectionResult(application: Application, error: Throwable?) {
        LaunchActivityInterceptor(application) { activity ->

            if (!hasStoragePermissions(activity)) {
                err("This tool requires permissions for external storage")
                showStoragePermissionDialog(activity)
            } else {
                val task = Runnable {
                    when (error) {
                        null -> toast(activity, "\uD83D\uDE3A DEX injection successful", short = false)
                        else -> toast(activity, "❌ DEX injection FAILED", short = false)
                    }
                }
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed(task, TOAST_SHOW_DELAY)
            }
        }
    }

    private fun hasStoragePermissions(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun showStoragePermissionDialog(context: Context) =
        AlertDialog.Builder(context)
            .setTitle(TAG)
            .setMessage("This tool requires EXTERNAL STORAGE permissions")
            .setPositiveButton("OK") { _, _ -> }
            .create()
            .show()

    private fun toast(context: Context, message: String, short: Boolean) {
        val duration = when (short) {
            true -> Toast.LENGTH_SHORT
            else -> Toast.LENGTH_LONG
        }
        Toast.makeText(context, message, duration).show()
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    private fun err(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}