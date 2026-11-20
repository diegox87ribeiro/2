package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.importexport.SpreadsheetImporter
import com.example.domain.model.OperationType
import com.example.domain.model.ProfitChartExecution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class ProfitChartParseOutcome(
    val executions: List<ProfitChartExecution>,
    val skippedNonExecutedCount: Int
)

object ProfitChartParsers {

    fun parsePayload(rawText: String, sourceChannel: String): ProfitChartParseOutcome {
        val trimmed = rawText.replace("\uFEFF", "").trim()
        if (trimmed.isEmpty()) return ProfitChartParseOutcome(emptyList(), 0)

        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJsonPayload(trimmed, sourceChannel)
        } else {
            parseCsvPayload(trimmed, sourceChannel)
        }
    }

    fun parseJsonPayload(jsonText: String, sourceChannel: String): ProfitChartParseOutcome {
        val trimmed = jsonText.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("executions")
                    ?: root.optJSONArray("orders")
                    ?: root.optJSONArray("operacoes")
                    ?: root.optJSONArray("trades")
                    ?: root.optJSONArray("negocios")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("items")
                    ?: JSONArray().apply {
                        if (root.has("ticker") || root.has("symbol") || root.has("ativo") || root.has("papel")) {
                            put(root)
                        }
                    }
            }
            else -> return ProfitChartParseOutcome(emptyList(), 0)
        }

        val executions = mutableListOf<ProfitChartExecution>()
        var skippedNonExecuted = 0

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val statusRaw = optAnyString(obj, "status", "orderStatus", "situacao", "estado")
            if (!isExecutedStatus(statusRaw)) {
                skippedNonExecuted++
                continue
            }

            val tickerRaw = optAnyString(obj, "ticker", "symbol", "asset", "ativo", "papel", "instrument")
            val ticker = LiveQuoteParsers.normalizeTicker(tickerRaw)
            if (ticker.length < 4) continue

            val sideRaw = optAnyString(obj, "side", "operation", "operacao", "operationType", "tipo", "lado", "c_v")
            val opType = parseOperationType(sideRaw) ?: continue

            val qty = optAnyDouble(obj, "executedQty", "tradedQty", "qtdExecutada", "qty", "quantity", "qtd", "quantidade")
                ?: continue
            val price = optAnyDouble(obj, "avgPrice", "executedPrice", "precoMedio", "precoExecucao", "price", "preco", "unitPrice")
                ?: continue
            if (qty <= 0.0 || price <= 0.0) continue

            val dateRaw = optAnyString(obj, "executedAt", "executionTime", "dataHora", "date", "timestamp", "createdAt", "atualizacao")
                .ifBlank { currentBrTimestamp() }
            val broker = optAnyString(obj, "broker", "corretora", "account", "conta")
            val orderId = optAnyString(obj, "externalRefId", "orderId", "clOrdId", "nOrderID", "id", "ordem", "execId")

            val refId = buildExternalRefId(orderId, ticker, opType, qty, price, dateRaw)
            executions.add(
                ProfitChartExecution(
                    externalRefId = refId,
                    ticker = ticker,
                    operationType = opType,
                    quantity = qty,
                    unitPrice = price,
                    totalValue = qty * price,
                    executedAtLabel = dateRaw,
                    statusLabel = statusRaw.ifBlank { "Executada" },
                    brokerAccount = broker,
                    sourceChannel = sourceChannel
                )
            )
        }

        return ProfitChartParseOutcome(executions, skippedNonExecuted)
    }

    fun parseCsvPayload(csvText: String, sourceChannel: String): ProfitChartParseOutcome {
        val lines = csvText.replace("\uFEFF", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.size < 2) return ProfitChartParseOutcome(emptyList(), 0)

        val delimiter = detectDelimiter(lines.first())
        val headers = splitRow(lines.first(), delimiter).map(::normalizeHeader)

        val tickerIdx = findCol(headers, listOf("ativo", "papel", "ticker", "codigo", "symbol"))
        val sideIdx = findCol(headers, listOf("lado", "c/v", "cv", "operacao", "tipo", "side", "compra/venda"))
        val statusIdx = findCol(headers, listOf("status", "situacao", "estado"))
        val qtyIdx = findCol(headers, listOf("qtd executada", "qtdexec", "qtd exec", "quantidade", "qtd", "qtde", "qty"))
        val priceIdx = findCol(headers, listOf("preco medio", "pm", "preco execucao", "preco", "valor unitario", "cotacao"))
        val dateIdx = findCol(headers, listOf("atualizacao", "criacao", "data/hora", "data hora", "data", "hora", "abertura"))
        val orderIdx = findCol(headers, listOf("ordem", "id ordem", "orderid", "id", "execucao", "numero"))
        val brokerIdx = findCol(headers, listOf("corretora", "conta", "broker", "account"))

        if (tickerIdx < 0 || qtyIdx < 0 || priceIdx < 0) {
            return ProfitChartParseOutcome(emptyList(), 0)
        }

        val executions = mutableListOf<ProfitChartExecution>()
        var skippedNonExecuted = 0

        for (i in 1 until lines.size) {
            val cols = splitRow(lines[i], delimiter)
            if (cols.size <= maxOf(tickerIdx, qtyIdx, priceIdx)) continue

            val statusRaw = if (statusIdx >= 0 && statusIdx < cols.size) cols[statusIdx].trim() else ""
            if (!isExecutedStatus(statusRaw)) {
                skippedNonExecuted++
                continue
            }

            val ticker = LiveQuoteParsers.normalizeTicker(cols[tickerIdx])
            if (ticker.length < 4) continue

            val sideRaw = if (sideIdx >= 0 && sideIdx < cols.size) cols[sideIdx] else "C"
            val opType = parseOperationType(sideRaw) ?: continue

            val qty = parseBrNumber(cols[qtyIdx]) ?: continue
            val price = parseBrNumber(cols[priceIdx]) ?: continue
            if (qty <= 0.0 || price <= 0.0) continue

            val dateRaw = if (dateIdx >= 0 && dateIdx < cols.size && cols[dateIdx].isNotBlank()) {
                cols[dateIdx].trim()
            } else {
                currentBrTimestamp()
            }
            val orderId = if (orderIdx >= 0 && orderIdx < cols.size) cols[orderIdx].trim() else ""
            val broker = if (brokerIdx >= 0 && brokerIdx < cols.size) cols[brokerIdx].trim() else ""
            val refId = buildExternalRefId(orderId, ticker, opType, qty, price, dateRaw)

            executions.add(
                ProfitChartExecution(
                    externalRefId = refId,
                    ticker = ticker,
                    operationType = opType,
                    quantity = qty,
                    unitPrice = price,
                    totalValue = qty * price,
                    executedAtLabel = dateRaw,
                    statusLabel = statusRaw.ifBlank { "Executada" },
                    brokerAccount = broker,
                    sourceChannel = sourceChannel
                )
            )
        }

        return ProfitChartParseOutcome(executions, skippedNonExecuted)
    }

    internal fun isExecutedStatus(rawStatus: String): Boolean {
        val norm = normalizeHeader(rawStatus)
        if (norm.isBlank()) return true
        val rejected = listOf("cancel", "rejeit", "aberta", "open", "pend", "negad", "expir")
        if (rejected.any { norm.contains(it) }) return false
        val accepted = listOf("execut", "filled", "preench", "parcial", "partial", "conclu", "realizada", "2")
        return accepted.any { norm.contains(it) }
    }

    internal fun parseOperationType(raw: String): OperationType? {
        val norm = normalizeHeader(raw)
        return when {
            norm == "c" || norm == "1" || norm.contains("compra") || norm.contains("buy") -> OperationType.COMPRA
            norm == "v" || norm == "2" || norm.contains("venda") || norm.contains("sell") -> OperationType.VENDA
            else -> null
        }
    }

    internal fun buildExternalRefId(
        rawOrderId: String,
        ticker: String,
        opType: OperationType,
        qty: Double,
        price: Double,
        dateRaw: String
    ): String {
        val cleanId = rawOrderId.trim()
        if (cleanId.isNotBlank()) return "PROFIT:$cleanId:$ticker:${opType.name}"
        val qtyStr = String.format(Locale.US, "%.4f", qty)
        val priceStr = String.format(Locale.US, "%.4f", price)
        val digest = "$ticker|${opType.name}|$qtyStr|$priceStr|${dateRaw.trim()}".hashCode().toUInt().toString(16)
        return "PROFIT:HASH:$ticker:$digest"
    }

    private fun optAnyString(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val v = obj.optString(k).trim()
                if (v.isNotEmpty() && v != "null") return v
            }
        }
        return ""
    }

    private fun optAnyDouble(obj: JSONObject, vararg keys: String): Double? {
        for (k in keys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val raw = obj.opt(k)
            when (raw) {
                is Number -> return raw.toDouble()
                is String -> parseBrNumber(raw)?.let { return it }
            }
        }
        return null
    }

    private fun parseBrNumber(raw: String): Double? {
        val cleaned = raw.trim()
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        val normalized = when {
            cleaned.contains(",") && cleaned.contains(".") -> cleaned.replace(".", "").replace(",", ".")
            cleaned.contains(",") -> cleaned.replace(",", ".")
            else -> cleaned
        }
        return normalized.toDoubleOrNull()
    }

    private fun detectDelimiter(header: String): Char {
        val semicolons = header.count { it == ';' }
        val tabs = header.count { it == '\t' }
        val commas = header.count { it == ',' }
        return when {
            tabs >= semicolons && tabs >= commas && tabs > 0 -> '\t'
            semicolons >= commas && semicolons > 0 -> ';'
            else -> ','
        }
    }

    private fun splitRow(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    out.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        out.add(cur.toString().trim())
        return out
    }

    private fun normalizeHeader(raw: String): String {
        val nfd = Normalizer.normalize(raw.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun findCol(headers: List<String>, candidates: List<String>): Int {
        for (c in candidates) {
            val exact = headers.indexOfFirst { it == c }
            if (exact >= 0) return exact
        }
        for (c in candidates) {
            val partial = headers.indexOfFirst { it.contains(c) }
            if (partial >= 0) return partial
        }
        return -1
    }

    private fun currentBrTimestamp(): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
    }
}

