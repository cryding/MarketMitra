package com.marketmitra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Navy = Color(0xFF101B2D)
private val Panel = Color(0xFF1A2940)
private val Cyan = Color(0xFF67E8F9)
private val Muted = Color(0xFF9FB0C8)

private data class ProductIdea(
    val name: String,
    val category: String,
    val price: String,
    val score: Int,
    val signal: String
)

private val demoProducts = listOf(
    ProductIdea("Cotton everyday kurti", "Women fashion", "₹299–₹699", 72, "Variant-led niche · sample idea"),
    ProductIdea("Jute gift & packaging bags", "Sustainable goods", "₹80–₹350", 75, "Custom branding · sample idea"),
    ProductIdea("Reusable kitchen storage set", "Home & kitchen", "₹199–₹499", 68, "Bundle potential · sample idea"),
    ProductIdea("Fashion jewellery combo", "Accessories", "₹149–₹399", 64, "Compact shipping · sample idea"),
    ProductIdea("Incense gift pack", "Home fragrance", "₹99–₹299", 61, "Giftable packaging · sample idea")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Cyan,
                onPrimary = Navy,
                background = Navy,
                surface = Panel,
                onSurface = Color.White,
                secondary = Color(0xFF86EFAC)
            )) {
                MarketMitraScreen()
            }
        }
    }
}

@Composable
private fun MarketMitraScreen() {
    var query by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf("") }
    var targetUrl by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Demo mode: ideas below are sample data, not live marketplace rankings.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val filtered = demoProducts.filter {
        query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true) || it.signal.contains(query, true)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.background(Panel, RoundedCornerShape(18.dp)).padding(13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = Cyan)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("MarketMitra", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("RESEARCH • COMPARE • BUILD", fontSize = 10.sp, color = Muted, letterSpacing = 1.5.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Find your next business opportunity", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("Product ideas, competitor page research and brand planning — one workspace.", color = Muted, lineHeight = 20.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricChip("₹0", "paid AI")
                            MetricChip("Public", "data first")
                            MetricChip("MVP", "milestone 1")
                        }
                    }
                }
            }
            item {
                Text("PRODUCT RESEARCH", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.3.sp)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search product or category") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Spacer(Modifier.height(8.dp))
                Text("Sample opportunities • validate with real sources before investing", color = Muted, fontSize = 12.sp)
            }
            items(filtered) { idea -> ProductCard(idea) }
            item {
                Divider(color = Color(0xFF2B3A50))
                Text("PUBLIC PAGE SCRAPER", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.3.sp)
                Text("Connect a running MarketMitra API to extract basic metadata from permitted public HTML pages.", color = Muted, lineHeight = 19.sp)
                OutlinedTextField(
                    value = backend,
                    onValueChange = { backend = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Backend base URL") },
                    placeholder = { Text("http://10.0.2.2:8000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Public page URL") },
                    placeholder = { Text("https://example.com/product") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            result = try {
                                withContext(Dispatchers.IO) { callScrapeApi(backend.trim(), targetUrl.trim()) }
                            } catch (e: Exception) {
                                "Request failed: ${e.message ?: "check backend URL and connection"}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy)
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "Researching…" else "Extract public page")
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Text(result, modifier = Modifier.fillMaxWidth().padding(14.dp), color = Color(0xFFE0EAF8), lineHeight = 19.sp)
                }
                Spacer(Modifier.height(22.dp))
                Text("MarketMitra MVP • sample data is not a live ranking • respect site terms and robots.txt", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun MetricChip(value: String, label: String) {
    Column(
        modifier = Modifier.background(Navy, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(value, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ProductCard(idea: ProductIdea) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = Cyan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(idea.name, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
                    Text(idea.category, color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = Muted)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("SAMPLE PRICE BAND", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
                    Text(idea.price, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("HEURISTIC", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
                    Text("${idea.score}/100", fontWeight = FontWeight.Bold, color = Color(0xFF86EFAC))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(idea.signal, color = Muted, fontSize = 12.sp)
        }
    }
}

private fun callScrapeApi(baseUrl: String, pageUrl: String): String {
    require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) { "Enter a valid backend base URL first." }
    require(pageUrl.startsWith("https://") || pageUrl.startsWith("http://")) { "Enter a public http/https page URL." }
    val connection = URL(baseUrl.trimEnd('/') + "/api/v1/scrape").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use {
            it.write(JSONObject().put("url", pageUrl).toString().toByteArray(Charsets.UTF_8))
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        val json = JSONObject(body)
        if (code !in 200..299) {
            return "API error $code: " + json.optString("detail", "The request could not be completed.")
        }
        return buildString {
            append("Source: ").append(json.optString("source_url")).append("\n")
            append("Title: ").append(json.optString("title", "(not found)")).append("\n")
            append("Description: ").append(json.optString("description", "(not found)")).append("\n")
            val names = json.optJSONArray("product_name_candidates")
            if (names != null && names.length() > 0) append("Name candidates: ").append(names.toString()).append("\n")
            val prices = json.optJSONArray("price_candidates")
            if (prices != null && prices.length() > 0) append("Price candidates: ").append(prices.toString()).append("\n")
            append(json.optString("extraction_note"))
        }
    } finally {
        connection.disconnect()
    }
}
