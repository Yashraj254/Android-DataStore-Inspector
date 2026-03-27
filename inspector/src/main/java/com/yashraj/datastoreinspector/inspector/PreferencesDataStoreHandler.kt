package com.yashraj.datastoreinspector.inspector

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

class PreferencesDataStoreHandler(private val context: Context) {

    private val registeredStores: MutableMap<String, DataStore<Preferences>>
        get() = DatastoreInspector.registeredDataStores


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
