package com.example.data.importexport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.PortfolioOperationEntity
import com.example.data.local.PortfolioPositionEntity
import com.example.domain.model.AssetClass
import com.example.domain.model.OperationType
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioType
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class ImportParseResult(
    val fileName: String,
    val fileType: String,
    val positions: List<PortfolioPositionEntity>,
    val cashBalanceBrl: Double?,
    val warnings: List<String>,
    val detectedColumns: List<String>,
    val dividends: List<PortfolioOperationEntity> = emptyList()
)

data class SpreadsheetColumnSpec(
    val name: String,
    val required: Boolean,
    val aliases: String,
    val example: String
)

object SpreadsheetImporter {

    val STANDARD_COLUMN_SPECS: List<SpreadsheetColumnSpec> = listOf(
        SpreadsheetColumnSpec("Ticker", true, "Ticker, Ativo, Código, Papel", "ITUB4 / HGLG11 / CAIXA"),
        SpreadsheetColumnSpec("Nome", false, "Nome, Descrição, Empresa, Fundo", "Itaú Unibanco PN"),
        SpreadsheetColumnSpec("Classe", false, "Classe, Tipo, Categoria", "Ações BR, FII, FIAGRO, ETF, Renda Fixa, Cripto"),
        SpreadsheetColumnSpec("Quantidade", true, "Quantidade, Qtd, Qtde, Cotas (vazio se linha só de dividendo)", "100 / 12,5"),
        SpreadsheetColumnSpec("Preço Médio", true, "Preço Médio, PM, Custo Médio (vazio se linha só de dividendo)", "31,50"),
        SpreadsheetColumnSpec("Preço Atual", false, "Preço Atual, Cotação, Valor Atual", "35,80 (vazio se não houver)"),
        SpreadsheetColumnSpec("Setor", false, "Setor, Segmento", "Bancos / Logística"),
        SpreadsheetColumnSpec("Data-Com", false, "Data-Com, Dia Com", "3 / 30 (dia de 1 a 31)"),
        SpreadsheetColumnSpec("Dividendo Valor", false, "Dividendo Valor, Provento Valor, Valor Provento, Dividendo, Provento, Rendimento, JCP, Dividendo Por Cota", "27,50 (total) ou por cota"),
        SpreadsheetColumnSpec("Data Provento", false, "Data Provento, Data Pagamento, Data Dividendo, Pgto Data", "15/11/2026"),
        SpreadsheetColumnSpec("Tipo Provento", false, "Tipo Provento, Evento Provento, Evento", "Dividendo / JCP / Rendimento")
    )

    fun importFromUri(context: Context, uri: Uri): ImportParseResult {
        val fileName = queryFileName(context, uri) ?: "planilha_importada"
        val lower = fileName.lowercase()
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Não foi possível abrir o arquivo selecionado.")

        val isXlsx = lower.endsWith(".xlsx") || isZipHeader(rawBytes)
        return if (isXlsx) {
            parseXlsx(fileName, ByteArrayInputStream(rawBytes))
        } else {
            parseCsvBytes(fileName, rawBytes)
        }
    }

    fun decodeCsvBytes(rawBytes: ByteArray): String {
        if (rawBytes.isEmpty()) return ""
        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching {
            utf8Decoder.decode(ByteBuffer.wrap(rawBytes)).toString()
        }.getOrElse {
            val win1252 = runCatching { Charset.forName("windows-1252") }.getOrElse { Charsets.ISO_8859_1 }
            rawBytes.toString(win1252)
        }
    }

    fun parseCsvBytes(fileName: String, rawBytes: ByteArray): ImportParseResult {
        return parseCsv(fileName, decodeCsvBytes(rawBytes))
    }

    fun generateStandardCsvTemplate(): String {
        val lines = listOf(
            "Ticker;Nome;Classe;Quantidade;Preço Médio;Preço Atual;Setor;Data-Com;Dividendo Valor;Data Provento;Tipo Provento",
            "ITUB4;Itaú Unibanco PN;Ações BR;100;31,50;35,80;Bancos;3;28,40;01/11/2026;JCP",
            "HGLG11;Pátria Logística FII;FII;25;158,00;156,40;Logística;30;27,50;14/11/2026;Rendimento",
            "KNCA11;Kinea Crédito Agro FIAGRO;FIAGRO;40;96,20;98,50;Agro / CRAs;30;43,20;13/11/2026;Rendimento",
            "IVVB11;iShares S&P 500 ETF;ETF;10;340,00;382,50;EUA / S&P 500;;;;",
            "SELIC29;Tesouro Selic 2029;Renda Fixa;0,50;14900,00;15842,30;Título Público;;;;",
            "CAIXA;Saldo Disponível em Corretora;Renda Fixa;1;2500,00;;Reserva;;;;"
        )
        return "\uFEFF" + lines.joinToString("\n")
    }

