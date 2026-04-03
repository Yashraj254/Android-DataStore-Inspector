package com.yashraj.datastoreinspector.inspector.handler

import android.util.Log
import com.yashraj.datastoreinspector.inspector.proto.ProtoDataStoreHolder
import com.yashraj.datastoreinspector.inspector.proto.ProtoEntry
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
