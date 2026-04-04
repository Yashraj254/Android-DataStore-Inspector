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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal class PreferencesDataStoreHandler(private val dataStores: Map<String, DataStore<Preferences>>) {

    companion object {
        private const val TAG = "PreferencesDataStore"
        private val gson = Gson()
    }


    fun listDataStores(): List<String> = dataStores.keys.toList()


    // Read all preferences from a registered DataStore.
    fun getAll(name: String): List<PreferenceEntry> {
        val dataStore = dataStores[name] ?: return emptyList()
        val prefs = runBlocking { dataStore.data.first() }

        return prefs.asMap().map { (key, value) ->
            PreferenceEntry(
                key = key.name,
                value = if (value is Set<*>) gson.toJson(value) else value,
                type = getType(value)
            )
        }.sortedBy { it.key }
    }

    // Update a value in a registered DataStore with proper type handling
    fun update(name: String, key: String, value: String, type: String) {
        val dataStore = dataStores[name] ?: return
        runBlocking {
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
        }
        Log.d(TAG, "Updated DataStore: $name[$key] = $value (type: $type)")
    }

    // Delete a key from a registered DataStore with proper type handling
    fun delete(name: String, key: String, type: String) {
        val dataStore = dataStores[name] ?: return
        runBlocking {
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
        }
        Log.d(TAG, "Deleted DataStore key: $name[$key]")
    }

    fun clear(name: String) {
        val dataStore = dataStores[name] ?: return
        runBlocking { dataStore.edit { it.clear() } }
        Log.d(TAG, "Cleared DataStore: $name")
    }

}


