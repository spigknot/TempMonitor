package com.tempmonitor

import android.app.Application
import com.tempmonitor.di.AppContainer
import com.topjohnwu.superuser.Shell

class TempMonitorApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Shell root principal criado UMA vez: sessao interativa persistente (libsu).
        // FLAG_MOUNT_MASTER nao e usado: so precisamos ler sysfs, nada de montar namespace.
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setTimeout(15)
        )
        container = AppContainer(this)
    }
}
