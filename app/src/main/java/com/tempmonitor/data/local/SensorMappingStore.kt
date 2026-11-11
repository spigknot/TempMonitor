package com.tempmonitor.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.domain.model.SensorKind
import com.tempmonitor.domain.model.TempScale
import com.tempmonitor.domain.model.ThermalNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "sensor_mapping")

/**
 * Persistencia local (DataStore Preferences):
 *  - cache do mapeamento descoberto (evita rescanear a cada abertura);
 *  - overrides manuais de categoria feitos na tela de nos brutos.
 */
class SensorMappingStore(private val context: Context) {

    private val keyNodes = stringPreferencesKey("discovered_nodes_json")
    private val keyOverrides = stringPreferencesKey("category_overrides_json")

    val overridesFlow: Flow<Map<String, SensorCategory>> =
        context.dataStore.data.map { prefs -> parseOverrides(prefs[keyOverrides]) }

    suspend fun currentOverrides(): Map<String, SensorCategory> = overridesFlow.first()

    suspend fun setOverride(nodeId: String, category: SensorCategory) {
        context.dataStore.edit { prefs ->
            val map = parseOverrides(prefs[keyOverrides]).toMutableMap()
            map[nodeId] = category
            prefs[keyOverrides] = JSONObject().apply {
                map.forEach { (k, v) -> put(k, v.name) }
            }.toString()
        }
    }

    suspend fun saveNodes(nodes: List<ThermalNode>) {
        val arr = JSONArray()
        nodes.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id); put("type", n.type); put("path", n.tempPath)
                put("category", n.category.name); put("kind", n.kind.name)
                put("scale", n.scale.name); put("label", n.label)
                n.cluster?.let { put("cluster", it) }
                put("manual", n.manuallyAssigned)
            })
        }
        context.dataStore.edit { it[keyNodes] = arr.toString() }
    }

    suspend fun loadNodes(): List<ThermalNode> {
        val json = context.dataStore.data.first()[keyNodes] ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ThermalNode(
                    id = o.getString("id"),
                    type = o.getString("type"),
                    tempPath = o.getString("path"),
                    category = SensorCategory.valueOf(o.getString("category")),
                    kind = SensorKind.valueOf(o.optString("kind", "TEMP")),
                    scale = TempScale.valueOf(o.optString("scale", "AUTO")),
                    label = o.optString("label", o.getString("type")),
                    cluster = o.optString("cluster").ifEmpty { null },
                    manuallyAssigned = o.optBoolean("manual", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun parseOverrides(json: String?): Map<String, SensorCategory> {
        if (json.isNullOrEmpty()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { SensorCategory.valueOf(o.getString(it)) }
        }.getOrDefault(emptyMap())
    }
}
