package ai.crm247.tracker

import android.content.Context
import android.os.Build
import android.os.SystemClock
import ai.crm247.tracker.internal.SessionState
import ai.crm247.tracker.internal.TrackerNetworkClient
import ai.crm247.tracker.internal.TrackerStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * CRM247 Android tracker SDK.
 *
 * Core API:
 * - init
 * - identify
 * - screen
 * - track
 * - flush
 * - reset
 */
object Crm247Tracker {
    private const val SDK_NAME = "crm247-android"
    private const val SDK_VERSION = "0.1.0"

    private val lock = Any()
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context
    private lateinit var config: Crm247Config
    private lateinit var store: TrackerStore
    private lateinit var networkClient: TrackerNetworkClient

    private var scheduler: ScheduledExecutorService? = null

    private var visitorReady = false
    private var flushInFlight = false

    private var visitorId: String = ""
    private var sessionId: String = ""

    private var currentScreenName: String? = null
    private var currentScreenPageUrl: String? = null
    private var previousScreenPageUrl: String? = null
    private var currentScreenStartedAtMs: Long = 0L
    private var lastSentActiveMs: Long = 0L

    private var pageVisitId: String? = null
    private var pageEventSeq: Int = 0

    private var contextMetadata: Map<String, Any?> = emptyMap()

    private val eventQueue = mutableListOf<JSONObject>()

    fun init(context: Context, config: Crm247Config) {
        synchronized(lock) {
            this.appContext = context.applicationContext
            this.config = config
            this.store = TrackerStore(this.appContext, config.storageNamespace)
            this.networkClient = TrackerNetworkClient(
                endpointBase = config.resolvedTrackingEndpoint(),
                connectTimeoutMs = config.connectTimeoutMs,
                readTimeoutMs = config.readTimeoutMs,
            )

            visitorId = store.getOrCreateVisitorId()
            sessionId = getOrCreateSessionIdLocked()

            eventQueue.clear()
            eventQueue.addAll(store.loadQueue())

            contextMetadata = buildContextMetadata(this.appContext)
            initialized = true
        }

        startSchedulerIfNeeded()
        runAsync { registerVisitorLocked(email = null, properties = emptyMap()) }
    }

    fun identify(email: String, properties: Map<String, Any?> = emptyMap()) {
        runAsync {
            if (!canTrackLocked()) return@runAsync
            if (email.isBlank()) return@runAsync
            registerVisitorLocked(email = email.trim(), properties = properties)
        }
    }

    fun screen(screenName: String, properties: Map<String, Any?> = emptyMap()) {
        runAsync {
            if (!canTrackLocked()) return@runAsync
            if (screenName.isBlank()) return@runAsync

            emitTimeOnCurrentScreenLocked(reason = "page_visit_end", force = true)

            previousScreenPageUrl = currentScreenPageUrl
            currentScreenName = screenName.trim()
            currentScreenPageUrl = pageUrlForScreen(currentScreenName!!)
            currentScreenStartedAtMs = SystemClock.elapsedRealtime()
            lastSentActiveMs = 0L

            pageVisitId = generateId("pv")
            pageEventSeq = 0

            val metadata = mutableMapOf<String, Any?>(
                "reason" to "page_visit_start",
                "screen_name" to currentScreenName,
                "platform" to "android",
            )
            metadata.putAll(properties)

            val event = buildEventLocked(
                eventType = EventType.PAGE_VIEW.wireValue,
                metadata = metadata,
                pageUrl = currentScreenPageUrl,
                pageTitle = currentScreenName,
                referrer = previousScreenPageUrl,
            )
            enqueueLocked(event)
            flushIfNeededLocked()
        }
    }

    fun track(eventType: String, metadata: Map<String, Any?> = emptyMap()) {
        runAsync {
            if (!canTrackLocked()) return@runAsync
            if (eventType.isBlank()) return@runAsync

            val normalizedType = EventType.normalize(eventType)
            val event = buildEventLocked(
                eventType = normalizedType,
                metadata = metadata,
                pageUrl = currentScreenPageUrl,
                pageTitle = currentScreenName,
                referrer = previousScreenPageUrl,
            )
            enqueueLocked(event)
            flushIfNeededLocked()
        }
    }

    fun flush() {
        runAsync {
            if (!canTrackLocked()) return@runAsync
            emitTimeOnCurrentScreenLocked(reason = "heartbeat", force = false)
            flushLocked(forceAll = true)
        }
    }

