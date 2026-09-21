package com.txt2epub.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.txt2epub.app.core.BuiltInRules
import com.txt2epub.app.model.ChapterRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.ruleDataStore by preferencesDataStore(name = "txt2epub_rules")

/** 正则规则的持久化（DataStore）。 */
class RuleStore(private val context: Context) {

    private val key = stringPreferencesKey("rules_json")

    val rules: Flow<List<ChapterRule>> = context.ruleDataStore.data.map { prefs ->
        val raw = prefs[key]
        if (raw.isNullOrBlank()) BuiltInRules.defaults() else decode(raw)
    }

    suspend fun save(list: List<ChapterRule>) {
        context.ruleDataStore.edit { prefs ->
            prefs[key] = encode(list)
        }
    }

    fun encode(list: List<ChapterRule>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    fun decode(raw: String): List<ChapterRule> = try {
        val arr = JSONArray(raw)
        val out = ArrayList<ChapterRule>(arr.length())
        for (i in 0 until arr.length()) out.add(ChapterRule.fromJson(arr.getJSONObject(i)))
        if (out.isEmpty()) BuiltInRules.defaults() else out
    } catch (_: Throwable) {
        BuiltInRules.defaults()
    }
}
