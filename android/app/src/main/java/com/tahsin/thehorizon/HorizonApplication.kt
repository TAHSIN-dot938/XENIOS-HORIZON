package com.tahsin.thehorizon

import android.app.Application

class HorizonApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
