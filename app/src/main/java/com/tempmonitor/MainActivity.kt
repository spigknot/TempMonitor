package com.tempmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.tempmonitor.ui.TempMonitorScreen
import com.tempmonitor.ui.TempMonitorViewModel
import com.tempmonitor.ui.theme.TempMonitorTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sem notificacao, o foreground service apenas nao expoe o aviso */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val viewModel = ViewModelProvider(
            this,
            TempMonitorViewModel.factory(application as TempMonitorApplication),
        )[TempMonitorViewModel::class.java]

        setContent {
            TempMonitorTheme {
                TempMonitorScreen(viewModel = viewModel)
            }
        }
    }
}
