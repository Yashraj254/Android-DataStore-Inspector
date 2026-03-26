package com.yashraj.datastoreinspector.inspector

import android.content.Context
import java.io.File

class SharedPreferenceHandler(private val context: Context) {

    // Get list of all SharedPreferences file names (without .xml extension) in the app's shared_prefs directory
    fun listAll(): List<String> {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        if (!prefsDir.exists()) return emptyList()

        return prefsDir.listFiles()
            ?.filter { it.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }
            ?.sorted()
            ?: emptyList()
    }

    // Get list of PreferenceEntry, which includes the key, value, and type of each entry
    fun getAllWithTypes(name: String): List<PreferenceEntry> {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        return prefs.all.map { (key, value) ->
            PreferenceEntry(
                key = key,
                value = value,
                type = getType(value)
            )
        }.sortedBy { it.key }
    }

    private fun getType(value: Any?): String {
        return when (value) {
            is String -> "String"
            is Int -> "Int"
            is Long -> "Long"
            is Float -> "Float"
            is Boolean -> "Boolean"
            is Set<*> -> "StringSet"
            null -> "Null"
            else -> "Unknown"
        }
    }
}

data class PreferenceEntry(
    val key: String,
    val value: Any?,
    val type: String
)
