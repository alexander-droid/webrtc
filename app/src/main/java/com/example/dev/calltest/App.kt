package com.example.dev.calltest

import android.app.Application
import com.google.firebase.analytics.FirebaseAnalytics

class App: Application() {


    override fun onCreate() {
        super.onCreate()
        FirebaseAnalytics.getInstance(this)
    }
}