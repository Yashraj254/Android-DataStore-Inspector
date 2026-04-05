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
import com.yashraj.datastoreinspector.inspector.model.ProtoEntry
import com.yashraj.datastoreinspector.inspector.proto.ProtoDataStoreHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal class ProtoDataStoreHandler(private val protoDataStores: Map<String, ProtoDataStoreHolder<*>>) {

    companion object {
        private const val TAG = "ProtoDataStoreHandler"
    }

    fun listProtoStores(): List<String> = protoDataStores.keys.toList()

    @Suppress("UNCHECKED_CAST")
    fun getAll(name: String): List<ProtoEntry> {
        val holder = protoDataStores[name] as? ProtoDataStoreHolder<Any> ?: return emptyList()
        val proto = runBlocking { holder.dataStore.data.first() }
        val entries = holder.mapper.toEntries(proto)
        Log.d(TAG, "Proto DataStore: $name, fields: ${entries.size}")
        return entries
    }

    @Suppress("UNCHECKED_CAST")
    fun update(name: String, key: String, value: String) {
        val holder = protoDataStores[name] as? ProtoDataStoreHolder<Any> ?: return
        runBlocking {
            holder.dataStore.updateData { old -> holder.mapper.updateField(old, key, value) }
        }
        Log.d(TAG, "Updated Proto DataStore: $name[$key] = $value")
    }
}
