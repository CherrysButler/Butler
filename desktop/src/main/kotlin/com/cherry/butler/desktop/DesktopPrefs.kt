package com.cherry.butler.desktop

import android.content.SharedPreferences
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * SharedPreferences as one JSON file per name in %APPDATA%\Butler\prefs. Every value keeps
 * its type. An encrypted file (the session) is sealed with Windows DPAPI, so only this
 * Windows user on this PC can open it.
 */
class DesktopPrefs private constructor(private val file: File, private val encrypted: Boolean) : SharedPreferences {
    private val values = ConcurrentHashMap<String, Any>()
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    init { load() }

    private fun load() {
        if (!file.isFile) return
        val raw = runCatching {
            val bytes = file.readBytes()
            if (encrypted) Crypt32Util.cryptUnprotectData(bytes) else bytes
        }.getOrNull() ?: return
        val obj = runCatching { Json.parseToJsonElement(raw.decodeToString()) as JsonObject }.getOrNull() ?: return
        for ((k, v) in obj) {
            val o = v as? JsonObject ?: continue
            val type = o["t"]?.jsonPrimitive?.content ?: continue
            val value = o["v"] ?: continue
            val decoded: Any? = when (type) {
                "s" -> value.jsonPrimitive.content
                "b" -> value.jsonPrimitive.booleanOrNull
                "i" -> value.jsonPrimitive.content.toIntOrNull()
                "l" -> value.jsonPrimitive.content.toLongOrNull()
                "f" -> value.jsonPrimitive.content.toFloatOrNull()
                "S" -> (value as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet()
                else -> null
            }
            if (decoded != null) values[k] = decoded
        }
    }

    private fun save() {
        val obj = JsonObject(values.mapValues { (_, v) ->
            val (t, e: JsonElement) = when (v) {
                is String -> "s" to JsonPrimitive(v)
                is Boolean -> "b" to JsonPrimitive(v)
                is Int -> "i" to JsonPrimitive(v)
                is Long -> "l" to JsonPrimitive(v)
                is Float -> "f" to JsonPrimitive(v)
                is Set<*> -> "S" to JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> "s" to JsonPrimitive(v.toString())
            }
            JsonObject(mapOf("t" to JsonPrimitive(t), "v" to e))
        })
        val bytes = obj.toString().encodeToByteArray()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(if (encrypted) Crypt32Util.cryptProtectData(bytes) else bytes)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = (values[key] as? Set<String>) ?: defValues
    override fun getInt(key: String, defValue: Int): Int = (values[key] as? Number)?.toInt() ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (values[key] as? Number)?.toLong() ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (values[key] as? Number)?.toFloat() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override val all: Map<String, *> get() = HashMap(values)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += listener }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= listener }

    private inner class Editor : SharedPreferences.Editor {
        private val puts = LinkedHashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { puts[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { puts[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { puts[key] = value }
        override fun putLong(key: String, value: Long) = apply { puts[key] = value }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
        override fun remove(key: String) = apply { puts[key] = null }
        override fun clear() = apply { clear = true }

        private fun merge(): List<String> {
            val changed = mutableListOf<String>()
            synchronized(this@DesktopPrefs) {
                if (clear) { changed += values.keys; values.clear() }
                for ((k, v) in puts) {
                    if (v == null) values.remove(k) else values[k] = v
                    changed += k
                }
            }
            changed.forEach { k -> listeners.forEach { it.onSharedPreferenceChanged(this@DesktopPrefs, k) } }
            return changed
        }

        override fun commit(): Boolean {
            merge()
            return runCatching { synchronized(this@DesktopPrefs) { save() } }.isSuccess
        }

        override fun apply() {
            merge()
            writer.execute { runCatching { synchronized(this@DesktopPrefs) { save() } } }
        }
    }

    companion object {
        private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "butler-prefs").apply { isDaemon = true } }
        private val open = ConcurrentHashMap<String, DesktopPrefs>()
        private val dir: File by lazy { File(DesktopContext.home, "prefs").apply { mkdirs() } }

        fun open(name: String, encrypted: Boolean = false): DesktopPrefs =
            open.getOrPut(name) { DesktopPrefs(File(dir, "$name.json"), encrypted) }
    }
}
