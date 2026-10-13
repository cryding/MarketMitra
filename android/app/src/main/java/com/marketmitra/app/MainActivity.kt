package com.marketmitra.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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

private data class DemandSignalItem(val title: String, val source: String, val url: String, val summary: String, val publishedAt: String)
private data class DemandResearchLink(val id: String, val name: String, val category: String, val status: String, val url: String)
private data class DemandSignalResponse(val summary: String, val items: List<DemandSignalItem>, val researchLinks: List<DemandResearchLink>)

private data class ResearchItem(
    val name: String,
    val price: String,
    val source: String,
    val url: String,
    val relevance: Int
)

private data class OpportunityTip(val title: String, val reason: String)
private data class SupplierLink(val name: String, val type: String, val url: String)
private data class ResearchHubLink(val name: String, val category: String, val description: String, val url: String)

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
    var demandSignalSummary by remember { mutableStateOf("") }
    var demandSignalItems by remember { mutableStateOf<List<DemandSignalItem>>(emptyList()) }
    var demandResearchLinks by remember { mutableStateOf<List<DemandResearchLink>>(emptyList()) }
    var priceSummary by remember { mutableStateOf("") }
    var profitSummary by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var packagingCost by remember { mutableStateOf("") }
    var shippingCost by remember { mutableStateOf("") }
    var marketplaceFee by remember { mutableStateOf("") }
    var returnLoss by remember { mutableStateOf("") }
    var adsCost by remember { mutableStateOf("") }
    var startupBudget by remember { mutableStateOf("5000") }
    var monthlyFixedCosts by remember { mutableStateOf("1000") }
    var targetOrders by remember { mutableStateOf("30") }
    var sourcingLocation by remember { mutableStateOf("Itahar, West Bengal") }
    var competitionSummary by remember { mutableStateOf("") }
    var businessPlanSummary by remember { mutableStateOf("") }
    var supplierLinks by remember { mutableStateOf<List<SupplierLink>>(emptyList()) }
    var researchLinks by remember { mutableStateOf<List<ResearchHubLink>>(emptyList()) }
    var showBusinessToolkit by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Enter a product or category to start live research.") }
    var busy by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var operationType by remember { mutableStateOf("Product") }
    var operationTitle by remember { mutableStateOf("") }
    var operationQuantity by remember { mutableStateOf("1") }
    var operationAmount by remember { mutableStateOf("") }
    var operationNote by remember { mutableStateOf("") }
    var operationLinkedProduct by remember { mutableStateOf("") }
    var operationRecords by remember { mutableStateOf(loadOperationRecords(context)) }
    val backupOperationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val array = org.json.JSONArray()
                operationRecords.forEach { record ->
                    array.put(JSONObject()
                        .put("id", record.id)
                        .put("type", record.type)
                        .put("title", record.title)
                        .put("quantity", record.quantity)
                        .put("amountInr", record.amountInr)
                        .put("status", record.status)
                        .put("note", record.note)
                        .put("linkedProduct", record.linkedProduct))
                }
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Could not open the selected file.")
                output.bufferedWriter().use { it.write(array.toString(2)) }
                message = "Backed up ${operationRecords.size} business record(s) to JSON."
            }.onFailure { error -> message = "Backup failed: " + (error.message ?: "could not write file") }
        }
    }
    val restoreOperationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Could not read the selected backup.")
                val array = org.json.JSONArray(raw)
                val imported = (0 until array.length()).mapNotNull { index ->
                    val obj = array.optJSONObject(index) ?: return@mapNotNull null
                    val title = obj.optString("title").trim()
                    val type = obj.optString("type", "Task")
                    val quantity = obj.optDouble("quantity", 1.0)
                    val amount = obj.optDouble("amountInr", 0.0)
                    if (title.isBlank() || type !in setOf("Product", "Order", "Expense", "Task") || quantity <= 0 || amount < 0) {
                        return@mapNotNull null
                    }
                    OperationRecord(
                        id = obj.optLong("id", System.currentTimeMillis() + index),
                        type = type,
                        title = title,
                        quantity = quantity,
                        amountInr = amount,
                        status = obj.optString("status", if (type == "Order") "Open" else "Active"),
                        note = obj.optString("note"),
                        linkedProduct = obj.optString("linkedProduct")
                    )
                }
                if (imported.isEmpty()) throw IllegalStateException("No valid business records were found in this backup.")
                operationRecords = imported
                saveOperationRecords(context, imported)
                message = "Restored ${imported.size} business record(s). Current device records were replaced."
            }.onFailure { error -> message = "Restore failed: " + (error.message ?: "invalid backup file") }
        }
    }
    val exportOperationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csv = buildString {
                    appendLine("id,type,title,quantity,amount_inr,status,note,linked_product")
                    operationRecords.forEach { record ->
                        appendLine(listOf(
                            record.id.toString(), record.type, record.title, record.quantity.toString(),
                            record.amountInr.toString(), record.status, record.note, record.linkedProduct
                        ).joinToString(",") { csvCell(it) })
                    }
                }
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Could not open the selected file.")
                output.bufferedWriter().use { it.write(csv) }
                message = "Exported ${operationRecords.size} business record(s) to CSV."
            }.onFailure { error ->
                message = "Export failed: " + (error.message ?: "could not write file")
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Page) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF14213D), Color(0xFF2457C5), Color(0xFF16794B))),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)).padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("MarketMitra", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(3.dp))
                            Text("BUSINESS INTELLIGENCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD7E6FF))
                            Text("Research · Plan · Launch · Grow", fontSize = 12.sp, color = Color.White.copy(alpha = 0.88f))
                        }
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
                        Text("Phone-only research: enter a product name and MarketMitra checks accessible public marketplace pages, compares visible prices and suggests next steps. No terminal, extra files or API setup needed.", color = Muted, lineHeight = 20.sp)
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
                        Spacer(Modifier.height(12.dp))
                        Text("Business launch plan", fontWeight = FontWeight.SemiBold, color = Ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = startupBudget, onValueChange = { startupBudget = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Start budget ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp))
                            OutlinedTextField(value = monthlyFixedCosts, onValueChange = { monthlyFixedCosts = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Monthly fixed cost ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = targetOrders, onValueChange = { targetOrders = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Monthly orders target") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(10.dp))
                            OutlinedTextField(value = sourcingLocation, onValueChange = { sourcingLocation = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Supplier location") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), shape = RoundedCornerShape(10.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    hasSearched = true
                                    itemsFound = emptyList()
                                    tips = emptyList()
                                    analysisSummary = ""
                                    demandSignalSummary = ""
                                    demandSignalItems = emptyList()
                                    priceSummary = ""
                                    profitSummary = ""
                                    competitionSummary = ""
                                    businessPlanSummary = ""
                                    supplierLinks = emptyList()
                                    researchLinks = emptyList()
                                    message = "Researching market, competitors, suppliers and business plan…"
                                    try {
                                        val response = withContext(Dispatchers.IO) {
                                            fetchEmpireWorkspace(query.trim(), unitCost, sellingPrice, packagingCost, shippingCost, marketplaceFee, returnLoss, adsCost, startupBudget, monthlyFixedCosts, targetOrders, sourcingLocation)
                                        }
                                        val demandSignals = withContext(Dispatchers.IO) {
                                            runCatching { fetchDemandSignals(query.trim()) }.getOrNull()
                                        }
                                        demandSignalSummary = demandSignals?.summary ?: "Independent trend/news feeds were unavailable this time. This does not mean demand is absent."
                                        demandSignalItems = demandSignals?.items ?: emptyList()
                                        demandResearchLinks = demandSignals?.researchLinks ?: emptyList()
                                        itemsFound = response.items
                                        tips = response.tips
                                        sourceSummary = response.sourceSummary
                                        analysisSummary = response.analysisSummary
                                        priceSummary = response.priceSummary
                                        profitSummary = response.profitSummary
                                        competitionSummary = response.competitionSummary
                                        businessPlanSummary = response.businessPlanSummary
                                        supplierLinks = response.supplierLinks
                                        researchLinks = response.researchLinks
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

            if (demandSignalSummary.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("CROSS-SOURCE DEMAND SIGNALS", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                                            Text(demandSignalSummary, color = Ink, lineHeight = 19.sp)
                            if (demandSignalItems.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                demandSignalItems.forEach { signal ->
                                    Divider(color = Border, modifier = Modifier.padding(vertical = 7.dp))
                                    Column(Modifier.fillMaxWidth().clickable { openSource(context, signal.url) }) {
                                        Text(signal.title, color = Ink, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
                                        Text(signal.source + if (signal.publishedAt.isNotBlank()) " · " + signal.publishedAt else "", color = Muted, fontSize = 11.sp)
                                        if (signal.summary.isNotBlank()) Text(signal.summary, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                                        Text("Open original source ↗", color = Blue, fontSize = 12.sp)
                                    }
                                }
                            }
                            val sourceLinks = demandResearchLinks.sortedBy { link ->
                                when (link.category) {
                                    "official_data" -> 0
                                    "trend_news" -> 1
                                    "marketplace" -> 2
                                    "supplier_discovery" -> 3
                                    else -> 4
                                }
                            }.sortedBy { link ->
                                when (link.category) {
                                    "official_data" -> 0
                                    "trend_news" -> 1
                                    "marketplace" -> 2
                                    "supplier_discovery" -> 3
                                    else -> 4
                                }
                            }
                            if (sourceLinks.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("SOURCE REGISTRY", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                sourceLinks.take(8).forEach { link ->
                                    Text(link.name + " · " + link.category.replace("_", " "), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(link.status.replace("_", " ") + " · Open research source ↗", color = Blue, fontSize = 11.sp,
                                        modifier = Modifier.clickable { openSource(context, link.url) })
                                }
                            }
                        }
                    }
                }
            }

            if (analysisSummary.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("MARKET INTELLIGENCE", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(analysisSummary, color = Ink, lineHeight = 20.sp)
                            if (priceSummary.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Divider(color = Border)
                                Spacer(Modifier.height(8.dp))
                                Text("Competitor price range", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(priceSummary, color = Muted, lineHeight = 19.sp)
                            }
                            if (competitionSummary.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Divider(color = Border)
                                Spacer(Modifier.height(8.dp))
                                Text("Competition analysis", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(competitionSummary, color = Muted, lineHeight = 19.sp)
                            }
                            if (profitSummary.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Divider(color = Border)
                                Spacer(Modifier.height(8.dp))
                                Text("Profit / loss scenario", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(profitSummary, color = Green, lineHeight = 19.sp)
                            }
                            if (businessPlanSummary.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Divider(color = Border)
                                Spacer(Modifier.height(8.dp))
                                Text("Business launch plan", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(businessPlanSummary, color = Ink, lineHeight = 19.sp)
                            }
                        }
                    }
                }
            }

            if (supplierLinks.isNotEmpty()) {
                item { Text("Supplier discovery", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
                items(supplierLinks) { supplier ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { openSource(context, supplier.url) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Blue)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(supplier.name, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(supplier.type, color = Muted, fontSize = 12.sp)
                            }
                            Text("Open ↗", color = Blue, fontSize = 12.sp)
                        }
                    }
                }
                item {
                    Text("Supplier directory links are discovery tools, not verified supplier endorsements. Check GST/invoice, samples, MOQ, delivery and replacement terms.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }

            if (researchLinks.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("BUSINESS TOOLKIT", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text("From idea validation to launch", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Spacer(Modifier.height(5.dp))
                            Text("Free research entry points, supplier discovery and official setup portals. External links are not scraped evidence or endorsements.", color = Muted, lineHeight = 19.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { showBusinessToolkit = !showBusinessToolkit },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (showBusinessToolkit) "Hide business toolkit" else "Explore " + researchLinks.size + " business tools")
                            }
                            if (showBusinessToolkit) {
                                researchLinks.groupBy { it.category }.forEach { (category, links) ->
                                    Spacer(Modifier.height(12.dp))
                                    Text(category.uppercase(), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    links.forEach { link ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { openSource(context, link.url) }.padding(vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(link.name, color = Ink, fontWeight = FontWeight.SemiBold)
                                                Text(link.description, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text("Open ↗", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Divider(color = Border)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                val stockUnits = operationRecords.filter { it.type == "Product" && it.status == "Active" }.sumOf { it.quantity }
                val openOrders = operationRecords.count { it.type == "Order" && it.status == "Open" }
                val completedRevenue = operationRecords.filter { it.type == "Order" && it.status == "Completed" }.sumOf { it.amountInr }
                val returnedOrders = operationRecords.count { it.type == "Order" && it.status == "Returned" }
                val returnedValue = operationRecords.filter { it.type == "Order" && it.status == "Returned" }.sumOf { it.amountInr }
                val recordedExpenses = operationRecords.filter { it.type == "Expense" }.sumOf { it.amountInr }
                val completedCogs = operationRecords.filter { it.type == "Order" && it.status == "Completed" }.sumOf { order ->
                    val product = operationRecords.firstOrNull { it.type == "Product" && it.title.equals(order.linkedProduct.trim(), ignoreCase = true) }
                    (product?.amountInr ?: 0.0) * order.quantity
                }
                val estimatedResult = completedRevenue - recordedExpenses - completedCogs
                val lowStockCount = operationRecords.count { it.type == "Product" && it.status == "Active" && it.quantity <= 3 }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("BUSINESS OPERATIONS", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("Your offline business desk", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Track products/stock, orders, expenses and tasks. Records are saved on this device; they are not synced to the cloud.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f).background(Page, RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text("ACTIVE STOCK", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(stockUnits.toString().removeSuffix(".0"), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f).background(Page, RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text("OPEN ORDERS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(openOrders.toString(), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f).background(Page, RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text("COMPLETED SALES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("₹" + completedRevenue.toInt().toString(), color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f).background(Page, RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text("RECORDED EXPENSES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("₹" + recordedExpenses.toInt().toString(), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f).background(Page, RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text("RETURNS / REFUNDS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(returnedOrders.toString(), color = if (returnedOrders > 0) Color(0xFFB54708) else Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("₹" + returnedValue.toInt().toString() + " excluded from sales estimate", color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f).background(Page, RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text("ESTIMATED RESULT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("₹" + estimatedResult.toInt().toString(), color = if (estimatedResult >= 0) Green else Color(0xFFB42318), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Sales − recorded expenses − linked product cost; excludes unrecorded fees/tax.", color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
                            }
                            Column(Modifier.weight(1f).background(Page, RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text("LOW STOCK", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(lowStockCount.toString(), color = if (lowStockCount > 0) Color(0xFFB54708) else Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Active products with 3 units or fewer", color = Muted, fontSize = 10.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { backupOperationsLauncher.launch("marketmitra-backup.json") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Backup JSON") }
                            OutlinedButton(
                                onClick = { restoreOperationsLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Restore JSON") }
                        }
                        OutlinedButton(
                            onClick = { exportOperationsLauncher.launch("marketmitra-operations.csv") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Export business records to CSV") }
                        Spacer(Modifier.height(14.dp))
                        Text("Add a record", fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Product", "Order", "Expense", "Task").forEach { type ->
                                if (operationType == type) {
                                    Button(
                                        onClick = { operationType = type },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text(type, fontSize = 11.sp) }
                                } else {
                                    OutlinedButton(
                                        onClick = { operationType = type },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text(type, fontSize = 11.sp) }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = operationTitle,
                            onValueChange = { operationTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(when (operationType) { "Product" -> "Product name / SKU"; "Order" -> "Order reference / customer"; "Expense" -> "Expense category"; else -> "Task title" }) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = operationQuantity,
                                onValueChange = { operationQuantity = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text(if (operationType == "Product") "Stock units" else if (operationType == "Order") "Order quantity" else "Count") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = operationAmount,
                                onValueChange = { operationAmount = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text(if (operationType == "Product") "Unit cost ₹" else if (operationType == "Order") "Order total ₹" else if (operationType == "Expense") "Expense ₹" else "Optional value ₹") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        if (operationType == "Order") {
                            OutlinedTextField(
                                value = operationLinkedProduct,
                                onValueChange = { operationLinkedProduct = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Link to exact Product name / SKU") },
                                placeholder = { Text("Add the same name used in Product records") },
                                shape = RoundedCornerShape(10.dp)
                            )
                            Text("Completing an order will deduct its quantity from this product stock. A matching product and sufficient stock are required.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        OutlinedTextField(
                            value = operationNote,
                            onValueChange = { operationNote = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Notes (optional)") },
                            shape = RoundedCornerShape(10.dp),
                            minLines = 1,
                            maxLines = 3
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val title = operationTitle.trim()
                                val quantity = operationQuantity.toDoubleOrNull() ?: 1.0
                                val amount = operationAmount.toDoubleOrNull() ?: 0.0
                                if (title.isBlank() || quantity <= 0 || amount < 0) {
                                    message = "Enter a name, positive quantity and non-negative amount to save the record."
                                } else {
                                    val status = when (operationType) {
                                        "Order" -> "Open"
                                        "Task" -> "To do"
                                        "Expense" -> "Recorded"
                                        else -> "Active"
                                    }
                                    val record = OperationRecord(
                                        id = System.currentTimeMillis(),
                                        type = operationType,
                                        title = title,
                                        quantity = quantity,
                                        amountInr = amount,
                                        status = status,
                                        note = operationNote.trim(),
                                        linkedProduct = operationLinkedProduct.trim()
                                    )
                                    val updated = listOf(record) + operationRecords
                                    operationRecords = updated
                                    saveOperationRecords(context, updated)
                                    operationTitle = ""
                                    operationQuantity = "1"
                                    operationAmount = ""
                                    operationNote = ""
                                    operationLinkedProduct = ""
                                    message = "$operationType record saved on this device."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Save " + operationType.lowercase()) }
                        if (operationRecords.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text("RECENT RECORDS · " + operationRecords.size, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            operationRecords.take(30).forEach { record ->
                                Divider(color = Border, modifier = Modifier.padding(vertical = 7.dp))
                                Row(verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f)) {
                                        Text(record.title, color = Ink, fontWeight = FontWeight.SemiBold)
                                        Text(record.type + " · " + record.status + " · Qty " + record.quantity.toString().removeSuffix(".0"), color = Muted, fontSize = 11.sp)
                                        if (record.amountInr > 0) Text("₹" + record.amountInr.toString().removeSuffix(".0"), color = Green, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        if (record.note.isNotBlank()) Text(record.note, color = Muted, fontSize = 12.sp)
                                    }
                                    if (record.type != "Expense") {
                                        OutlinedButton(
                                            onClick = {
                                                if (record.type == "Order") {
                                                    val linkedProduct = operationRecords.firstOrNull {
                                                        it.type == "Product" && it.title.equals(record.linkedProduct.trim(), ignoreCase = true)
                                                    }
                                                    if (record.status == "Completed") {
                                                        if (record.linkedProduct.isBlank() || linkedProduct == null) {
                                                            message = "Return not recorded: add or restore the matching Product record first so returned stock can be tracked."
                                                        } else {
                                                            val updated = operationRecords.map {
                                                                when (it.id) {
                                                                    record.id -> it.copy(status = "Returned")
                                                                    linkedProduct.id -> it.copy(quantity = it.quantity + record.quantity)
                                                                    else -> it
                                                                }
                                                            }
                                                            operationRecords = updated
                                                            saveOperationRecords(context, updated)
                                                            message = "Return recorded; stock restored. The full order total is excluded from the sales estimate."
                                                        }
                                                    } else if (record.status == "Open" || record.status == "Returned") {
                                                        if (record.linkedProduct.isBlank() || linkedProduct == null) {
                                                            message = "Order not completed: add a matching Product record and exact product name/SKU first."
                                                        } else if (linkedProduct.quantity < record.quantity) {
                                                            message = "Order not completed: insufficient stock for " + linkedProduct.title + ". Available " + linkedProduct.quantity + "."
                                                        } else {
                                                            val updated = operationRecords.map {
                                                                when (it.id) {
                                                                    record.id -> it.copy(status = "Completed")
                                                                    linkedProduct.id -> it.copy(quantity = it.quantity - record.quantity)
                                                                    else -> it
                                                                }
                                                            }
                                                            operationRecords = updated
                                                            saveOperationRecords(context, updated)
                                                            message = if (record.status == "Returned") "Order sold again; stock deducted for " + linkedProduct.title + "." else "Order completed and stock deducted for " + linkedProduct.title + "."
                                                        }
                                                    }
                                                } else {
                                                    val nextStatus = when (record.type) {
                                                        "Task" -> if (record.status == "To do") "Done" else "To do"
                                                        else -> if (record.status == "Active") "Archived" else "Active"
                                                    }
                                                    val updated = operationRecords.map { if (it.id == record.id) it.copy(status = nextStatus) else it }
                                                    operationRecords = updated
                                                    saveOperationRecords(context, updated)
                                                }
                                            },
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) { Text(if (record.type == "Order") when (record.status) { "Open" -> "Complete order"; "Completed" -> "Record return"; "Returned" -> "Sell again"; else -> "Order status" } else if (record.type == "Task") "Done / undo" else "Archive", fontSize = 10.sp) }
                                    }
                                    IconButton(onClick = {
                                        val updated = operationRecords.filterNot { it.id == record.id }
                                        operationRecords = updated
                                        saveOperationRecords(context, updated)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete record", tint = Muted)
                                    }
                                }
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

private data class OperationRecord(
    val id: Long,
    val type: String,
    val title: String,
    val quantity: Double,
    val amountInr: Double,
    val status: String,
    val note: String,
    val linkedProduct: String = ""
)

private fun loadOperationRecords(context: Context): List<OperationRecord> {
    return runCatching {
        val raw = context.getSharedPreferences("marketmitra_operations", Context.MODE_PRIVATE)
            .getString("records_json", "[]") ?: "[]"
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            OperationRecord(
                id = obj.optLong("id"),
                type = obj.optString("type", "Task"),
                title = obj.optString("title"),
                quantity = obj.optDouble("quantity", 1.0),
                amountInr = obj.optDouble("amountInr", 0.0),
                status = obj.optString("status", "Active"),
                note = obj.optString("note"),
                linkedProduct = obj.optString("linkedProduct")
            ).takeIf { it.title.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}

private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

private fun saveOperationRecords(context: Context, records: List<OperationRecord>) {
    val array = org.json.JSONArray()
    records.forEach { record ->
        array.put(JSONObject()
            .put("id", record.id)
            .put("type", record.type)
            .put("title", record.title)
            .put("quantity", record.quantity)
            .put("amountInr", record.amountInr)
            .put("status", record.status)
            .put("note", record.note)
            .put("linkedProduct", record.linkedProduct))
    }
    context.getSharedPreferences("marketmitra_operations", Context.MODE_PRIVATE)
        .edit().putString("records_json", array.toString()).apply()
}

private data class LiveResponse(
    val items: List<ResearchItem>,
    val tips: List<OpportunityTip>,
    val sourceSummary: String,
    val analysisSummary: String,
    val priceSummary: String,
    val profitSummary: String,
    val competitionSummary: String,
    val businessPlanSummary: String,
    val supplierLinks: List<SupplierLink>,
    val researchLinks: List<ResearchHubLink>
)

private fun fetchEmpireWorkspace(
    query: String,
    unitCost: String,
    sellingPrice: String,
    packagingCost: String,
    shippingCost: String,
    marketplaceFee: String,
    returnLoss: String,
    adsCost: String,
    startupBudget: String,
    monthlyFixedCosts: String,
    targetOrders: String,
    location: String
): LiveResponse {
    require(query.length >= 2) { "Please enter at least two characters." }
    val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
    require(baseUrl.startsWith("https://")) {
        "Live research service is not connected yet. The backend must be deployed once; you do not need to enter an API URL in the app."
    }
    val url = URL(baseUrl + "/api/v1/empire/workspace")
    val payload = JSONObject()
        .put("q", query)
        .put("packaging_cost_inr", packagingCost.toDoubleOrNull() ?: 0.0)
        .put("shipping_cost_inr", shippingCost.toDoubleOrNull() ?: 0.0)
        .put("marketplace_fee_pct", marketplaceFee.toDoubleOrNull() ?: 0.0)
        .put("return_loss_pct", returnLoss.toDoubleOrNull() ?: 0.0)
        .put("ads_cost_per_order_inr", adsCost.toDoubleOrNull() ?: 0.0)
        .put("startup_budget_inr", startupBudget.toDoubleOrNull() ?: 5000.0)
        .put("monthly_fixed_costs_inr", monthlyFixedCosts.toDoubleOrNull() ?: 0.0)
        .put("target_monthly_orders", targetOrders.toIntOrNull() ?: 30)
        .put("location", location.ifBlank { "India" })
        .put("sourcing_type", "wholesale")
    unitCost.toDoubleOrNull()?.let { payload.put("unit_cost_inr", it) }
    sellingPrice.toDoubleOrNull()?.let { payload.put("selling_price_inr", it) }

    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 12_000
    connection.readTimeout = 35_000
    connection.doOutput = true
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    connection.setRequestProperty("User-Agent", "MarketMitra-Android/0.5")
    try {
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        val json = JSONObject(body)
        if (code !in 200..299) throw IllegalStateException(json.optString("detail", "Research request failed (HTTP $code)."))

        val analysis = json.optJSONObject("market_analysis") ?: JSONObject()
        val found = mutableListOf<ResearchItem>()
        val array = analysis.optJSONArray("items")
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
        val recommendationArray = analysis.optJSONArray("recommendations")
        if (recommendationArray != null) {
            for (index in 0 until recommendationArray.length()) {
                val obj = recommendationArray.optJSONObject(index) ?: continue
                recommendations.add(OpportunityTip(
                    title = obj.optString("priority", "medium").uppercase() + " PRIORITY · " + obj.optString("action"),
                    reason = obj.optString("why")
                ))
            }
        }
        val actions = json.optJSONArray("next_actions")
        if (actions != null) {
            for (index in 0 until actions.length()) {
                val obj = actions.optJSONObject(index) ?: continue
                recommendations.add(OpportunityTip("NEXT STEP " + obj.optString("priority") + " · " + obj.optString("action"), "Action from the business workspace."))
            }
        }

        val snapshot = analysis.optJSONObject("market_snapshot") ?: JSONObject()
        val sourceStatuses = mutableListOf<String>()
        val sourceArray = snapshot.optJSONArray("source_status")
        if (sourceArray != null) {
            for (index in 0 until sourceArray.length()) {
                val obj = sourceArray.optJSONObject(index) ?: continue
                sourceStatuses.add(obj.optString("source") + ": " + obj.optString("status").replace('_', ' '))
            }
        }
        val fetchedAt = analysis.optString("fetched_at_utc")
        val sourceSummary = sourceStatuses.joinToString(" • ") +
            if (fetchedAt.isNotBlank()) "\nFetched: " + fetchedAt + " UTC" else ""
        val analysisSummary = "Evidence score: " + analysis.optInt("evidence_score") + "/100 · Confidence: " +
            analysis.optString("confidence", "low") + "\n" +
            analysis.optString("score_meaning", "Evidence coverage only; not a demand forecast.")
        val priceObject = snapshot.optJSONObject("price_summary")
        val priceSummary = if (priceObject != null) {
            "Low ₹" + priceObject.optDouble("min") + ", median ₹" + priceObject.optDouble("median") +
                ", high ₹" + priceObject.optDouble("max") + ". Price coverage: " +
                priceObject.optDouble("coverage_pct") + "%."
        } else "No reliable public prices were extracted; verify listings manually."

        val economics = analysis.optJSONObject("unit_economics")
        val profitSummary = if (economics == null) "" else if (economics.optString("status") == "needs_selling_price") {
            economics.optString("note")
        } else {
            "Estimated profit/order: ₹" + economics.optDouble("estimated_net_profit_per_order") +
                " · Margin: " + economics.optDouble("estimated_net_margin_pct") +
                "% · Break-even selling price: ₹" + economics.optDouble("break_even_selling_price") +
                ". " + economics.optString("note")
        }

        val competition = json.optJSONObject("competition") ?: JSONObject()
        val competitionSummary = competition.optString("level", "Not enough evidence") + "\n" +
            competition.optString("note", "Competition could not be assessed.") + "\n" +
            "Accessible listings: " + competition.optInt("listing_count_signal") +
            " · Sources with results: " + competition.optInt("sources_with_results")

        val plan = json.optJSONObject("business_plan") ?: JSONObject()
        val scenario = plan.optJSONObject("scenario")
        val allocation = plan.optJSONArray("recommended_test_budget_allocation")
        val allocationLines = mutableListOf<String>()
        if (allocation != null) {
            for (index in 0 until allocation.length()) {
                val obj = allocation.optJSONObject(index) ?: continue
                allocationLines.add(obj.optString("category") + ": ₹" + obj.optDouble("amount_inr") + " (" + obj.optInt("pct") + "%)")
            }
        }
        val businessPlanSummary = buildString {
            append("Starting budget: ₹").append(plan.optDouble("startup_budget_inr"))
            append("\nIllustrative test-budget allocation:\n").append(allocationLines.joinToString("\n"))
            if (scenario != null) {
                append("\n\nEstimated monthly result after fixed costs: ₹")
                    .append(scenario.optDouble("estimated_monthly_result_after_fixed_costs_inr"))
                append("\nTarget orders: ").append(scenario.optInt("target_monthly_orders"))
                val breakEvenOrders = scenario.opt("break_even_orders_per_month")
                if (breakEvenOrders != null && breakEvenOrders != JSONObject.NULL) {
                    append("\nBreak-even orders/month: ").append(breakEvenOrders.toString())
                }
            }
            append("\n\n").append(plan.optString("note"))
        }

        val supplierLinks = mutableListOf<SupplierLink>()
        val supplierDiscovery = json.optJSONObject("supplier_discovery") ?: JSONObject()
        val links = supplierDiscovery.optJSONArray("links")
        if (links != null) {
            for (index in 0 until links.length()) {
                val obj = links.optJSONObject(index) ?: continue
                supplierLinks.add(SupplierLink(obj.optString("name"), obj.optString("type"), obj.optString("url")))
            }
        }

        val researchLinks = mutableListOf<ResearchHubLink>()
        val researchHub = json.optJSONObject("research_hub") ?: JSONObject()
        val researchArray = researchHub.optJSONArray("links")
        if (researchArray != null) {
            for (index in 0 until researchArray.length()) {
                val obj = researchArray.optJSONObject(index) ?: continue
                researchLinks.add(ResearchHubLink(
                    name = obj.optString("name"),
                    category = obj.optString("category", "Research"),
                    description = obj.optString("description"),
                    url = obj.optString("url")
                ))
            }
        }
        return LiveResponse(found, recommendations, sourceSummary, analysisSummary, priceSummary, profitSummary, competitionSummary, businessPlanSummary, supplierLinks, researchLinks)
    } finally {
        connection.disconnect()
    }
}

private fun fetchDemandSignals(query: String): DemandSignalResponse {
    val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val connection = (URL("$baseUrl/api/v1/research/signals?q=$encodedQuery").openConnection() as HttpURLConnection)
    connection.requestMethod = "GET"
    connection.connectTimeout = 5_000
    connection.readTimeout = 10_000
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty("User-Agent", "MarketMitra-Android/0.6")
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        val json = JSONObject(body)
        if (code !in 200..299) throw IllegalStateException(json.optString("detail", "Demand signal request failed (HTTP $code)."))
        val score = json.optInt("coverage_score")
        val summaryObject = json.optJSONObject("source_summary") ?: JSONObject()
        val sourceArray = json.optJSONArray("sources")
        val sourceLines = mutableListOf<String>()
        if (sourceArray != null) {
            for (index in 0 until sourceArray.length()) {
                val source = sourceArray.optJSONObject(index) ?: continue
                sourceLines.add(source.optString("source") + ": " + source.optString("status") + " (" + source.optInt("count") + ")")
            }
        }
        val summary = "Cross-Source Evidence Lens · Coverage $score/100 (feed/evidence coverage only, not sales demand). " +
            summaryObject.optInt("with_matching_items") + " source(s) returned matching items.\n" + sourceLines.joinToString(" · ")
        val items = mutableListOf<DemandSignalItem>()
        val array = json.optJSONArray("items")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (!url.startsWith("https://")) continue
                items.add(DemandSignalItem(
                    title = item.optString("title"),
                    source = item.optString("source"),
                    url = url,
                    summary = item.optString("summary"),
                    publishedAt = item.optString("published_at")
                ))
            }
        }
        val researchLinks = mutableListOf<DemandResearchLink>()
        val linksArray = json.optJSONArray("research_links")
        if (linksArray != null) {
            for (index in 0 until linksArray.length()) {
                val link = linksArray.optJSONObject(index) ?: continue
                val url = link.optString("url")
                if (!url.startsWith("https://")) continue
                researchLinks.add(DemandResearchLink(
                    id = link.optString("id"),
                    name = link.optString("name"),
                    category = link.optString("category"),
                    status = link.optString("integration_status"),
                    url = url
                ))
            }
        }
        return DemandSignalResponse(summary, items, researchLinks)
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
