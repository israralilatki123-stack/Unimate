package com.example.unimate

import android.app.Application
import com.example.unimate.di.DefaultUniMateAppContainer
import com.example.unimate.di.UniMateAppContainer

class UniMateApplication : Application() {
    lateinit var appContainer: UniMateAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultUniMateAppContainer(this)
    }
}
