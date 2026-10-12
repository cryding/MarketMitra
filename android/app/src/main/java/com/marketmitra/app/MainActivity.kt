package com.marketmitra.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

private val Blue = Color(0xFF2457C5)
private val Ink = Color(0xFF172033)
private val Muted = Color(0xFF667085)
private val Page = Color(0xFFF6F8FC)
private val Border = Color(0xFFE4E8F0)
private val Green = Color(0xFF16794B)

private data class ResearchItem(
    val name: String,
    val price: String,
    val source: String,
    val url: String,
    val relevance: Int
)

private data class OpportunityTip(val title: String, val reason: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Blue,
                onPrimary = Color.White,
                background = Page,
                surface = Color.White,
                onSurface = Ink,
                secondary = Green
            )) {
                MarketMitraScreen()
            }
        }
    }
}

@Composable
private fun MarketMitraScreen() {
    var query by remember { mutableStateOf("") }
    var itemsFound by remember { mutableStateOf<List<ResearchItem>>(emptyList()) }
    var tips by remember { mutableStateOf<List<OpportunityTip>>(emptyList()) }
    var sourceSummary by remember { mutableStateOf("Search products to fetch current public marketplace results.") }
    var message by remember { mutableStateOf("Enter a product or category to start live research.") }
    var busy by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = Page) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.background(Blue, RoundedCornerShape(12.dp)).padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("MarketMitra", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("LIVE MARKET RESEARCH", fontSize = 11.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Find products worth researching", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        Text("Search public marketplace listings and get simple research suggestions. No API keys or setup screens.", color = Muted, lineHeight = 20.sp)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Product or category") },
                            placeholder = { Text("e.g. cotton kurti") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    hasSearched = true
                                    itemsFound = emptyList()
                                    tips = emptyList()
                                    message = "Fetching live public results…"
                                    try {
                                        val response = withContext(Dispatchers.IO) { fetchLiveResearch(query.trim()) }
                                        itemsFound = response.items
                                        tips = response.tips
                                        sourceSummary = response.sourceSummary
                                        message = if (response.items.isEmpty()) {
                                            "No accessible listings returned this time. Some marketplaces block automated requests; try another search later."
                                        } else {
                                            "Found ${response.items.size} public result(s). Check the source page before making business decisions."
                                        }
                                    } catch (e: Exception) {
                                        message = e.message ?: "Could not fetch live results. Please try again later."
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !busy && query.trim().length >= 2,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Researching…")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start market research")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("RESEARCH STATUS", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(message, color = Ink, lineHeight = 20.sp)
                        if (hasSearched) {
                            Spacer(Modifier.height(8.dp))
                            Text(sourceSummary, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }

            if (tips.isNotEmpty()) {
                item {
                    Text("Research suggestions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                items(tips) { tip ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(tip.title, fontWeight = FontWeight.SemiBold, color = Ink)
                            Spacer(Modifier.height(5.dp))
                            Text(tip.reason, color = Muted, lineHeight = 19.sp)
                        }
                    }
                }
            }

            if (itemsFound.isNotEmpty()) {
                item { Text("Live public listings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
                items(itemsFound) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { openSource(context, item.url) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = Blue)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold, color = Ink, lineHeight = 20.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.source, color = Muted, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Divider(color = Border)
                            Spacer(Modifier.height(8.dp))
                            Text(if (item.price.isBlank()) "Price not reliably extracted" else item.price, color = Green, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Tap to verify on source website  ↗", color = Blue, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("Live results depend on source access rules and availability. Prices and stock can change.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private data class LiveResponse(
    val items: List<ResearchItem>,
    val tips: List<OpportunityTip>,
    val sourceSummary: String
)

private fun fetchLiveResearch(query: String): LiveResponse {
    require(query.length >= 2) { "Please enter at least two characters." }
    val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
    require(baseUrl.startsWith("https://")) {
        "Live research service is not connected yet. The backend must be deployed once; you do not need to enter an API URL in the app."
    }
    val url = URL("$baseUrl/api/v1/research/live?q=" + URLEncoder.encode(query, "UTF-8"))
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = 12_000
    connection.readTimeout = 20_000
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty("User-Agent", "MarketMitra-Android/0.1")
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        val json = JSONObject(body)
        if (code !in 200..299) throw IllegalStateException(json.optString("detail", "Research request failed (HTTP $code)."))
        val found = mutableListOf<ResearchItem>()
        val array = json.optJSONArray("items")
        if (array != null) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                found.add(
                    ResearchItem(
                        name = obj.optString("name"),
                        price = obj.optString("price"),
                        source = obj.optString("source"),
                        url = obj.optString("url"),
                        relevance = obj.optInt("relevance")
                    )
                )
            }
        }
        val suggestions = mutableListOf<OpportunityTip>()
        val suggestionArray = json.optJSONArray("suggestions")
        if (suggestionArray != null) {
            for (index in 0 until suggestionArray.length()) {
                val obj = suggestionArray.optJSONObject(index) ?: continue
                suggestions.add(OpportunityTip(obj.optString("title"), obj.optString("reason")))
            }
        }
        val statuses = mutableListOf<String>()
        val sourceArray = json.optJSONArray("sources")
        if (sourceArray != null) {
            for (index in 0 until sourceArray.length()) {
                val obj = sourceArray.optJSONObject(index) ?: continue
                statuses.add("${obj.optString("source")}: ${obj.optString("status").replace('_', ' ')}")
            }
        }
        val fetchedAt = json.optString("fetched_at_utc")
        val summary = statuses.joinToString(" • ") + if (fetchedAt.isNotBlank()) "\nFetched: $fetchedAt UTC" else ""
        return LiveResponse(found, suggestions, summary)
    } finally {
        connection.disconnect()
    }
}

private fun openSource(context: Context, url: String) {
    if (!url.startsWith("https://")) return
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}
