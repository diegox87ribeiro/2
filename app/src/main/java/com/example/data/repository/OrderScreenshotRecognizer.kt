package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
        private const val MAX_PDF_INLINE_BYTES = 18 * 1024 * 1024 // Limite seguro (< 20 MB Firebase AI Logic)
        private const val IMAGE_TIMEOUT_MS = 60_000L
        private const val PDF_TIMEOUT_MS = 120_000L
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
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(5)
                val read = stream.read(header)
                read >= 4 &&
                    header[0] == '%'.code.toByte() &&
                    header[1] == 'P'.code.toByte() &&
                    header[2] == 'D'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
            } ?: false
        }.getOrDefault(false)
    }

    suspend fun recognizeFromUri(uri: Uri): OrderScreenshotRecognitionResult = withContext(Dispatchers.IO) {
        if (isPdfUri(uri)) {
            recognizeBrokeragePdf(uri)
        } else {
            recognizeOrderImage(uri)
        }
    }

    private suspend fun recognizeBrokeragePdf(uri: Uri): OrderScreenshotRecognitionResult {
        val pdfBytes = readBytesWithinLimit(uri, MAX_PDF_INLINE_BYTES)
        val fileName = resolveDisplayName(uri).ifBlank { "nota_corretagem.pdf" }

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

    private suspend fun recognizeOrderImage(uri: Uri): OrderScreenshotRecognitionResult {
        val bitmap = decodeScaledBitmap(uri, maxDimensionPx = 1600)
            ?: throw IllegalStateException(
                "Não foi possível abrir o arquivo selecionado. Escolha uma Nota de Corretagem em PDF ou um print válido (PNG/JPG)."
            )

        return try {
            val prompt = content {
                image(bitmap)
                text(
                    """
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
                )
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
            Log.e(TAG, "Falha ao interpretar print de ordens", e)
            throw e
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun readBytesWithinLimit(uri: Uri, maxBytes: Int): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir o PDF selecionado. Verifique se o arquivo ainda está disponível no dispositivo.")
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
                        "O PDF selecionado excede o limite de 18 MB suportado para leitura direta pela IA. Divida o arquivo por período e tente novamente."
                    )
                }
                output.write(buffer, 0, read)
            }
            val bytes = output.toByteArray()
            if (bytes.isEmpty()) {
                throw IllegalStateException("O arquivo PDF selecionado está vazio.")
            }
            return bytes
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

    private fun decodeScaledBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return null

        val srcWidth = boundsOpts.outWidth
        val srcHeight = boundsOpts.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while ((srcWidth / sampleSize) > maxDimensionPx || (srcHeight / sampleSize) > maxDimensionPx) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

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
