package com.yashraj.datastoreinspector.inspector.proto

import androidx.datastore.core.DataStore

internal data class ProtoDataStoreHolder<T>(
    val dataStore: DataStore<T>,
    val mapper: ProtoInspectorMapper<T>
)