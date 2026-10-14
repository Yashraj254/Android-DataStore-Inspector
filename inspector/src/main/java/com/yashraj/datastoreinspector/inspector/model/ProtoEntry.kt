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
package com.yashraj.datastoreinspector.inspector.model

/**
 * A single inspectable field of a Proto DataStore message.
 *
 * @property key Field name shown in the UI and used as the lookup key for updates.
 * @property value Current field value rendered as a string. For scalar fields this is the plain
 * value; for `"Message"`, `"List"`, and `"Map"` fields it is a JSON document describing the
 * nested structure.
 * @property type One of `"String"`, `"Int"`, `"Long"`, `"Float"`, `"Double"`, `"Boolean"`,
 * `"Enum"` (scalars), or `"Message"`, `"List"`, `"Map"` (composite fields rendered as JSON).
 * Drives input validation and rendering in the inspector UI.
 * @property template For `"List"` fields only, a JSON document for a fresh default element so the
 * UI can render a blank "add item" form even when the list is currently empty. Null otherwise.
 */
data class ProtoEntry(
    val key: String,
    val value: String,
    val type: String,
    val template: String? = null,
)
