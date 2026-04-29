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
 * @property value Current field value rendered as a string.
 * @property type One of `"String"`, `"Int"`, `"Long"`, `"Float"`, `"Double"`, `"Boolean"`,
 * Drives input validation in the inspector UI.
 */
data class ProtoEntry(
    val key: String,
    val value: String,
    val type: String,
)
