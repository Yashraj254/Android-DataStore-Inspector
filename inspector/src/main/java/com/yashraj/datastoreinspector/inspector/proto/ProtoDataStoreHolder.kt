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
package com.yashraj.datastoreinspector.inspector.proto

import androidx.datastore.core.DataStore
import com.yashraj.datastoreinspector.inspector.model.ProtoEntry
import kotlinx.coroutines.flow.first

/**
 * Pairs a Proto DataStore with the mapper for its message type. The read/write logic lives here,
 * where [T] is known, so callers holding a `ProtoDataStoreHolder<*>` need no unchecked cast.
 */
internal data class ProtoDataStoreHolder<T>(
    val dataStore: DataStore<T>,
    val mapper: ProtoInspectorMapper<T>
) {
    suspend fun readEntries(): List<ProtoEntry> = mapper.toEntries(dataStore.data.first())

    suspend fun updateField(key: String, value: String) {
        dataStore.updateData { old -> mapper.updateField(old, key, value) }
    }
}