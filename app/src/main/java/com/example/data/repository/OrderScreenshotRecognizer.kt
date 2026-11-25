package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.example.domain.model.OrderScreenshotJsonParser
import com.example.domain.model.OrderScreenshotRecognitionResult
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

enum class BrokerageDocumentKind {
    PDF,
    IMAGE,
    UNSUPPORTED
}

data class LoadedBrokerageDocument(
    val bytes: ByteArray,
    val displayName: String,
    val mimeType: String,
    val kind: BrokerageDocumentKind
) {
    val isPdf: Boolean get() = kind == BrokerageDocumentKind.PDF
}

/**
 * Reconhecedor multimodal de Notas de Corretagem em PDF (SINACOR B3 RV Listado e BM&F Futuros)
 * e prints de ordens de corretoras (Profit Mobile, Genial, XP, Rico, Clear, BTG, Inter, NuInvest)
 * via Firebase AI Logic (`Firebase.ai` + `gemini-3.8-flash`).
 */
class OrderScreenshotRecognizer(
    private val context: Context
) {
    companion object {
        private const val TAG = "OrderNoteRecognizer"
        private const val MAX_INLINE_BYTES = 18 * 1024 * 1024 // Limite seguro (< 20 MB Firebase AI Logic)
        private const val MAX_READ_BYTES = 35 * 1024 * 1024   // Permite ler fotos em alta resolução antes de redimensionar
        private const val IMAGE_TIMEOUT_MS = 60_000L
        private const val PDF_TIMEOUT_MS = 120_000L
        private const val PDF_HEADER_SCAN_LIMIT = 1024

        val PICKER_MIME_TYPES: Array<String> = arrayOf(
            "application/pdf",
            "application/x-pdf",
            "image/*",
            "application/octet-stream"
        )

        private val GEMINI_DIRECT_IMAGE_MIMES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif",
            "image/gif"
        )

        /**
         * Verifica a assinatura `%PDF` nos primeiros 1024 bytes (padrão ISO 32000),
         * tolerando UTF-8 BOM ou espaços/bytes iniciais antes do cabeçalho PDF.
         */
        fun containsPdfHeader(bytes: ByteArray): Boolean {
            if (bytes.size < 4) return false
            val limit = minOf(bytes.size - 4, PDF_HEADER_SCAN_LIMIT)
            for (i in 0..limit) {
                if (
                    bytes[i] == '%'.code.toByte() &&
                    bytes[i + 1] == 'P'.code.toByte() &&
                    bytes[i + 2] == 'D'.code.toByte() &&
                    bytes[i + 3] == 'F'.code.toByte()
                ) {
                    return true
                }
            }
            return false
        }

        /**
         * Detecta o MIME de imagem pelos magic bytes do arquivo (JPEG, PNG, WebP, GIF, BMP, HEIC/HEIF/AVIF).
         */
        fun detectImageMimeFromBytes(bytes: ByteArray): String? {
            if (bytes.size < 4) return null
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF

            // JPEG: FF D8 FF
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "image/jpeg"

            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (
                bytes.size >= 8 &&
                b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47 &&
                (bytes[4].toInt() and 0xFF) == 0x0D &&
                (bytes[5].toInt() and 0xFF) == 0x0A &&
                (bytes[6].toInt() and 0xFF) == 0x1A &&
                (bytes[7].toInt() and 0xFF) == 0x0A
            ) {
                return "image/png"
            }

            // WebP: RIFF....WEBP
            if (
                bytes.size >= 12 &&
                b0 == 'R'.code && b1 == 'I'.code && b2 == 'F'.code && b3 == 'F'.code &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte()
            ) {
                return "image/webp"
            }

            // GIF: GIF87a / GIF89a
            if (
                bytes.size >= 6 &&
                b0 == 'G'.code && b1 == 'I'.code && b2 == 'F'.code && b3 == '8'.code
            ) {
                return "image/gif"
            }

            // BMP: 42 4D
            if (b0 == 'B'.code && b1 == 'M'.code) return "image/bmp"

            // ISO BMFF (HEIC / HEIF / AVIF): offset 4 == "ftyp"
            if (
                bytes.size >= 12 &&
                bytes[4] == 'f'.code.toByte() &&
                bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() &&
                bytes[7] == 'p'.code.toByte()
            ) {
                val brand = String(bytes, 8, 4, Charsets.US_ASCII).lowercase()
                return when {
                    brand in setOf("heic", "heix", "hevc", "hevx") -> "image/heic"
                    brand in setOf("mif1", "msf1", "heif") -> "image/heif"
                    brand in setOf("avif", "avis") -> "image/avif"
                    else -> null
                }
            }

            return null
        }

        /**
         * Classifica o arquivo carregado priorizando assinatura binária (magic bytes),
         * com fallback seguro para MIME/extensão.
         */
        fun detectDocumentKindAndMime(
            bytes: ByteArray,
            displayName: String,
            resolverMime: String
        ): Pair<BrokerageDocumentKind, String> {
            // 1. Assinatura PDF nos primeiros 1024 bytes (independe de MIME ou extensão .PDF)
            if (containsPdfHeader(bytes)) {
                return BrokerageDocumentKind.PDF to "application/pdf"
            }

            // 2. Assinatura binária de imagem (PNG, JPG, WebP, HEIC, HEIF, AVIF, GIF, BMP)
            val magicImageMime = detectImageMimeFromBytes(bytes)
            if (magicImageMime != null) {
                return BrokerageDocumentKind.IMAGE to magicImageMime
            }

            // 3. Rejeita explicitamente arquivos comuns de planilha/ZIP/HTML que não são PDF nem imagem
            val lowerName = displayName.lowercase()
            val lowerMime = resolverMime.lowercase()
            if (
                lowerName.endsWith(".csv") ||
                lowerName.endsWith(".xlsx") ||
                lowerName.endsWith(".xls") ||
                lowerName.endsWith(".txt") ||
                lowerName.endsWith(".zip") ||
                lowerMime.contains("spreadsheet") ||
                lowerMime.contains("excel") ||
                lowerMime.contains("csv")
            ) {
                return BrokerageDocumentKind.UNSUPPORTED to resolverMime.ifBlank { "application/octet-stream" }
            }

            // 4. Fallback por MIME/extensão de imagem (caso formato raro decodificável pelo Android)
            if (lowerMime.startsWith("image/")) {
                return BrokerageDocumentKind.IMAGE to lowerMime
            }
            val extImageMime = when {
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
                lowerName.endsWith(".png") -> "image/png"
                lowerName.endsWith(".webp") -> "image/webp"
                lowerName.endsWith(".heic") -> "image/heic"
                lowerName.endsWith(".heif") -> "image/heif"
                lowerName.endsWith(".gif") -> "image/gif"
                lowerName.endsWith(".bmp") -> "image/bmp"
                else -> null
            }
            if (extImageMime != null) {
                return BrokerageDocumentKind.IMAGE to extImageMime
            }

            return BrokerageDocumentKind.UNSUPPORTED to resolverMime.ifBlank { "application/octet-stream" }
        }
    }

    private val responseSchema = Schema.obj(
        properties = mapOf(
            "documentKind" to Schema.enumeration(
                values = listOf("PRINT", "NOTA_B3", "NOTA_BMF"),
                description = "Tipo do documento: 'NOTA_B3' para Nota de Corretagem B3 RV (Ações/FIIs/FIAGROs/ETFs), 'NOTA_BMF' para Nota de Corretagem BM&F (Futuros WIN/WDO/IND/DOL), ou 'PRINT' para captura de tela de corretora/Profit."
            ),
            "brokerHeader" to Schema.string(
                description = "Nome da corretora ou conta exibida no cabeçalho (ex.: 'GENIAL CCTVM S/A', 'GENIAL - SWINGTRADE', 'XP', 'BTG'). Vazio se não visível."
            ),
            "notesCount" to Schema.integer(
                description = "Quantidade de notas/pregões distintos encontrados no documento (0 para print simples)."
            ),
            "totalFeesBrl" to Schema.double(
                description = "Soma total em reais (R$) das taxas, emolumentos, liquidação, clearing, registro BM&F e IRRF informados no Resumo Financeiro / Despesas de todas as notas. Use 0 se não aplicável."
            ),
            "totalNetSettlementBrl" to Schema.double(
                description = "Soma líquida financeira em reais (R$) dos campos 'Líquido para ...' ou 'Total líquido da nota' (positivo para crédito C, negativo para débito D).",
                nullable = true
            ),
            "warnings" to Schema.array(
                items = Schema.string(),
                description = "Alertas em português sobre ordens parcialmente cortadas, páginas ilegíveis, especificações ambíguas ou observações relevantes."
            ),
            "orders" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "ticker" to Schema.string(
                            description = "Código oficial do ativo na B3 em maiúsculas (ex.: ALOS3, DEVA11, HCTR11, IRIM11, VULC3, ISAE4, EGAF11, TRXF11, WINZ26, WDOF27)."
                        ),
                        "specificationTitle" to Schema.string(
                            description = "Especificação exata do título ou mercadoria como impressa na nota (ex.: 'ALLOS ON ED NM', 'FII DEVANT CI ER', 'WIN Z26 16/12/2026'). Vazio se for print com ticker direto."
                        ),
                        "side" to Schema.enumeration(
                            values = listOf("COMPRA", "VENDA"),
                            description = "Lado da operação: 'COMPRA' (C) ou 'VENDA' (V). Em prints Profit/Genial, '5C'/'10C' = COMPRA e '5V'/'10V' = VENDA."
                        ),
                        "marketSegment" to Schema.enumeration(
                            values = listOf("VISTA", "FRACIONARIO", "BMF_FUTUROS", "OPCOES"),
                            description = "Segmento de mercado: 'VISTA', 'FRACIONARIO', 'BMF_FUTUROS' (para contratos futuros WIN/WDO/IND/DOL) ou 'OPCOES'."
                        ),
                        "isDayTrade" to Schema.boolean(
                            description = "true se a operação estiver marcada como Day Trade (coluna Obs. 'D' na B3 ou Tipo do negócio 'DAY TRADE' na BM&F)."
                        ),
                        "noteNumber" to Schema.string(
                            description = "Número da nota de corretagem ('Nr.Nota' / 'Nr. nota', ex.: '2333', '534005'). Vazio se não aplicável."
                        ),
                        "quantity" to Schema.double(
                            description = "Quantidade total executada consolidada (ex.: 5.0, 10.0, 2.0 contratos). Use 0 se não visível."
                        ),
                        "unitPrice" to Schema.double(
                            description = "Preço unitário médio ponderado em reais (B3 RV) ou pontos de ajuste (BM&F Futuros, ex.: 167625.0). Use 0 se não visível."
                        ),
                        "totalValue" to Schema.double(
                            description = "Valor financeiro total em reais (R$). Em BM&F Futuros, use EXATAMENTE a coluna 'Vlr. de Operação / Ajuste' em reais (ex.: 29.60, 49.60) — NUNCA multiplique quantidade por pontos."
                        ),
                        "feesBrl" to Schema.double(
                            description = "Taxas operacionais atribuídas à operação ou nota em reais (R$), se visível; caso contrário 0."
                        ),
                        "dateBr" to Schema.string(
                            description = "Data do pregão ou fechamento no formato dd/MM/yyyy (ex.: '03/11/2026', '23/11/2026')."
                        ),
                        "timeLabel" to Schema.string(
                            description = "Horário da execução/atualização (ex.: '14:13:21'). Vazio se não constar na nota."
                        ),
                        "status" to Schema.string(
                            description = "Status da ordem (em notas de corretagem use 'Executada'; em prints use o status exibido)."
                        ),
                        "orderType" to Schema.string(
                            description = "Tipo ou modalidade da operação (ex.: 'Normal', 'Day Trade', 'Limite', 'Mercado')."
                        ),
                        "isIncomplete" to Schema.boolean(
                            description = "true apenas se os dados essenciais estiverem cortados ou ilegíveis."
                        ),
                        "notes" to Schema.string(
                            description = "Observação curta em português (ex.: 'Consolidado de 8 parciais', 'Ajuste D/C', ou aviso de corte)."
                        )
                    ),
                    optionalProperties = listOf("specificationTitle", "noteNumber", "feesBrl", "timeLabel", "orderType", "notes")
                ),
                description = "Lista de operações extraídas em ordem cronológica/página."
            )
        ),
        optionalProperties = listOf("notesCount", "totalFeesBrl", "totalNetSettlementBrl")
    )

    private val visionModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiTaskModel.GENERAL.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = this@OrderScreenshotRecognizer.responseSchema
                temperature = 0.1f
            },
            systemInstruction = content {
                text(
                    "Você é um auditor e leitor especializado em prints de home broker e Notas de Corretagem brasileiras padrão SINACOR (B3 RV Listado e BM&F Derivativos Futuros — Genial, XP, Rico, Clear, BTG, Inter, NuInvest, Profit Mobile). " +
                        "Extraia com máxima precisão numérica todas as operações executadas. " +
                        "Converta números no padrão brasileiro para decimal JSON (ex.: '1.250,40' -> 1250.40; '167.625,0000' -> 167625.0; '29,60' -> 29.60). " +
                        "NUNCA invente valores cortados ou ilegíveis."
                )
            }
        )
    }

    private val brokeragePdfModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiTaskModel.GENERAL.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = this@OrderScreenshotRecognizer.responseSchema
                temperature = 0.05f
                maxOutputTokens = 16384
            },
            systemInstruction = content {
                text(
                    "Você é um especialista contábil e financeiro em Notas de Corretagem brasileiras no padrão SINACOR (B3 RV Listado e BM&F Derivativos Futuros). " +
                        "Você deve ler TODAS as páginas do PDF com precisão absoluta, identificar cada Nota ('Nr.Nota' e 'Data pregão'), traduzir 'Especificação do título' para o ticker oficial da B3 (ex.: 'ALLOS ON ED NM' -> 'ALOS3', 'FII DEVANT CI ER' -> 'DEVA11', 'FII HECTARE CI' -> 'HCTR11', 'FII IRIM CI' -> 'IRIM11', 'FII SMRE CI ER' -> 'SMRE11', 'FII EXES CI' -> 'EXES11', 'FII BTG AGRO CI' / 'FIAGRO BTAG CI' -> 'BTAG11', 'VULCABRAS ON NM' -> 'VULC3', 'FII MAUA RE CI' -> 'MCRE11', 'WIZ CO ON NM' -> 'WIZC3', 'MINERVA ON NM' -> 'BEEF3', 'KLABIN S/A ON N2' -> 'KLBN3', 'DEXXOS PAR ON N1' -> 'DEXP3', 'FII BTHF CI' -> 'BTHF11', 'FII RIZA AKN CI' -> 'RZAK11', 'B3 ON NM' -> 'B3SA3', 'SCHULZ PN' -> 'SHUL4', 'MITRE REALTY ON NM' -> 'MTRE3', 'CURY S/A ON NM' -> 'CURY3', 'CAIXA SEGURI ON ED NM' -> 'CXSE3', 'TENDA ON NM' -> 'TEND3', 'CARTESIA FII CI' -> 'CACR11', 'FII REC REND CI' -> 'RECR11', 'FII EQI RECE CI' -> 'EQIR11', 'LAVVI ON NM' -> 'LAVV3', 'ISA ENERGIA PN EJ N1' -> 'ISAE4', 'CEA MODAS ON NM' -> 'CEAB3', 'COPEL ON NM' -> 'CPLE3', 'FIAGRO ECO CI' -> 'EGAF11', 'FII RB YIELD CI' -> 'RBRI11', 'WIN Z26' -> 'WINZ26'). " +
                        "Para manter a saída compacta e 100% fiel em PDFs com múltiplas páginas, CONSOLIDE execuções parciais idênticas da mesma (Nr.Nota + Data pregão + ticker + C/V + isDayTrade) somando a quantidade, calculando o preço médio ponderado e somando o valor financeiro."
                )
            }
        )
    }

    fun isPdfUri(uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (mime.contains("pdf", ignoreCase = true)) return true

        val displayName = resolveDisplayName(uri)
        if (displayName.endsWith(".pdf", ignoreCase = true)) return true

        return runCatching {
            openUriInputStream(uri)?.use { stream ->
                val header = readPrefixBytes(stream, PDF_HEADER_SCAN_LIMIT + 4)
                containsPdfHeader(header)
            } ?: false
        }.getOrDefault(false)
    }

    /**
     * Lê o arquivo da `Uri` uma única vez (com fallbacks para provedores SAF, Samsung My Files,
     * Galeria e arquivos virtuais da nuvem) e classifica PDF vs. Imagem pelos bytes reais.
     */
    suspend fun loadDocumentFromUri(uri: Uri): LoadedBrokerageDocument = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri)
        val resolverMime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val bytes = readBytesOnce(uri, MAX_READ_BYTES)
        val (kind, resolvedMime) = detectDocumentKindAndMime(
            bytes = bytes,
            displayName = displayName,
            resolverMime = resolverMime
        )
        if (kind == BrokerageDocumentKind.UNSUPPORTED) {
            val fileLabel = displayName.ifBlank { "selecionado" }
            val lower = fileLabel.lowercase()
            val spreadsheetHint = if (
                lower.endsWith(".csv") || lower.endsWith(".xlsx") || lower.endsWith(".xls")
            ) {
                " Para planilhas CSV/XLSX, use o botão \"Importar XLSX/CSV\"."
            } else {
                ""
            }
            throw IllegalStateException(
                "O arquivo \"$fileLabel\" não foi reconhecido como Nota de Corretagem PDF nem imagem válida (PNG, JPG, WEBP ou HEIC).$spreadsheetHint"
            )
        }
        LoadedBrokerageDocument(
            bytes = bytes,
            displayName = displayName.ifBlank { if (kind == BrokerageDocumentKind.PDF) "nota_corretagem.pdf" else "print_ordens" },
            mimeType = resolvedMime,
            kind = kind
        )
    }

    suspend fun recognizeFromUri(uri: Uri): OrderScreenshotRecognitionResult = withContext(Dispatchers.IO) {
        val loaded = loadDocumentFromUri(uri)
        recognizeLoadedDocument(loaded)
    }

    suspend fun recognizeLoadedDocument(document: LoadedBrokerageDocument): OrderScreenshotRecognitionResult =
        withContext(Dispatchers.IO) {
            when (document.kind) {
                BrokerageDocumentKind.PDF -> recognizeBrokeragePdfBytes(document.bytes, document.displayName)
                BrokerageDocumentKind.IMAGE -> recognizeOrderImageBytes(document.bytes, document.mimeType, document.displayName)
                BrokerageDocumentKind.UNSUPPORTED -> throw IllegalStateException(
                    "Formato de arquivo não suportado. Escolha uma Nota de Corretagem em PDF ou um print válido (PNG/JPG/WEBP/HEIC)."
                )
            }
        }

    private suspend fun recognizeBrokeragePdfBytes(
        pdfBytes: ByteArray,
        fileName: String
    ): OrderScreenshotRecognitionResult {
        if (pdfBytes.size > MAX_INLINE_BYTES) {
            throw IllegalStateException(
                "O PDF selecionado (${pdfBytes.size / (1024 * 1024)} MB) excede o limite de 18 MB suportado para leitura direta pela IA. Divida o arquivo por período e tente novamente."
            )
        }

        return try {
            val prompt = content {
                inlineData(pdfBytes, "application/pdf")
                text(
                    """
                    Analise integralmente TODAS as páginas desta Nota de Corretagem em PDF ($fileName) e extraia todas as operações para lançamento na carteira.

                    Regras obrigatórias de extração:
                    1. Identifique se é 'NOTA_B3' (B3 RV Listado: Ações, FIIs, FIAGROs, ETFs, BDRs) ou 'NOTA_BMF' (BM&FBOVESPA Derivativos Futuros: WIN, WDO, IND, DOL).
                    2. Preencha `brokerHeader` com o nome da corretora no topo (ex.: 'GENIAL CCTVM S/A') e `notesCount` com a quantidade total de notas/pregões distintos no PDF.
                    3. Para cada página/nota, capture `noteNumber` ('Nr.Nota' / 'Nr. nota') e `dateBr` ('Data pregão' no formato dd/MM/yyyy).
                    4. Para B3 RV (`NOTA_B3`):
                       - Converta `Especificação do título` para o ticker oficial da B3 (ex.: 'ALLOS ON ED NM' -> 'ALOS3', 'FII DEVANT CI ER' -> 'DEVA11') e salve o texto original em `specificationTitle`.
                       - Leia a coluna `C/V`: 'C' = 'COMPRA', 'V' = 'VENDA'.
                       - Leia `Tipo mercado`: 'VISTA' ou 'FRACIONARIO' (se fracionário, remova o 'F' final do ticker, ex.: 'ALOS3').
                       - Se a coluna `Obs. (*)` contiver 'D', defina `isDayTrade = true` e `orderType = 'Day Trade'`; caso contrário `isDayTrade = false` e `orderType = 'Normal'`.
                       - CONSOLIDE execuções parciais do mesmo ativo/lado/dia/nota/isDayTrade somando `quantity`, calculando `unitPrice` médio ponderado e somando `totalValue` (informe em `notes` quantas parciais foram consolidadas).
                       - Some todas as despesas/taxas dos blocos 'Resumo Financeiro' (Taxa de liquidação, Emolumentos, Taxa de Transferência, Clearing, ISS/PIS/COFINS, IRRF) em `totalFeesBrl` e a soma dos valores líquidos em `totalNetSettlementBrl`.
                    5. Para BM&F Futuros (`NOTA_BMF`):
                       - Defina `marketSegment = 'BMF_FUTUROS'`.
                       - Converta `Mercadoria` (ex.: 'WIN Z26' -> 'WINZ26', 'WDO F27' -> 'WDOF27') em `ticker` e guarde mercadoria + vencimento em `specificationTitle`.
                       - Se `Tipo do negócio` for 'DAY TRADE', defina `isDayTrade = true` e `orderType = 'Day Trade'`.
                       - Em `unitPrice`, informe o `Preço / ajuste` em pontos (ex.: 167625.0).
                       - Em `totalValue`, informe EXATAMENTE a soma do valor financeiro em REAIS (R$) da coluna `Vlr. de Operação / Ajuste` (ex.: 29.60, 49.60) — NUNCA multiplique quantidade pelos pontos do índice!
                       - Consolide compras e vendas por `(noteNumber, dateBr, ticker, side, isDayTrade)` somando contratos (`quantity`), preço médio em pontos (`unitPrice`) e valor de ajuste em reais (`totalValue`).
                       - Some `Total das despesas` + `IRRF Day Trade` de todas as páginas em `totalFeesBrl` e a soma de `Total líquido da nota` (positivo se 'C', negativo se 'D') em `totalNetSettlementBrl`.
                    6. Em notas de corretagem, todas as linhas listadas são operações efetivamente executadas (`status = 'Executada'`, `isIncomplete = false`).
                    7. Se o PDF não contiver operações financeiras, retorne `orders = []` e explique o motivo em `warnings`.
                    """.trimIndent()
                )
            }

            val response = withTimeout(PDF_TIMEOUT_MS) {
                brokeragePdfModel.generateContent(prompt)
            }
            val rawJson = response.text?.trim()
                ?: throw IllegalStateException("O modelo Gemini não retornou dados estruturados para esta nota de corretagem PDF.")

            OrderScreenshotJsonParser.parse(rawJson, defaultDocumentKind = "NOTA_B3")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao interpretar nota de corretagem PDF ($fileName)", e)
            throw e
        }
    }

    private suspend fun recognizeOrderImageBytes(
        imageBytes: ByteArray,
        detectedMime: String,
        fileName: String
    ): OrderScreenshotRecognitionResult {
        val bitmap = decodeScaledBitmapFromBytes(imageBytes, maxDimensionPx = 1600)
        val canUseDirectInline = bitmap == null &&
            detectedMime.lowercase() in GEMINI_DIRECT_IMAGE_MIMES &&
            imageBytes.size <= MAX_INLINE_BYTES

        if (bitmap == null && !canUseDirectInline) {
            throw IllegalStateException(
                "Não foi possível decodificar a imagem \"$fileName\". Salve o arquivo como PDF, PNG ou JPG e tente novamente."
            )
        }

        val imageInstructions = """
            Analise esta imagem (print de corretora/home broker ou foto de nota de corretagem) e extraia todas as ordens exibidas para lançamento na carteira.
            Regras de leitura:
            1. Defina `documentKind` como 'PRINT' (para tela de app/home broker), 'NOTA_B3' ou 'NOTA_BMF' (se for foto/print de nota SINACOR).
            2. Identifique a corretora/conta no cabeçalho (ex.: "GENIAL - SWINGTRADE") em `brokerHeader`.
            3. Em apps como Profit Mobile / Genial, o selo ao lado do ticker indica quantidade e lado:
               - "5C" = quantity 5, side "COMPRA"
               - "10C" = quantity 10, side "COMPRA"
               - "10V" = quantity 10, side "VENDA"
            4. Priorize `Preço Médio` (ou `Preço Exec.`) para `unitPrice`; use `Preço` apenas se `Preço Médio` não existir.
            5. Use `Qtd Exec.` para `quantity` e `Total Exec` para `totalValue`.
            6. Use `Fechamento`, `Data pregão` ou `Data` para `dateBr` (dd/MM/yyyy) e `Atualizado` ou `Hora` para `timeLabel`.
            7. Leia o status (`Executada`, `Parcial`, `Aberta`, `Cancelada`, `Rejeitada`).
            8. Se um cartão estiver parcialmente cortado ou sobreposto pela barra inferior (ex.: ticker e 10C visíveis, mas preço e data ocultos), registre o item com `isIncomplete = true`, `unitPrice = 0`, `totalValue = 0`, e explique em `notes` e `warnings`.
            9. Se a imagem não contiver nenhuma ordem financeira, retorne `orders = []` e explique em `warnings`.
        """.trimIndent()

        return try {
            val prompt = content {
                if (bitmap != null) {
                    image(bitmap)
                } else {
                    inlineData(imageBytes, detectedMime.lowercase())
                }
                text(imageInstructions)
            }

            val response = withTimeout(IMAGE_TIMEOUT_MS) {
                visionModel.generateContent(prompt)
            }
            val rawJson = response.text?.trim()
                ?: throw IllegalStateException("O modelo Gemini não retornou dados estruturados para este print.")

            OrderScreenshotJsonParser.parse(rawJson, defaultDocumentKind = "PRINT")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao interpretar print de ordens ($fileName)", e)
            throw e
        } finally {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    /**
     * Abre o fluxo da URI usando múltiplos mecanismos compatíveis com provedores locais,
     * Samsung My Files, Galeria, WhatsApp e arquivos virtuais (Google Drive / OneDrive).
     */
    private fun openUriInputStream(uri: Uri): InputStream? {
        val cr = context.contentResolver

        runCatching { cr.openInputStream(uri) }.getOrNull()?.let { return it }

        runCatching { cr.openAssetFileDescriptor(uri, "r")?.createInputStream() }
            .getOrNull()
            ?.let { return it }

        runCatching {
            cr.openFileDescriptor(uri, "r")?.let { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd)
            }
        }.getOrNull()?.let { return it }

        // Fallback para arquivos virtuais da nuvem (ex.: Google Drive / Docs)
        val streamTypes = runCatching { cr.getStreamTypes(uri, "*/*") }.getOrNull().orEmpty()
        val preferredTypes = buildList {
            streamTypes.firstOrNull { it.contains("pdf", ignoreCase = true) }?.let { add(it) }
            streamTypes.firstOrNull { it.startsWith("image/", ignoreCase = true) }?.let { add(it) }
            add("application/pdf")
            add("image/jpeg")
            add("image/png")
            add("image/*")
        }.distinct()

        for (mime in preferredTypes) {
            runCatching {
                cr.openTypedAssetFileDescriptor(uri, mime, null)?.createInputStream()
            }.getOrNull()?.let { return it }
        }

        return null
    }

    private fun readPrefixBytes(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 1024))
        val buffer = ByteArray(512)
        var total = 0
        while (total < maxBytes) {
            val toRead = minOf(buffer.size, maxBytes - total)
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private fun readBytesOnce(uri: Uri, maxBytes: Int): ByteArray {
        val stream = openUriInputStream(uri)
            ?: throw IllegalStateException(
                "Não foi possível acessar o arquivo selecionado. Se estiver na nuvem (Google Drive/OneDrive), baixe o PDF ou imagem para o aparelho e tente novamente."
            )
        return try {
            stream.use { input ->
                val buffer = ByteArray(16 * 1024)
                val output = ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) {
                        throw IllegalStateException(
                            "O arquivo selecionado excede o limite de ${maxBytes / (1024 * 1024)} MB. Selecione um arquivo menor ou divida o PDF por período."
                        )
                    }
                    output.write(buffer, 0, read)
                }
                val bytes = output.toByteArray()
                if (bytes.isEmpty()) {
                    throw IllegalStateException(
                        "O arquivo selecionado está vazio (0 bytes). Verifique se o download foi concluído e tente novamente."
                    )
                }
                bytes
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permissão negada ao ler URI: $uri", e)
            throw IllegalStateException(
                "Permissão negada para ler o arquivo selecionado. Selecione o PDF ou imagem novamente a partir dos arquivos locais do aparelho."
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun decodeScaledBitmapFromBytes(bytes: ByteArray, maxDimensionPx: Int): Bitmap? {
        val factoryDecoded = runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val srcWidth = boundsOpts.outWidth
            val srcHeight = boundsOpts.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return@runCatching null

            var sampleSize = 1
            while ((srcWidth / sampleSize) > maxDimensionPx || (srcHeight / sampleSize) > maxDimensionPx) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: return@runCatching null

            scaleBitmapIfNeeded(decoded, maxDimensionPx)
        }.getOrNull()

        if (factoryDecoded != null) return factoryDecoded

        // Fallback moderno (Android 9+): decodifica HEIC, HEIF, AVIF, WebP e capturas HDR Samsung
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return runCatching {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val srcW = info.size.width
                    val srcH = info.size.height
                    val longest = maxOf(srcW, srcH)
                    if (longest > maxDimensionPx && longest > 0) {
                        val scale = maxDimensionPx.toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            (srcW * scale).toInt().coerceAtLeast(1),
                            (srcH * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            }.getOrNull()
        }

        return null
    }

    private fun scaleBitmapIfNeeded(decoded: Bitmap, maxDimensionPx: Int): Bitmap {
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxDimensionPx) return decoded

        val scale = maxDimensionPx.toFloat() / longest.toFloat()
        val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled != decoded) {
            decoded.recycle()
        }
        return scaled
    }
}

