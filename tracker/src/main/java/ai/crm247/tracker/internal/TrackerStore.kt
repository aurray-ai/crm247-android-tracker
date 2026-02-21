package ai.crm247.tracker.internal

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class TrackerStore(
    context: Context,
    private val namespace: String,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("${namespace}.tracker", Context.MODE_PRIVATE)

    private fun key(suffix: String): String = "${namespace}.${suffix}"

    fun getVisitorId(): String? = prefs.getString(key("visitor_id"), null)

    fun setVisitorId(visitorId: String) {
        prefs.edit().putString(key("visitor_id"), visitorId).apply()
    }

    fun getSession(): SessionState? {
        val id = prefs.getString(key("session_id"), null) ?: return null
        val createdAt = prefs.getLong(key("session_created_at"), 0L)
        val lastSeenAt = prefs.getLong(key("session_last_seen_at"), 0L)
        if (createdAt <= 0L || lastSeenAt <= 0L) return null
        return SessionState(id = id, createdAtMs = createdAt, lastSeenAtMs = lastSeenAt)
    }

    fun saveSession(session: SessionState) {
        prefs.edit()
            .putString(key("session_id"), session.id)
            .putLong(key("session_created_at"), session.createdAtMs)
            .putLong(key("session_last_seen_at"), session.lastSeenAtMs)
            .apply()
    }

    fun loadQueue(): MutableList<JSONObject> {
        val raw = prefs.getString(key("event_queue"), null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(obj)
            }
            list
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveQueue(queue: List<JSONObject>) {
        val arr = JSONArray()
        queue.forEach { arr.put(it) }
        prefs.edit().putString(key("event_queue"), arr.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun getOrCreateVisitorId(): String {
        val existing = getVisitorId()
        if (!existing.isNullOrBlank()) return existing
        val generated = "v_${UUID.randomUUID().toString().replace("-", "")}"
        setVisitorId(generated)
        return generated
    }
}

internal data class SessionState(
    val id: String,
    val createdAtMs: Long,
    val lastSeenAtMs: Long,
)
