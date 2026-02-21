package ai.crm247.tracker

data class Crm247Config(
    val domainId: String,
    val baseUrl: String = "https://api.auray.net",
    val trackingEndpoint: String? = null,
    val storageNamespace: String = "crm247.ai",
    val sessionTimeoutMinutes: Int = 43200,
    val batchSize: Int = 10,
    val batchIntervalMs: Long = 5000,
    val maxQueueSize: Int = 500,
    val connectTimeoutMs: Int = 10000,
    val readTimeoutMs: Int = 10000,
    val consentRequired: Boolean = false,
    val consentProvider: ConsentProvider? = null,
    val debug: Boolean = false,
) {
    init {
        require(domainId.isNotBlank()) { "domainId is required" }
        require(batchSize in 1..500) { "batchSize must be between 1 and 500" }
        require(maxQueueSize >= batchSize) { "maxQueueSize must be >= batchSize" }
    }

    fun resolvedTrackingEndpoint(): String {
        val custom = trackingEndpoint?.trim()?.trimEnd('/')
        if (!custom.isNullOrBlank()) return custom
        return "${baseUrl.trim().trimEnd('/')}/api/v1/crm/tracking"
    }
}
