package com.cang.streamcam

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.cang.streamcam.gps.PlayServicesAvailabilityChecker
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

/**
 * Set CameraX logging level to Log.ERROR to avoid excessive logcat messages.
 * Refer to https://developer.android.com/reference/androidx/camera/core/CameraXConfig.Builder#setMinimumLoggingLevel(int)
 * for details.
 */

class MainApplication : Application(), CameraXConfig.Provider {

    private lateinit var ourFusedLC : FusedLocationProviderClient
    private lateinit var privateDataStore : DataStore<Preferences>

    fun provideFusedLocationProviderClient() : FusedLocationProviderClient {
        if (ourFusedLC == null) {
            ourFusedLC = LocationServices.getFusedLocationProviderClient(this)
        }
        return ourFusedLC
    }

    fun provideDataStore(): DataStore<Preferences> {
        if (privateDataStore == null){
            privateDataStore = PreferenceDataStoreFactory.create {
                this.preferencesDataStoreFile("prefs")
            }
        }
        return privateDataStore
    }

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(Log.ERROR).build()
    }

    // Called when the application is starting, before any other application objects have been created.
    // Overriding this method is totally optional!
    override fun onCreate() {
        super.onCreate()
        // Required initialization logic here!
        ourFusedLC = LocationServices.getFusedLocationProviderClient(this)
        privateDataStore =  PreferenceDataStoreFactory.create {
            this.preferencesDataStoreFile("prefs")
        }

    }

    // Called by the system when the device configuration changes while your component is running.
    // Overriding this method is totally optional!
    override fun onConfigurationChanged ( newConfig : Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    // This is called when the overall system is running low on memory,
    // and would like actively running processes to tighten their belts.
    // Overriding this method is totally optional!
    override fun onLowMemory() {
        super.onLowMemory()
    }

    companion object {
        private var app: MainApplication? = null

        fun getApp(): MainApplication? {
            return app
        }
    }
}