    fun exportPositionsToCsv(
        positions: List<PortfolioItemView>,
        cashBalanceBrl: Double,
        latestDividendsByTicker: Map<String, Triple<Double, String, String>> = emptyMap()
    ): String {
        val header = "Ticker;Nome;Classe;Quantidade;Preço Médio;Preço Atual;Setor;Data-Com;Dividendo Valor;Data Provento;Tipo Provento"
        val rows = positions.map { p ->
            val curStr = if (p.hasMarketPrice && p.currentPrice > 0.0) formatCsvDecimal(p.currentPrice) else ""
            val dcStr = p.dataComDay?.toString().orEmpty()
            val divInfo = latestDividendsByTicker[p.ticker.uppercase()]
            val divValStr = divInfo?.first?.takeIf { it > 0.0 }?.let { formatCsvDecimal(it) }.orEmpty()
            val divDateStr = divInfo?.second.orEmpty()
            val divTypeStr = divInfo?.third.orEmpty()
            listOf(
                escapeCsvCell(p.ticker),
                escapeCsvCell(p.name),
                escapeCsvCell(p.assetClass.shortLabel),
                formatCsvDecimal(p.quantity),
                formatCsvDecimal(p.averagePrice),
                curStr,
                escapeCsvCell(p.sector),
                dcStr,
                divValStr,
                escapeCsvCell(divDateStr),
                escapeCsvCell(divTypeStr)
            ).joinToString(";")
        }
        val cashRow = if (cashBalanceBrl > 0.0) {
            listOf(
                "CAIXA;Saldo em Caixa;Renda Fixa;1;${formatCsvDecimal(cashBalanceBrl)};;Liquidez;;;;"
            )
        } else {
            emptyList()
        }
        return "\uFEFF" + (listOf(header) + rows + cashRow).joinToString("\n")
    }

    private fun formatCsvDecimal(value: Double): String {
        return String.format(Locale("pt", "BR"), "%.4f", value)
            .trimEnd('0')
            .trimEnd(',')
    }

    private fun escapeCsvCell(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return if (clean.contains(';') || clean.contains(',') || clean.contains('"')) {
            "\"$clean\""
        } else {
            clean
        }
    }

