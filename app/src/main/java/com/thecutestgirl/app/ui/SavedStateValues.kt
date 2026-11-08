package com.thecutestgirl.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import com.thecutestgirl.app.data.parseRecordDate
import java.time.LocalDate
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * P2-7：Compose 可变状态 + SavedStateHandle 双写。
 *
 * 普通字段用 `mutableStateOf` 就能重组，但进程被杀后会丢；直接用 SavedStateHandle
 * 又不会触发重组。这个委托同时做两件事，表单里照常 `var note by savedString(...)`。
 */
internal class SavedStateValue<T>(
    private val handle: SavedStateHandle,
    private val key: String,
    initial: T,
    private val encode: (T) -> Any?,
    private val decode: (Any?) -> T
) : ReadWriteProperty<Any?, T> {

    private var current: T by mutableStateOf(initial)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = current

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        current = value
        handle[key] = encode(value)
    }
}

internal fun savedString(
    handle: SavedStateHandle,
    key: String,
    default: String = ""
): ReadWriteProperty<Any?, String> = SavedStateValue(
    handle, key,
    handle.get<String>(key) ?: default,
    { it },
    { it as? String ?: default }
)

internal fun savedNullableString(
    handle: SavedStateHandle,
    key: String
): ReadWriteProperty<Any?, String?> = SavedStateValue<String?>(
    handle, key,
    handle.get<String>(key),
    { it },
    { it as? String }
)

internal fun savedInt(
    handle: SavedStateHandle,
    key: String,
    default: Int
): ReadWriteProperty<Any?, Int> = SavedStateValue(
    handle, key,
    handle.get<Int>(key) ?: default,
    { it },
    { it as? Int ?: default }
)

internal fun savedDate(
    handle: SavedStateHandle,
    key: String,
    default: LocalDate = LocalDate.now()
): ReadWriteProperty<Any?, LocalDate> = SavedStateValue(
    handle, key,
    parseRecordDate(handle.get<String>(key) ?: "") ?: default,
    { it.toString() },
    { parseRecordDate(it as? String ?: "") ?: default }
)

@Suppress("UNCHECKED_CAST")
internal fun savedStringList(
    handle: SavedStateHandle,
    key: String
): ReadWriteProperty<Any?, List<String>> = SavedStateValue(
    handle, key,
    handle.get<ArrayList<String>>(key) ?: emptyList(),
    { ArrayList(it) },
    { (it as? ArrayList<String>) ?: emptyList() }
)