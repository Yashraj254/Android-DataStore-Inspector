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

import android.content.Context
import android.util.Log
import java.io.File
import androidx.core.content.edit
import com.google.gson.Gson
import com.yashraj.datastoreinspector.inspector.model.PreferenceEntry
import com.yashraj.datastoreinspector.inspector.utils.getType

internal class SharedPreferenceHandler(private val context: Context) {

    companion object {
        private const val TAG = "SharedPreferenceHandler"
        val gson = Gson()
    }

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
                value = if (value is Set<*>) gson.toJson(value) else value,
                type = getType(value)
            )
        }.sortedBy { it.key }
    }
    // Update a value in SharedPreferences with proper type handling
    fun update(name: String, key: String, value: String, type: String) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit {
            when (type) {
                "String" -> putString(key, value)
                "Int" -> putInt(key, value.toInt())
                "Long" -> putLong(key, value.toLong())
                "Float" -> putFloat(key, value.toFloat())
                "Boolean" -> putBoolean(key, value.toBoolean())
                "StringSet" -> {
                    val set = gson.fromJson(value, Array<String>::class.java).toSet()
                    putStringSet(key, set)
                }
            }
        }
        Log.d(TAG, "Writing SharedPrefs: $name[$key] = $value (type: $type)")
    }

    fun delete(name: String, key: String) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit { remove(key) }
        Log.d(TAG, "Deleted SharedPrefs key: $name[$key]")
    }

    fun clear(name: String) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit { clear() }
        Log.d(TAG, "Cleared SharedPrefs: $name")
    }
}