    private fun isZipHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    fun parseCsv(fileName: String, text: String): ImportParseResult {
        val lines = text.replace("\uFEFF", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        if (lines.isEmpty()) {
            return ImportParseResult(fileName, "CSV", emptyList(), null, listOf("Arquivo CSV vazio."), emptyList())
        }

        val headerLine = lines.first()
        val delimiter = when {
            headerLine.count { it == ';' } >= headerLine.count { it == ',' } && headerLine.contains(';') -> ';'
            headerLine.contains('\t') && !headerLine.contains(',') && !headerLine.contains(';') -> '\t'
            else -> ','
        }
        val rows = lines.map { splitCsvLine(it, delimiter) }
        return mapRowsToPositions(fileName, "CSV", rows)
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    fun parseXlsx(fileName: String, input: InputStream): ImportParseResult {
        var sharedStrings = emptyList<String>()
        val worksheets = linkedMapOf<String, ByteArray>()

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                when {
                    name == "xl/sharedstrings.xml" -> {
                        val bytes = zip.readBytes()
                        sharedStrings = parseSharedStringsXml(ByteArrayInputStream(bytes))
                    }
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                        worksheets[name] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val sheetBytes = worksheets["xl/worksheets/sheet1.xml"]
            ?: worksheets.entries
                .sortedBy { extractSheetOrder(it.key) }
                .firstOrNull()
                ?.value
            ?: return ImportParseResult(
                fileName = fileName,
                fileType = "XLSX",
                positions = emptyList(),
                cashBalanceBrl = null,
                warnings = listOf("Nenhuma planilha (xl/worksheets/sheet1.xml) encontrada no arquivo XLSX."),
                detectedColumns = emptyList()
            )

        val rows = parseSheetXml(ByteArrayInputStream(sheetBytes), sharedStrings)
        return mapRowsToPositions(fileName, "XLSX", rows)
    }

    private fun extractSheetOrder(entryName: String): Int {
        return entryName.substringAfterLast("sheet").substringBefore(".xml").toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun createSecureDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }.newDocumentBuilder()

    private fun parseSharedStringsXml(input: InputStream): List<String> {
        val doc = createSecureDocumentBuilder().parse(input)
        val siNodes = doc.getElementsByTagName("si")
        val strings = mutableListOf<String>()
        for (i in 0 until siNodes.length) {
            val siElem = siNodes.item(i) as? Element ?: continue
            val tNodes = siElem.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until tNodes.length) {
                sb.append(tNodes.item(j).textContent.orEmpty())
            }
            strings.add(sb.toString())
        }
        return strings
    }

    private fun parseSheetXml(input: InputStream, sharedStrings: List<String>): List<List<String>> {
        val doc = createSecureDocumentBuilder().parse(input)
        val rowNodes = doc.getElementsByTagName("row")
        val rows = mutableListOf<List<String>>()
        for (r in 0 until rowNodes.length) {
            val rowElem = rowNodes.item(r) as? Element ?: continue
            val cellNodes = rowElem.getElementsByTagName("c")
            val currentRow = mutableMapOf<Int, String>()
            var fallbackCol = 0
            for (c in 0 until cellNodes.length) {
                val cellElem = cellNodes.item(c) as? Element ?: continue
                val ref = cellElem.getAttribute("r")
                val colIdx = if (ref.isNotBlank()) columnRefToIndex(ref) else fallbackCol
                fallbackCol = colIdx + 1
                val cellType = cellElem.getAttribute("t")
                val resolved = when (cellType) {
                    "inlineStr" -> {
                        val tList = cellElem.getElementsByTagName("t")
                        val sb = StringBuilder()
                        for (j in 0 until tList.length) sb.append(tList.item(j).textContent.orEmpty())
                        sb.toString().trim()
                    }
                    "s" -> {
                        val vList = cellElem.getElementsByTagName("v")
                        val rawIdx = if (vList.length > 0) vList.item(0).textContent.orEmpty().trim() else ""
                        rawIdx.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                    }
                    "b" -> {
                        val vList = cellElem.getElementsByTagName("v")
                        val rawBool = if (vList.length > 0) vList.item(0).textContent.orEmpty().trim() else ""
                        if (rawBool == "1") "TRUE" else "FALSE"
                    }
                    "e" -> "" // Error cells (#N/A, #DIV/0!, etc.)
                    else -> { // "n", "str" (formula string), or omitted numeric
                        val vList = cellElem.getElementsByTagName("v")
                        if (vList.length > 0) vList.item(0).textContent.orEmpty().trim() else ""
                    }
                }
                if (colIdx >= 0) currentRow[colIdx] = resolved
            }
            if (currentRow.isNotEmpty()) {
                val maxCol = currentRow.keys.maxOrNull() ?: 0
                val rowList = (0..maxCol).map { currentRow[it].orEmpty().trim() }
                if (rowList.any { it.isNotBlank() }) rows.add(rowList)
            }
        }
        return rows
    }

    private fun columnRefToIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return 0
        var col = 0
        for (ch in letters) {
            col = col * 26 + (ch - 'A' + 1)
        }
        return col - 1
    }

    private fun mapRowsToPositions(
        fileName: String,
        fileType: String,
        rows: List<List<String>>
    ): ImportParseResult {
        if (rows.size < 2) {
            return ImportParseResult(
                fileName = fileName,
                fileType = fileType,
                positions = emptyList(),
                cashBalanceBrl = null,
                warnings = listOf("A planilha precisa conter cabeçalho e ao menos uma linha de posição."),
                detectedColumns = rows.firstOrNull().orEmpty()
            )
        }

        val headers = rows.first()
        val normHeaders = headers.map { normalizeHeader(it) }

        val tickerIdx = findCol(
            normHeaders,
            "ticker", "ativo", "codigo", "codigo_de_negociacao", "papel", "simbolo", "produto"
        )
        val nameIdx = findCol(normHeaders, "nome", "descricao", "empresa", "fundo", "instituicao")
        val classIdx = findCol(normHeaders, "classe", "tipo", "categoria", "asset_class", "tipo_ativo")
        val qtyIdx = findCol(normHeaders, "quantidade", "qtd", "qtde", "cotas", "quantity", "quantidade_disponivel")
        val pmIdx = findCol(normHeaders, "preco_medio", "precomedio", "pm", "custo_medio", "valor_medio", "avg_price")
        val totalCostIdx = findCol(normHeaders, "valor_investido", "custo_total", "valor_aplicado")
        val curIdx = findCol(
            normHeaders,
            "preco_atual", "precoatual", "cotacao", "valor_atual", "current_price", "ultimo_preco", "preco_de_fechamento"
        )
        val sectorIdx = findCol(normHeaders, "setor", "segmento", "sector")
        val dataComIdx = findCol(normHeaders, "data_com", "datacom", "dia_com")
        val cashIdx = findCol(normHeaders, "caixa", "saldo_caixa", "cash")

        // Campos opcionais de dividendos / proventos na mesma tabela de ativos
        val divPerUnitIdx = findCol(
            normHeaders,
            "dividendo_por_cota", "dividendo_cota", "provento_unitario", "provento_cota", "rendimento_cota", "valor_cota"
        )
        val divValueIdx = findCol(
            normHeaders,
            "dividendo_valor", "valor_dividendo", "provento_valor", "valor_provento",
            "valor_total_provento", "dividendo_total", "provento_total",
            "dividendo", "provento", "rendimento", "jcp", "dividendos", "proventos"
        )
        val divDateIdx = findCol(
            normHeaders,
            "data_provento", "data_dividendo", "data_pagamento", "data_pgto", "pgto_data", "pagamento_provento"
        )
        val divTypeIdx = findCol(
            normHeaders,
            "tipo_provento", "tipo_dividendo", "evento_provento", "evento"
        )

        val hasPositionCols = qtyIdx != -1 && (pmIdx != -1 || totalCostIdx != -1)
        val hasDividendCols = divValueIdx != -1 || divPerUnitIdx != -1

        if (tickerIdx == -1 || (!hasPositionCols && !hasDividendCols)) {
            return ImportParseResult(
                fileName = fileName,
                fileType = fileType,
                positions = emptyList(),
                cashBalanceBrl = null,
                warnings = listOf(
                    "Colunas obrigatórias não encontradas. Esperado: Ticker/Ativo + (Quantidade/PM e/ou Dividendo Valor). Detectadas: ${headers.joinToString(", ")}"
                ),
                detectedColumns = headers
            )
        }

        val warnings = mutableListOf<String>()
        val aggregated = linkedMapOf<String, PortfolioPositionEntity>()
        val importedDividends = mutableListOf<PortfolioOperationEntity>()
        var detectedCash: Double? = null
        val todayFormatted = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())

        rows.drop(1).forEachIndexed { idx, row ->
            val rawTickerCell = row.getOrNull(tickerIdx)?.trim()?.uppercase().orEmpty()
            if (rawTickerCell.isBlank()) return@forEachIndexed
            val rawTicker = rawTickerCell.substringBefore(" - ").trim()

            if (rawTicker == "CAIXA" || rawTicker == "CASH" || rawTicker == "SALDO") {
                val valCash = (if (pmIdx != -1) parseBrNumber(row.getOrNull(pmIdx)) else null)
                    ?: (if (totalCostIdx != -1) parseBrNumber(row.getOrNull(totalCostIdx)) else null)
                    ?: (if (curIdx != -1) parseBrNumber(row.getOrNull(curIdx)) else null)
                if (valCash != null && valCash >= 0.0) {
                    detectedCash = (detectedCash ?: 0.0) + valCash
                }
                return@forEachIndexed
            }

            if (cashIdx != -1) {
                parseBrNumber(row.getOrNull(cashIdx))?.let {
                    if (it > 0) detectedCash = it
                }
            }

            val qty = if (qtyIdx != -1) parseBrNumber(row.getOrNull(qtyIdx)) else null
            val pm = (if (pmIdx != -1) parseBrNumber(row.getOrNull(pmIdx)) else null)
                ?: (if (totalCostIdx != -1 && qty != null && qty > 0.0) {
                    parseBrNumber(row.getOrNull(totalCostIdx))?.div(qty)
                } else null)

            val hasValidPosition = qty != null && qty > 0.0 && pm != null && pm > 0.0

            if (hasValidPosition) {
                val validQty = qty!!
                val validPm = pm!!
                val currentPrice = if (curIdx != -1) parseBrNumber(row.getOrNull(curIdx)) else null
                val hasMarketPrice = currentPrice != null && currentPrice > 0.0

                val assetClass = inferAssetClass(
                    rawClass = row.getOrNull(classIdx).orEmpty(),
                    ticker = rawTicker
                )
                val dataComDay = if (dataComIdx != -1) {
                    row.getOrNull(dataComIdx)?.filter { it.isDigit() }?.toIntOrNull()?.takeIf { it in 1..31 }
                } else null

                val candidate = PortfolioPositionEntity(
                    portfolioType = "REAL",
                    ticker = rawTicker,
                    name = row.getOrNull(nameIdx)?.takeIf { it.isNotBlank() } ?: rawTicker,
                    assetClass = assetClass.name,
                    quantity = validQty,
                    averagePrice = validPm,
                    currentPrice = currentPrice ?: 0.0,
                    hasMarketPrice = hasMarketPrice,
                    sector = row.getOrNull(sectorIdx)?.takeIf { it.isNotBlank() } ?: "Importado",
                    dataComDay = dataComDay,
                    dataSourceLabel = "Importação $fileType ($fileName)",
                    isDemo = false
                )

                val existing = aggregated[rawTicker]
                if (existing == null) {
                    aggregated[rawTicker] = candidate
                } else {
                    val totalQty = existing.quantity + candidate.quantity
                    val weightedPm = ((existing.quantity * existing.averagePrice) + (candidate.quantity * candidate.averagePrice)) / totalQty
                    val resolvedPrice = when {
                        candidate.hasMarketPrice -> candidate.currentPrice
                        existing.hasMarketPrice -> existing.currentPrice
                        else -> 0.0
                    }
                    val resolvedHasPrice = candidate.hasMarketPrice || existing.hasMarketPrice
                    aggregated[rawTicker] = existing.copy(
                        quantity = totalQty,
                        averagePrice = weightedPm,
                        currentPrice = resolvedPrice,
                        hasMarketPrice = resolvedHasPrice,
                        name = if (existing.name == existing.ticker && candidate.name != candidate.ticker) candidate.name else existing.name,
                        sector = if (existing.sector == "Importado" && candidate.sector != "Importado") candidate.sector else existing.sector,
                        dataComDay = existing.dataComDay ?: candidate.dataComDay
                    )
                    warnings.add("$rawTicker consolidado em lote único (quantidade somada e PM ponderado).")
                }
            }

            // Extrai dividendo / provento da linha (combinada com posição OU linha extra só de dividendo)
            val rawTotalDiv = if (divValueIdx != -1 && divValueIdx != divPerUnitIdx) {
                parseBrNumber(row.getOrNull(divValueIdx))
            } else null
            val rawUnitDiv = if (divPerUnitIdx != -1) {
                parseBrNumber(row.getOrNull(divPerUnitIdx))
            } else null

            val resolvedQtyForDiv = (qty?.takeIf { it > 0.0 })
                ?: aggregated[rawTicker]?.quantity?.takeIf { it > 0.0 }
                ?: 1.0

            val (finalDivTotal, divUnitQtyPair) = when {
                rawTotalDiv != null && rawTotalDiv > 0.0 -> rawTotalDiv to (1.0 to rawTotalDiv)
                rawUnitDiv != null && rawUnitDiv > 0.0 -> {
                    val total = rawUnitDiv * resolvedQtyForDiv
                    total to (resolvedQtyForDiv to rawUnitDiv)
                }
                else -> null to (1.0 to 0.0)
            }

            if (finalDivTotal != null && finalDivTotal > 0.0) {
                val rawDivDate = if (divDateIdx != -1) row.getOrNull(divDateIdx).orEmpty() else ""
                val normalizedDivDate = normalizeSpreadsheetDate(rawDivDate) ?: todayFormatted
                val rawEvent = if (divTypeIdx != -1) row.getOrNull(divTypeIdx)?.trim().orEmpty() else ""
                val eventLabel = rawEvent.ifBlank { "Provento" }
                val refId = "DIV_IMPORT:$rawTicker:$normalizedDivDate:${String.format(Locale.US, "%.2f", finalDivTotal)}"

                importedDividends.add(
                    PortfolioOperationEntity(
                        portfolioType = PortfolioType.REAL.name,
                        ticker = rawTicker,
                        operationType = OperationType.PROVENTO.name,
                        quantity = divUnitQtyPair.first,
                        unitPrice = divUnitQtyPair.second,
                        totalValue = finalDivTotal,
                        dateIso = normalizedDivDate,
                        notes = "$eventLabel • Planilha $fileType ($fileName)",
                        externalRefId = refId
                    )
                )
            } else if (!hasValidPosition) {
                warnings.add("Linha ${idx + 2} ($rawTicker) ignorada: quantidade/PM e valor de dividendo inválidos.")
            }
        }

        val finalPositions = aggregated.values.toList()
        finalPositions.filter { !it.hasMarketPrice }.forEach { pos ->
            warnings.add("${pos.ticker} importado sem cotação atual na planilha — preço não foi inventado; atualize manualmente ou via próxima importação.")
        }

        return ImportParseResult(
            fileName = fileName,
            fileType = fileType,
            positions = finalPositions,
            cashBalanceBrl = detectedCash,
            warnings = warnings.distinct(),
            detectedColumns = headers,
            dividends = importedDividends.distinctBy { it.externalRefId }
        )
    }

