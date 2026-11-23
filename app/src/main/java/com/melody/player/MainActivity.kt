package com.melody.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.melody.player.ui.MelodyApp
import com.melody.player.ui.theme.MelodyTheme

class MainActivity : ComponentActivity() {

    private var permissionGranted by mutableStateOf(false)

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    private val requestNotifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知权限结果无需处理界面状态 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 官方沉浸式 edge-to-edge：状态栏/导航栏透明 + 图标明暗自动适配
        enableEdgeToEdge()

        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        permissionGranted = checkSelfPermissionCompat(audioPermission)
        if (!permissionGranted) {
            requestAudioPermission.launch(audioPermission)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MelodyTheme {
                MelodyApp(permissionGranted = permissionGranted)
            }
        }
    }

    private fun checkSelfPermissionCompat(permission: String): Boolean =
        checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
