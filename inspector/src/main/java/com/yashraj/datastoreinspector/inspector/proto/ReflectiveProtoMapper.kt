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

import com.yashraj.datastoreinspector.inspector.model.ProtoEntry
import java.lang.reflect.Modifier

internal class ReflectiveProtoMapper<T : Any> : ProtoInspectorMapper<T> {

    // Caches filtered methods per class so reflection runs only once per proto type.
    private val methodCache = mutableMapOf<Class<*>, List<java.lang.reflect.Method>>()

    private fun getFieldMethods(clazz: Class<*>) = methodCache.getOrPut(clazz) {
        clazz.methods.filter { method ->
            val name = method.name
            val returnType = method.returnType
            name.startsWith("get")
                    && method.parameterCount == 0
                    && !Modifier.isStatic(method.modifiers)
                    && !name.endsWith("Bytes")
                    && !name.endsWith("Count")
                    && !name.endsWith("List")
                    && name != "getClass"
                    && name != "getDefaultInstanceForType"
                    && name != "getSerializedSize"
                    && name != "getAllFields"
                    && name != "getUnknownFields"
                    && !returnType.name.contains("Builder")
                    && !returnType.name.contains("ByteString")
                    && !returnType.name.contains("Descriptor")
                    && !returnType.name.contains("Parser")
        }
    }

    override fun toEntries(proto: T): List<ProtoEntry> {
        return getFieldMethods(proto.javaClass)
            .mapNotNull { method ->
                val value = runCatching { method.invoke(proto) }.getOrNull() ?: return@mapNotNull null
                val fieldName = method.name.removePrefix("get").let { camelToSnake(it) }
                ProtoEntry(fieldName, value.toString(), inferType(value))
            }
    }

    override fun updateField(proto: T, key: String, value: String): T {
        val setterName = "set" + key.split("_").joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        val builder = proto.javaClass.getMethod("toBuilder").invoke(proto)
        val setter = builder.javaClass.methods.firstOrNull { it.name == setterName } ?: return proto
        val paramType = setter.parameterTypes[0]
        val typedValue: Any = when {
            paramType == String::class.java -> value
            paramType == Boolean::class.java -> value.toBoolean()
            paramType == Int::class.java -> value.toInt()
            paramType == Long::class.java -> value.toLong()
            paramType == Float::class.java -> value.toFloat()
            paramType == Double::class.java -> value.toDouble()
            paramType.isEnum -> paramType.enumConstants?.first { (it as Enum<*>).name == value } ?: return proto
            else -> return proto
        }
        setter.invoke(builder, typedValue)
        @Suppress("UNCHECKED_CAST")
        return builder.javaClass.getMethod("build").invoke(builder) as T
    }

    private fun camelToSnake(name: String): String =
        name.replace(Regex("(?<=[a-z])(?=[A-Z])"), "_").lowercase()

    private fun inferType(value: Any): String = when (value) {
        is String -> "String"
        is Boolean -> "Boolean"
        is Int -> "Int"
        is Long -> "Long"
        is Float -> "Float"
        is Double -> "Double"
        else -> if (value.javaClass.isEnum) "Enum" else "String"
    }
}
