package com.aurora.r

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "aurora_settings")

/** ذخیره‌سازی تنظیمات و endpointهای شخصی با DataStore */
class SettingsRepository(private val context: Context) {

    private val KEY_CONFIG = stringPreferencesKey("connection_config")
    private val KEY_ENDPOINTS = stringPreferencesKey("saved_endpoints")

    val config: Flow<ConnectionConfig> = context.dataStore.data.map { prefs ->
        prefs[KEY_CONFIG]?.let {
            runCatching { Json.decodeFromString<ConnectionConfig>(it) }.getOrNull()
        } ?: ConnectionConfig()
    }

    val endpoints: Flow<List<SavedEndpoint>> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENDPOINTS]?.let {
            runCatching {
                Json.decodeFromString(ListSerializer(SavedEndpoint.serializer()), it)
            }.getOrNull()
        } ?: emptyList()
    }

    suspend fun saveConfig(cfg: ConnectionConfig) {
        context.dataStore.edit { it[KEY_CONFIG] = Json.encodeToString(ConnectionConfig.serializer(), cfg) }
    }

    suspend fun saveEndpoints(list: List<SavedEndpoint>) {
        context.dataStore.edit {
            it[KEY_ENDPOINTS] = Json.encodeToString(ListSerializer(SavedEndpoint.serializer()), list)
        }
    }
}
