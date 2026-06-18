package com.nextaffiliate.sample

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nextaffiliate.sdk.Attribution
import com.nextaffiliate.sdk.NextAffiliate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "NextAffiliateSample"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Route the launch deep-link (if any) into the SDK.
        handleIncoming(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SampleScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        val url = intent?.dataString ?: return
        lifecycleScope.launch {
            val attribution = NextAffiliate.handleLink(url)
            Log.d(TAG, "handleLink($url) -> $attribution")
        }
    }
}

@Composable
private fun SampleScreen() {
    var attribution by remember { mutableStateOf<Attribution?>(NextAffiliate.getAttribution()) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Next Affiliate SDK", style = MaterialTheme.typography.headlineSmall)
        Text("baseDomain: $DEV_BASE_DOMAIN")

        Text("Attribution", style = MaterialTheme.typography.titleMedium)
        Text("nxPb: ${attribution?.nxPb ?: "-"}")
        Text("clickId: ${attribution?.clickId ?: "-"}")
        Text("source: ${attribution?.source?.wireValue ?: "-"}")
        Text("isAttributed: ${attribution?.isAttributed ?: false}")

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    NextAffiliate.checkDeferredOnFirstLaunch()
                    attribution = NextAffiliate.getAttribution()
                }
            },
        ) { Text("Check deferred") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    NextAffiliate.handleLink(
                        "$APP_SCHEME://open?nx_pb=DEMO_TOKEN&nx_click_id=demo-click",
                    )
                    attribution = NextAffiliate.getAttribution()
                }
            },
        ) { Text("Simulate scheme link") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    status = sendTestConversion(NextAffiliate.getAttribution()?.nxPb)
                }
            },
        ) { Text("Send test conversion") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                NextAffiliate.clearAttribution()
                attribution = NextAffiliate.getAttribution()
            },
        ) { Text("Clear attribution") }

        if (status.isNotEmpty()) Text(status)
    }
}

/**
 * Stub S2S conversion: reads the stored `nxPb` and POSTs it to a placeholder endpoint.
 * Best-effort; failures are logged, never thrown.
 */
private suspend fun sendTestConversion(nxPb: String?): String = withContext(Dispatchers.IO) {
    if (nxPb == null) {
        Log.d(TAG, "No nxPb stored; nothing to send.")
        return@withContext "No nxPb stored — nothing to send."
    }
    val body = """{"nx_pb":"$nxPb","event":"test_conversion"}"""
    Log.d(TAG, "Sending test conversion: $body")
    runCatching {
        val conn = (URL("https://$DEV_BASE_DOMAIN/trk/conversion").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        conn.disconnect()
        "Test conversion POSTed (HTTP $code)"
    }.getOrElse { "Test conversion attempted (placeholder): $body" }
}
