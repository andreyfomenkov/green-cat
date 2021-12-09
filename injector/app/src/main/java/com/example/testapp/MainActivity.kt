package com.example.testapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.FileUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import android.content.Intent
import android.net.Uri
import android.provider.Settings


class MainActivity : AppCompatActivity() {

    private lateinit var message: TextView
    private lateinit var content: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val button = findViewById<Button>(R.id.open_picker_button)
        message = findViewById(R.id.message_text)
        content = findViewById(R.id.content_text)

        updateMessage()

        button.setOnClickListener {
            if (hasStoragePermissions()) {
                readFile(content)
            } else {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_CODE)
            }
        }
    }

    private fun updateMessage() {
        if (hasStoragePermissions()) {
            message.text = "Есть доступ к хранилищу"
            message.setTextColor(Color.parseColor("#00AF00"))
            readFile(content)
        } else {
            message.text = "Нет доступа к хранилищу"
            message.setTextColor(Color.parseColor("#FF0000"))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            updateMessage()
        }
    }

    private fun openApplicationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun readFile(textView: TextView) {
        textView.text = try {
            File("/sdcard/doc.txt").readText()
        } catch (e: Throwable) {
            e.message
        }
    }

    private fun hasStoragePermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private companion object {
        const val PERMISSIONS_REQUEST_CODE = 123
    }
}