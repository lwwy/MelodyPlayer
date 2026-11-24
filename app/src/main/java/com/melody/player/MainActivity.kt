package com.melody.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    // 永久拒绝 = 已拒且不再弹出系统授权框，此时只能引导去系统设置
    private var permissionPermanentlyDenied by mutableStateOf(false)

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        // 被拒且系统不再提示 rationale，视为永久拒绝
        permissionPermanentlyDenied = !granted &&
            !shouldShowRequestPermissionRationale(audioPermission)
    }

    private val requestNotifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知权限结果无需处理界面状态 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 官方沉浸式 edge-to-edge：状态栏/导航栏透明 + 图标明暗自动适配
        enableEdgeToEdge()

        permissionGranted = checkSelfPermissionCompat(audioPermission)
        permissionPermanentlyDenied = !permissionGranted &&
            !shouldShowRequestPermissionRationale(audioPermission)
        if (!permissionGranted) {
            requestAudioPermission.launch(audioPermission)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MelodyTheme {
                MelodyApp(
                    permissionGranted = permissionGranted,
                    permissionPermanentlyDenied = permissionPermanentlyDenied,
                    onRequestPermission = { requestAudioPermission.launch(audioPermission) },
                    onOpenSettings = { openAppSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置返回后重新校验权限，避免用户已开启但界面仍停在引导页
        if (!permissionGranted && checkSelfPermissionCompat(audioPermission)) {
            permissionGranted = true
            permissionPermanentlyDenied = false
        }
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    private fun checkSelfPermissionCompat(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
