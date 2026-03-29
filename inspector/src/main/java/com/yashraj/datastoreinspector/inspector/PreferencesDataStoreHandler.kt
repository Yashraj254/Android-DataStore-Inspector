package com.yashraj.datastoreinspector.inspector

import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class PreferencesDataStoreHandler(private val context: Context) {

    companion object {
        private const val TAG = "PreferencesDataStore"
    }
    private val registeredStores: MutableMap<String, DataStore<Preferences>>
        get() = DatastoreInspector.registeredDataStores


    fun listDataStores(): List<String> = registeredStores.keys.toList()


    // List all DataStore files found on disk + registered instances.
    fun listAll(): List<DataStoreInfo> {
        val datastoreDir = File(context.filesDir, "datastore")
        val fileNames = if (datastoreDir.exists()) {
            datastoreDir.listFiles()
                ?.filter { it.name.endsWith(".preferences_pb") }
                ?.map { it.name.removeSuffix(".preferences_pb") }
                ?.sorted()
                ?: emptyList()
        } else {
            emptyList()
        }

        return fileNames.map { name ->
            DataStoreInfo(
                name = name,
                registered = registeredStores.containsKey(name)
            )
        }
    }


    // Read all preferences from a registered DataStore.
    fun getAll(name: String): List<DataStoreEntry> {
        val dataStore = registeredStores[name]
            ?: return emptyList()

        return runBlocking {
            val prefs = dataStore.data.first()
            prefs.asMap().map { (key, value) ->
                DataStoreEntry(
                    key = key.name,
                    value = value,
                    type = getType(value)
                )
            }.sortedBy { it.key }
        }
    }

    // Update a value in a registered DataStore with proper type handling
    fun update(name: String, key: String, value: String, type: String) {
        val dataStore = registeredStores[name] ?: return
        CoroutineScope(Dispatchers.IO).launch {
            dataStore.edit { prefs ->
                when (type) {
                    "String" -> prefs[stringPreferencesKey(key)] = value
                    "Int" -> prefs[intPreferencesKey(key)] = value.toInt()
                    "Long" -> prefs[longPreferencesKey(key)] = value.toLong()
                    "Float" -> prefs[floatPreferencesKey(key)] = value.toFloat()
                    "Double" -> prefs[doublePreferencesKey(key)] = value.toDouble()
                    "Boolean" -> prefs[booleanPreferencesKey(key)] = value.toBoolean()
                }
            }
        }
        Log.d(TAG, "Updated DataStore: $name[$key] = $value (type: $type)")
    }

    // Delete a key from a registered DataStore with proper type handling
    fun delete(name: String, key: String, type: String) {
        val dataStore = registeredStores[name] ?: return
        CoroutineScope(Dispatchers.IO).launch {
            dataStore.edit { prefs ->
                val typedKey = when (type) {
                    "String" -> stringPreferencesKey(key)
                    "Int" -> intPreferencesKey(key)
                    "Long" -> longPreferencesKey(key)
                    "Float" -> floatPreferencesKey(key)
                    "Double" -> doublePreferencesKey(key)
                    "Boolean" -> booleanPreferencesKey(key)
                    else -> stringPreferencesKey(key)
                }
                prefs.remove(typedKey)
            }
        }
        Log.d(TAG, "Deleted DataStore key: $name[$key]")
    }

    fun clear(name: String) {
        val dataStore = registeredStores[name] ?: return
        runBlocking { dataStore.edit { it.clear() } }
        Log.d(TAG, "Cleared DataStore: $name")
    }

    private fun getType(value: Any?): String {
        return when (value) {
            is String -> "String"
            is Int -> "Int"
            is Long -> "Long"
            is Float -> "Float"
            is Double -> "Double"
            is Boolean -> "Boolean"
            is Set<*> -> "StringSet"
            null -> "Null"
            else -> "Unknown"
        }
    }
}

data class DataStoreInfo(
    val name: String,
    val registered: Boolean
)

data class DataStoreEntry(
    val key: String,
    val value: Any?,
    val type: String
)
