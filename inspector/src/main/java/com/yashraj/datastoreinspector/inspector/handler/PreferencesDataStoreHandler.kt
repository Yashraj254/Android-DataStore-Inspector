/*
 * Copyright (C) 2026 Yashraj Singh Jadon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yashraj.datastoreinspector.inspector.handler

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import com.yashraj.datastoreinspector.inspector.model.PreferenceEntry
import com.yashraj.datastoreinspector.inspector.utils.getType
import kotlinx.coroutines.flow.first

internal class PreferencesDataStoreHandler(private val dataStores: Map<String, DataStore<Preferences>>) {

    companion object {
        private const val TAG = "PreferencesDataStore"
        private val gson = Gson()
    }


    fun listDataStores(): List<String> = dataStores.keys.toList()


    // Read all preferences from a registered DataStore.
    suspend fun getAll(name: String): List<PreferenceEntry> {
        val dataStore = dataStores[name] ?: return emptyList()
        val prefs = dataStore.data.first()

        return prefs.asMap().map { (key, value) ->
            PreferenceEntry(
                key = key.name,
                value = if (value is Set<*>) gson.toJson(value) else value,
                type = getType(value)
            )
        }.sortedBy { it.key }
    }

    // Update a value in a registered DataStore with proper type handling
    suspend fun update(name: String, key: String, value: String, type: String) {
        val dataStore = dataStores[name] ?: return
        dataStore.edit { prefs ->
            when (type) {
                "String" -> prefs[stringPreferencesKey(key)] = value
                "Int" -> prefs[intPreferencesKey(key)] = value.toInt()
                "Long" -> prefs[longPreferencesKey(key)] = value.toLong()
                "Float" -> prefs[floatPreferencesKey(key)] = value.toFloat()
                "Double" -> prefs[doublePreferencesKey(key)] = value.toDouble()
                "Boolean" -> prefs[booleanPreferencesKey(key)] = value.toBoolean()
                "StringSet" -> prefs[stringSetPreferencesKey(key)] = gson.fromJson(value, Array<String>::class.java).toSet()
            }
        }
        Log.d(TAG, "Updated DataStore: $name[$key] = $value (type: $type)")
    }

    // Delete a key from a registered DataStore with proper type handling
    suspend fun delete(name: String, key: String, type: String) {
        val dataStore = dataStores[name] ?: return
        dataStore.edit { prefs ->
            val typedKey = when (type) {
                "String" -> stringPreferencesKey(key)
                "Int" -> intPreferencesKey(key)
                "Long" -> longPreferencesKey(key)
                "Float" -> floatPreferencesKey(key)
                "Double" -> doublePreferencesKey(key)
                "Boolean" -> booleanPreferencesKey(key)
                "StringSet" -> stringSetPreferencesKey(key)
                else -> stringPreferencesKey(key)
            }
            prefs.remove(typedKey)
        }
        Log.d(TAG, "Deleted DataStore key: $name[$key]")
    }

    suspend fun clear(name: String) {
        val dataStore = dataStores[name] ?: return
        dataStore.edit { it.clear() }
        Log.d(TAG, "Cleared DataStore: $name")
    }

}


