package com.yashraj.datastoreinspector.inspector

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ProtoDataStoreHandler(private val protoDataStores: Map<String, ProtoDataStoreHolder<*>>) {

    companion object {
        private const val TAG = "ProtoDataStoreHandler"
    }
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
        CoroutineScope(Dispatchers.IO).launch {
            holder.dataStore.updateData { old -> holder.mapper.updateField(old, key, value) }
        }
        Log.d(TAG, "Updated Proto DataStore: $name[$key] = $value")
    }
}