    fun reset() {
        runAsync {
            synchronized(lock) {
                store.clearAll()
                eventQueue.clear()

                visitorReady = false
                flushInFlight = false

                visitorId = store.getOrCreateVisitorId()
                sessionId = getOrCreateSessionIdLocked(forceNew = true)

                currentScreenName = null
                currentScreenPageUrl = null
                previousScreenPageUrl = null
                currentScreenStartedAtMs = 0L
                lastSentActiveMs = 0L
                pageVisitId = null
                pageEventSeq = 0
            }
            registerVisitorLocked(email = null, properties = emptyMap())
        }
    }

    fun shutdown() {
        runAsync {
            if (!initialized) return@runAsync
            emitTimeOnCurrentScreenLocked(reason = "app_background", force = true)
            flushLocked(forceAll = true)
        }
        synchronized(lock) {
            scheduler?.shutdownNow()
            scheduler = null
        }
    }

    private fun startSchedulerIfNeeded() {
        synchronized(lock) {
            if (scheduler != null) return
            scheduler = Executors.newSingleThreadScheduledExecutor().apply {
                scheduleAtFixedRate(
                    {
                        runAsync {
                            if (!canTrackLocked()) return@runAsync
                            emitTimeOnCurrentScreenLocked(reason = "heartbeat", force = false)
                            flushLocked(forceAll = false)
                        }
                    },
                    config.batchIntervalMs,
                    config.batchIntervalMs,
                    TimeUnit.MILLISECONDS,
                )
            }
        }
    }

    private fun runAsync(block: () -> Unit) {
        worker.execute {
            try {
                block()
            } catch (t: Throwable) {
                log("Unhandled tracker exception: ${t.message}")
            }
        }
    }

    private fun canTrackLocked(): Boolean {
        synchronized(lock) {
            if (!initialized) return false
            if (!config.consentRequired) return true
            return config.consentProvider?.hasConsent() == true
        }
    }

    private fun registerVisitorLocked(email: String?, properties: Map<String, Any?>) {
        val payload = JSONObject().apply {
            put("domain_id", config.domainId)
            put("visitor_id", visitorId)
            put("email", email ?: JSONObject.NULL)
            put("page_url", currentScreenPageUrl ?: "app://unknown")
            put("page_title", currentScreenName ?: "Unknown Screen")
            put("referrer", previousScreenPageUrl ?: JSONObject.NULL)
            put("user_agent", System.getProperty("http.agent") ?: "android")
            put("ip_address", JSONObject.NULL)
            put("properties", mapToJsonObject(contextMetadata + properties))
        }

        val response = networkClient.postJson("/visitor", payload)
        if (!response.ok) {
            synchronized(lock) {
                visitorReady = false
            }
            log("Visitor registration failed: status=${response.statusCode}")
            return
        }

        synchronized(lock) {
            val responseVisitorId = response.json?.optString("visitor_id", "").orEmpty()
            if (responseVisitorId.isNotBlank() && responseVisitorId != visitorId) {
                visitorId = responseVisitorId
                store.setVisitorId(visitorId)
                eventQueue.forEach { it.put("visitor_id", visitorId) }
                store.saveQueue(eventQueue)
            }
            visitorReady = true
        }

        flushLocked(forceAll = false)
    }

    private fun buildEventLocked(
        eventType: String,
        metadata: Map<String, Any?>,
        pageUrl: String?,
        pageTitle: String?,
        referrer: String?,
    ): JSONObject {
        refreshSessionActivityLocked()
        pageEventSeq += 1

        val metadataJson = mapToJsonObject(contextMetadata + metadata).apply {
            put("page_visit_id", pageVisitId ?: JSONObject.NULL)
            put("event_seq", pageEventSeq)
        }

        return JSONObject().apply {
            put("domain_id", config.domainId)
            put("visitor_id", visitorId)
            put("event_type", eventType)
            put("page_url", pageUrl ?: "app://unknown")
            put("page_title", pageTitle ?: "Unknown Screen")
            put("referrer", referrer ?: JSONObject.NULL)
            put("metadata", metadataJson)
            put("session_id", sessionId)
            put("timestamp", isoNow())
        }
    }

    private fun enqueueLocked(event: JSONObject) {
        synchronized(lock) {
            if (eventQueue.size >= config.maxQueueSize) {
                eventQueue.removeAt(0)
            }
            eventQueue.add(event)
            store.saveQueue(eventQueue)
        }
    }

    private fun flushIfNeededLocked() {
        synchronized(lock) {
            if (!visitorReady) return
            if (eventQueue.size < config.batchSize) return
        }
        flushLocked(forceAll = false)
    }