    private fun normalizeSpreadsheetDate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        // Suporte a número serial de data do Excel (ex: 46341 -> dd/MM/yyyy)
        val serial = trimmed.replace(",", ".").toDoubleOrNull()
        if (serial != null && serial in 20000.0..90000.0 && !trimmed.contains("/") && !trimmed.contains("-")) {
            val cal = Calendar.getInstance().apply {
                set(1899, Calendar.DECEMBER, 30, 12, 0, 0)
                add(Calendar.DAY_OF_YEAR, serial.toInt())
            }
            return SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(cal.time)
        }

        val outFmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val clean = trimmed.substringBefore(" ")
        for (pattern in listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yy")) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = fmt.parse(clean)
                if (parsed != null) return outFmt.format(parsed)
            }
        }
        return null
    }

    private fun normalizeHeader(value: String): String {
        return value.lowercase()
            .replace("á", "a").replace("à", "a").replace("ã", "a").replace("â", "a")
            .replace("é", "e").replace("ê", "e")
            .replace("í", "i")
            .replace("ó", "o").replace("ô", "o").replace("õ", "o")
            .replace("ú", "u").replace("ç", "c")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun findCol(headers: List<String>, vararg aliases: String): Int {
        val exact = headers.indexOfFirst { h -> aliases.any { alias -> h == alias } }
        if (exact != -1) return exact
        return headers.indexOfFirst { h -> aliases.any { alias -> h.contains(alias) } }
    }

    private fun parseBrNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.startsWith("#") || trimmed == "-" || trimmed == "--") return null
        val cleaned = trimmed.replace("R$", "", ignoreCase = true)
            .replace("US$", "", ignoreCase = true)
            .replace("%", "")
            .replace(" ", "")
            .trim()
        if (cleaned.isEmpty()) return null
        val normalized = if (cleaned.contains(",") && cleaned.contains(".")) {
            if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                cleaned.replace(".", "").replace(",", ".")
            } else {
                cleaned.replace(",", "")
            }
        } else if (cleaned.contains(",")) {
            cleaned.replace(",", ".")
        } else {
            cleaned
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun inferAssetClass(rawClass: String, ticker: String): AssetClass {
        val norm = rawClass.lowercase()
        return when {
            norm.contains("fiagro") -> AssetClass.FIAGRO
            norm.contains("fii") || norm.contains("imob") -> AssetClass.FII
            norm.contains("etf") || norm.contains("fundo de indice") -> AssetClass.ETF
            norm.contains("renda fixa") || norm.contains("tesouro") || norm.contains("cdb") || norm.contains("lci") || norm.contains("lca") -> AssetClass.RENDA_FIXA
            norm.contains("cripto") || norm.contains("btc") || norm.contains("eth") -> AssetClass.CRIPTO
            norm.contains("acao") || norm.contains("ações") || norm.contains("acoes") -> AssetClass.ACOES_BR
            ticker in listOf("BTC", "ETH", "SOL", "LINK", "AVAX") -> AssetClass.CRIPTO
            ticker.endsWith("11") && ticker.length == 6 -> AssetClass.FII
            else -> AssetClass.ACOES_BR
        }
    }
}
