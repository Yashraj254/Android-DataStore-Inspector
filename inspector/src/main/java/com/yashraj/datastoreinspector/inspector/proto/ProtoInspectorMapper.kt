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

import com.yashraj.datastoreinspector.inspector.DataStoreInspector
import com.yashraj.datastoreinspector.inspector.model.ProtoEntry

/**
 * Bridges a Proto DataStore message to the inspector's read/write API.
 *
 * Implement this when registering a Proto DataStore with [DataStoreInspector.registerProto]
 * if you want explicit control over which fields are exposed and how their values are parsed.
 * The default [ReflectiveProtoMapper] discovers fields automatically and is sufficient for most
 * generated proto classes.
 */
interface ProtoInspectorMapper<T> {

    /**
     * Returns the list of inspector fields to display for [proto]. Each entry's `type`
     * string drives client-side validation; see [ProtoEntry] for the supported values.
     */
    fun toEntries(proto: T): List<ProtoEntry>

    /**
     * Returns a new instance of [proto] with the field named [key] set to [value].
     *
     * Implementations should return [proto] unchanged when [key] is unknown or when
     * [value] cannot be parsed for that field.
     */
    fun updateField(proto: T, key: String, value: String): T
}
