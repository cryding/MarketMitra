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
    var analysisSummary by remember { mutableStateOf("") }
    var priceSummary by remember { mutableStateOf("") }
    var profitSummary by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var packagingCost by remember { mutableStateOf("") }
    var shippingCost by remember { mutableStateOf("") }
    var marketplaceFee by remember { mutableStateOf("") }
    var returnLoss by remember { mutableStateOf("") }
    var adsCost by remember { mutableStateOf("") }
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
                        Spacer(Modifier.height(14.dp))
                        Text("Optional profit estimate", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("Add your own costs. Leave blank if unknown; estimates are not guarantees.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = unitCost, onValueChange = { unitCost = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Product cost ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp))
                            OutlinedTextField(value = sellingPrice, onValueChange = { sellingPrice = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Selling price ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = packagingCost, onValueChange = { packagingCost = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Packaging ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp))
                            OutlinedTextField(value = shippingCost, onValueChange = { shippingCost = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Shipping ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = marketplaceFee, onValueChange = { marketplaceFee = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Marketplace fee %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp))
                            OutlinedTextField(value = returnLoss, onValueChange = { returnLoss = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Return loss %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp))
                        }
                        OutlinedTextField(value = adsCost, onValueChange = { adsCost = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Ads cost per order ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp))
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    hasSearched = true
                                    itemsFound = emptyList()
                                    tips = emptyList()
                                    analysisSummary = ""
                                    priceSummary = ""
                                    profitSummary = ""
                                    message = "Researching listings and analysing business signals…"
                                    try {
                                        val response = withContext(Dispatchers.IO) {
                                            fetchMarketAnalysis(query.trim(), unitCost, sellingPrice, packagingCost, shippingCost, marketplaceFee, returnLoss, adsCost)
                                        }
                                        itemsFound = response.items
                                        tips = response.tips
                                        sourceSummary = response.sourceSummary
                                        analysisSummary = response.analysisSummary
                                        priceSummary = response.priceSummary
                                        profitSummary = response.profitSummary
                                        message = if (response.items.isEmpty()) {
                                            "No accessible listings returned this time. This does not prove there is no demand."
                                        } else {
                                            "Analysed ${response.items.size} public listing(s). Verify source details before spending money."
                                        }
                                    } catch (e: Exception) {
                                        message = e.message ?: "Could not complete research. Please try again later."
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

            if (analysisSummary.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("MARKET ANALYSIS", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(analysisSummary, color = Ink, lineHeight = 20.sp)
                            if (priceSummary.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Divider(color = Border)
                                Spacer(Modifier.height(8.dp))
                                Text("Observed public prices", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(priceSummary, color = Muted, lineHeight = 19.sp)
                            }
                            if (profitSummary.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Divider(color = Border)
                                Spacer(Modifier.height(8.dp))
                                Text("Unit economics", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(profitSummary, color = Green, lineHeight = 19.sp)
                            }
                        }
                    }
                }
            }

            if (tips.isNotEmpty()) {
                item {
                    Text("Recommended next steps", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
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
    val sourceSummary: String,
    val analysisSummary: String,
    val priceSummary: String,
    val profitSummary: String
)

private fun fetchMarketAnalysis(
    query: String,
    unitCost: String,
    sellingPrice: String,
    packagingCost: String,
    shippingCost: String,
    marketplaceFee: String,
    returnLoss: String,
    adsCost: String
): LiveResponse {
    require(query.length >= 2) { "Please enter at least two characters." }
    val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
    require(baseUrl.startsWith("https://")) {
        "Live research service is not connected yet. The backend must be deployed once; you do not need to enter an API URL in the app."
    }
    val url = URL(baseUrl + "/api/v1/research/analyze")
    val payload = JSONObject()
        .put("q", query)
        .put("packaging_cost_inr", packagingCost.toDoubleOrNull() ?: 0.0)
        .put("shipping_cost_inr", shippingCost.toDoubleOrNull() ?: 0.0)
        .put("marketplace_fee_pct", marketplaceFee.toDoubleOrNull() ?: 0.0)
        .put("return_loss_pct", returnLoss.toDoubleOrNull() ?: 0.0)
        .put("ads_cost_per_order_inr", adsCost.toDoubleOrNull() ?: 0.0)
    unitCost.toDoubleOrNull()?.let { payload.put("unit_cost_inr", it) }
    sellingPrice.toDoubleOrNull()?.let { payload.put("selling_price_inr", it) }

    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 12_000
    connection.readTimeout = 30_000
    connection.doOutput = true
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    connection.setRequestProperty("User-Agent", "MarketMitra-Android/0.2")
    try {
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
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
                found.add(ResearchItem(
                    name = obj.optString("name"),
                    price = obj.optString("price"),
                    source = obj.optString("source"),
                    url = obj.optString("url"),
                    relevance = obj.optInt("relevance")
                ))
            }
        }

        val recommendations = mutableListOf<OpportunityTip>()
        val recommendationArray = json.optJSONArray("recommendations")
        if (recommendationArray != null) {
            for (index in 0 until recommendationArray.length()) {
                val obj = recommendationArray.optJSONObject(index) ?: continue
                val priority = obj.optString("priority", "medium").uppercase()
                recommendations.add(OpportunityTip(
                    title = priority + " PRIORITY · " + obj.optString("action"),
                    reason = obj.optString("why")
                ))
            }
        }

        val snapshot = json.optJSONObject("market_snapshot") ?: JSONObject()
        val sourceStatuses = mutableListOf<String>()
        val sourceArray = snapshot.optJSONArray("source_status")
        if (sourceArray != null) {
            for (index in 0 until sourceArray.length()) {
                val obj = sourceArray.optJSONObject(index) ?: continue
                sourceStatuses.add(obj.optString("source") + ": " + obj.optString("status").replace('_', ' '))
            }
        }
        val fetchedAt = json.optString("fetched_at_utc")
        val sourceSummary = sourceStatuses.joinToString(" • ") +
            if (fetchedAt.isNotBlank()) "\nFetched: " + fetchedAt + " UTC" else ""

        val score = json.optInt("evidence_score")
        val confidence = json.optString("confidence", "low")
        val analysisSummary = "Evidence score: " + score + "/100 · Confidence: " + confidence + "\n" +
            json.optString("score_meaning", "Evidence coverage only; not a demand forecast.")
        val priceObject = snapshot.optJSONObject("price_summary")
        val priceSummary = if (priceObject != null) {
            "Low ₹" + priceObject.optDouble("min") + ", median ₹" + priceObject.optDouble("median") +
                ", high ₹" + priceObject.optDouble("max") + ". Readable price coverage: " +
                priceObject.optDouble("coverage_pct") + "% (" + priceObject.optInt("priced_listing_count") +
                "/" + priceObject.optInt("listing_count") + " listings)."
        } else {
            "No reliable public prices were extracted for this search."
        }
        val economics = json.optJSONObject("unit_economics")
        val profitSummary = if (economics == null) {
            ""
        } else if (economics.optString("status") == "needs_selling_price") {
            economics.optString("note")
        } else {
            "Estimated profit/order: ₹" + economics.optDouble("estimated_net_profit_per_order") +
                " · Estimated margin: " + economics.optDouble("estimated_net_margin_pct") +
                "% · Break-even selling price: ₹" + economics.optDouble("break_even_selling_price") + ". " +
                economics.optString("note")
        }
        return LiveResponse(found, recommendations, sourceSummary, analysisSummary, priceSummary, profitSummary)
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
