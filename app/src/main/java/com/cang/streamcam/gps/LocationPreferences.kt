/*
 * Copyright (C) 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cang.streamcam.gps

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cang.streamcam.MainApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext


/**
 * Data store which holds preferences for the app.
 */
class LocationPreferences {


    val getLastLocation = dataStore?.data?.map {
        it[lastLocationKey] ?: ""
    }

    suspend fun setLastLocationn(lastLocation : String) = withContext(Dispatchers.IO) {
        dataStore?.edit {
            it[lastLocationKey] = lastLocation
        }
    }

    private companion object {
        val lastLocationKey = stringPreferencesKey("last_location")
        val dataStore = MainApplication.getApp()
            ?.let { MainApplication.getApp()?.provideDataStore(it) }
    }

}