    private fun flushLocked(forceAll: Boolean) {
        if (!canTrackLocked()) return

        while (true) {
            val batch = synchronized(lock) {
                if (!visitorReady) return
                if (flushInFlight) return
                if (eventQueue.isEmpty()) return

                val count = if (forceAll) eventQueue.size else min(config.batchSize, eventQueue.size)
                if (count <= 0) return

                val taken = eventQueue.subList(0, count).map { JSONObject(it.toString()) }
                eventQueue.subList(0, count).clear()
                store.saveQueue(eventQueue)
                flushInFlight = true
                taken
            }

            val response = networkClient.postJson(
                path = "/events/batch",
                body = JSONObject().put("events", JSONArray(batch)),
            )

            synchronized(lock) {
                flushInFlight = false
                if (!response.ok) {
                    eventQueue.addAll(0, batch)
                    store.saveQueue(eventQueue)
                }
            }

            if (!response.ok) {
                log("Batch flush failed: status=${response.statusCode}")
                return
            }

            if (!forceAll) {
                return
            }
        }
    }

    private fun emitTimeOnCurrentScreenLocked(reason: String, force: Boolean) {
        val activeMs = getCurrentActiveMsLocked()
        if (activeMs <= 0L) return

        val deltaMs = activeMs - lastSentActiveMs
        if (!force && deltaMs < 1000L) return

        val metadata = mapOf(
            "active_ms_delta" to if (deltaMs < 0L) 0L else deltaMs,
            "active_ms_total" to activeMs,
            "reason" to reason,
        )

        val event = buildEventLocked(
            eventType = EventType.TIME_ON_PAGE.wireValue,
            metadata = metadata,
            pageUrl = currentScreenPageUrl,
            pageTitle = currentScreenName,
            referrer = previousScreenPageUrl,
        )
        enqueueLocked(event)
        lastSentActiveMs = activeMs
    }

    private fun getCurrentActiveMsLocked(): Long {
        val start = currentScreenStartedAtMs
        if (start <= 0L) return 0L
        val elapsed = SystemClock.elapsedRealtime() - start
        return if (elapsed > 0L) elapsed else 0L
    }

    private fun refreshSessionActivityLocked() {
        sessionId = getOrCreateSessionIdLocked(forceNew = false)
    }

    private fun getOrCreateSessionIdLocked(forceNew: Boolean = false): String {
        val now = System.currentTimeMillis()
        val timeoutMs = config.sessionTimeoutMinutes.toLong() * 60L * 1000L
        val saved = store.getSession()

        if (!forceNew && saved != null) {
            val stillValid = now - saved.lastSeenAtMs <= timeoutMs
            if (stillValid) {
                val updated = saved.copy(lastSeenAtMs = now)
                store.saveSession(updated)
                return updated.id
            }
        }

        val newId = "sess_${UUID.randomUUID().toString().replace("-", "")}"
        store.saveSession(SessionState(id = newId, createdAtMs = now, lastSeenAtMs = now))
        return newId
    }

    private fun pageUrlForScreen(screenName: String): String {
        val appPackage = appContext.packageName
        val slug = screenName
            .trim()
            .lowercase()
            .replace(" ", "-")
            .replace(Regex("[^a-z0-9_-]"), "")
            .ifBlank { "screen" }
        return "app://$appPackage/$slug"
    }

    private fun buildContextMetadata(context: Context): Map<String, Any?> {
        val packageName = context.packageName
        val appVersion = runCatching {
            val pInfo = context.packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "unknown"
        }.getOrDefault("unknown")

        return mapOf(
            "platform" to "android",
            "sdk_name" to SDK_NAME,
            "sdk_version" to SDK_VERSION,
            "app_package" to packageName,
            "app_version" to appVersion,
            "os_name" to "Android",
            "os_version" to Build.VERSION.RELEASE,
            "device_model" to Build.MODEL,
            "device_manufacturer" to Build.MANUFACTURER,
            "locale" to Locale.getDefault().toLanguageTag(),
            "timezone" to TimeZone.getDefault().id,
        )
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (key, value) ->
            obj.put(key, normalizeValue(value))
        }
        return obj
    }

    private fun normalizeValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is String, is Number, is Boolean -> value
            is Map<*, *> -> {
                val obj = JSONObject()
                value.forEach { (k, v) ->
                    if (k != null) obj.put(k.toString(), normalizeValue(v))
                }
                obj
            }
            is Iterable<*> -> {
                val arr = JSONArray()
                value.forEach { arr.put(normalizeValue(it)) }
                arr
            }
            else -> value.toString()
        }
    }

    private fun isoNow(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }

    private fun generateId(prefix: String): String {
        return "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
    }

    private fun log(message: String) {
        if (!::config.isInitialized || !config.debug) return
        android.util.Log.d("CRM247", message)
    }
}