class ProfitChartConnector(
    private val context: Context,
    private val client: OkHttpClient = defaultClient()
) {
    suspend fun fetchFromEndpoint(endpointUrl: String, authToken: String): Result<ProfitChartParseOutcome> =
        withContext(Dispatchers.IO) {
            val cleanUrl = endpointUrl.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Informe uma URL válida iniciando com https:// ou http:// (ex.: http://192.168.1.10:8080/profit/executions).")
                )
            }
            try {
                val reqBuilder = Request.Builder()
                    .url(cleanUrl)
                    .header("Accept", "application/json, text/csv, text/plain, */*")
                if (authToken.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${authToken.trim()}")
                    reqBuilder.header("X-Profit-Token", authToken.trim())
                }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("ProfitChart Bridge retornou HTTP ${resp.code}. Verifique o endpoint e o token.")
                        )
                    }
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext Result.success(ProfitChartParseOutcome(emptyList(), 0))
                    }
                    Result.success(ProfitChartParsers.parsePayload(body, "ProfitChart API"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException("Falha ao conectar ao ProfitChart: ${e.localizedMessage ?: "servidor inacessível"}")
                )
            }
        }

    suspend fun parseExportedUri(uri: Uri): Result<ProfitChartParseOutcome> = withContext(Dispatchers.IO) {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Não foi possível abrir o arquivo selecionado."))
            val fileName = queryFileName(uri) ?: "profitchart_export"
            val isZip = rawBytes.size >= 4 &&
                rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()
            val outcome = if (isZip || fileName.lowercase().endsWith(".xlsx")) {
                val csvFromXlsx = extractXlsxAsSemicolonCsv(rawBytes)
                ProfitChartParsers.parseCsvPayload(csvFromXlsx, "ProfitChart XLSX ($fileName)")
            } else {
                val text = SpreadsheetImporter.decodeCsvBytes(rawBytes)
                ProfitChartParsers.parsePayload(text, "ProfitChart Arquivo ($fileName)")
            }
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("Erro ao ler exportação do ProfitChart: ${e.localizedMessage ?: "formato inválido"}")
            )
        }
    }

    private fun queryFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return uri.lastPathSegment
    }

    private fun extractXlsxAsSemicolonCsv(bytes: ByteArray): String {
        val sharedStrings = mutableListOf<String>()
        var sheetXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                            .parse(ByteArrayInputStream(zip.readBytes()))
                        val siNodes = doc.getElementsByTagName("si")
                        for (i in 0 until siNodes.length) {
                            sharedStrings.add(siNodes.item(i).textContent.orEmpty())
                        }
                    }
                    "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        val xml = sheetXml ?: return ""
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val rowNodes = doc.getElementsByTagName("row")
        val sb = StringBuilder()
        for (r in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(r) as? Element ?: continue
            val cells = rowEl.getElementsByTagName("c")
            val values = mutableListOf<String>()
            for (c in 0 until cells.length) {
                val cell = cells.item(c) as? Element ?: continue
                val type = cell.getAttribute("t")
                val vNode = cell.getElementsByTagName("v")
                val raw = if (vNode.length > 0) vNode.item(0).textContent.orEmpty() else ""
                val text = if (type == "s") {
                    raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                } else {
                    raw
                }
                values.add(text)
            }
            if (values.any { it.isNotBlank() }) {
                sb.appendLine(values.joinToString(";"))
            }
        }
        return sb.toString()
    }

    companion object {
        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
        }
    }
}
