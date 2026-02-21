package ai.crm247.tracker.internal

import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal data class NetworkResponse(
    val ok: Boolean,
    val statusCode: Int,
    val json: JSONObject?,
    val rawBody: String,
)

internal class TrackerNetworkClient(
    private val endpointBase: String,
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int,
) {
    fun postJson(path: String, body: JSONObject): NetworkResponse {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("${endpointBase.trimEnd('/')}$path")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doInput = true
                doOutput = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Content-Type", "application/json")
            }

            BufferedOutputStream(connection.outputStream).use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
                it.flush()
            }

            val statusCode = connection.responseCode
            val reader = if (statusCode in 200..299) {
                InputStreamReader(connection.inputStream)
            } else {
                InputStreamReader(connection.errorStream ?: connection.inputStream)
            }
            val responseText = reader.use { it.readText() }
            NetworkResponse(
                ok = statusCode in 200..299,
                statusCode = statusCode,
                json = runCatching { JSONObject(responseText) }.getOrNull(),
                rawBody = responseText,
            )
        } catch (_: Exception) {
            NetworkResponse(ok = false, statusCode = 0, json = null, rawBody = "")
        } finally {
            connection?.disconnect()
        }
    }
}
