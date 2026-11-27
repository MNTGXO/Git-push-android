package com.mntx.gitpulse

import android.app.Application

class GitPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: GitPulseApp
            private set
    }
}
