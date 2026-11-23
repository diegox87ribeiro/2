package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.domain.model.OrderScreenshotJsonParser
import com.example.domain.model.OrderScreenshotRecognitionResult
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Reconhecedor multimodal de prints de ordens de corretoras (Profit Mobile, Genial, XP, Rico,
 * Clear, BTG, Inter, NuInvest, B3) via Firebase AI Logic (`Firebase.ai` + `gemini-3.8-flash`).
 */
class OrderScreenshotRecognizer(
    private val context: Context
) {
    private val responseSchema = Schema.obj(
        properties = mapOf(
            "brokerHeader" to Schema.string(
                description = "Nome da corretora ou conta exibida no topo do print (ex.: 'GENIAL - SWINGTRADE', 'XP', 'Profit Mobile'). Vazio se não visível."
            ),
            "warnings" to Schema.array(
                items = Schema.string(),
                description = "Alertas em português sobre ordens parcialmente cortadas, cobertas por barras ou campos ilegíveis."
            ),
            "orders" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "ticker" to Schema.string(
                            description = "Código do ativo na B3 em maiúsculas (ex.: TRXF11, EGAF11, SMRE11, VALE3, ITUB4)."
                        ),
                        "side" to Schema.enumeration(
                            values = listOf("COMPRA", "VENDA"),
                            description = "Lado da operação. Em telas Profit/Genial, selos como '5C' ou '10C' significam COMPRA e '5V' ou '10V' significam VENDA."
                        ),
                        "quantity" to Schema.double(
                            description = "Quantidade executada (ex.: 5.0 de 'Qtd Exec. 5,00' ou '5C'). Use 0 se não visível."
                        ),
                        "unitPrice" to Schema.double(
                            description = "Preço unitário em reais (priorize 'Preço Médio' ou 'Preço Exec.'; se ausente, use 'Preço'). Use 0 se não visível."
                        ),
                        "totalValue" to Schema.double(
                            description = "Valor financeiro total executado em reais ('Total Exec'). Use 0 se não visível."
                        ),
                        "dateBr" to Schema.string(
                            description = "Data da ordem no formato dd/MM/yyyy (ex.: '23/11/2026' em 'Fechamento' ou 'Data'). Vazio se não visível."
                        ),
                        "timeLabel" to Schema.string(
                            description = "Horário da execução/atualização (ex.: '14:13:21' em 'Atualizado'). Vazio se não visível."
                        ),
                        "status" to Schema.string(
                            description = "Status exatamente como aparece no print (ex.: 'Executada', 'Parcial', 'Aberta', 'Cancelada')."
                        ),
                        "orderType" to Schema.string(
                            description = "Tipo da ordem (ex.: 'Limite', 'Mercado', 'Stop'). Vazio se não visível."
                        ),
                        "isIncomplete" to Schema.boolean(
                            description = "true se o cartão da ordem estiver cortado ou coberto pela barra de navegação impedindo leitura de preço, quantidade ou data."
                        ),
                        "notes" to Schema.string(
                            description = "Observação curta em português (ex.: estratégia 'Normal', ou aviso de cartão cortado na parte inferior)."
                        )
                    )
                ),
                description = "Lista de ordens visíveis no print, na ordem de cima para baixo."
            )
        )
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
                    "Você é um leitor especializado em telas de ordens e notas de corretagem do mercado financeiro brasileiro (B3, Profit Mobile, Nelogica, Genial, XP, Rico, Clear, BTG, Inter, NuInvest). " +
                        "Extraia com máxima precisão numérica todos os cartões ou linhas de ordem visíveis na imagem. " +
                        "Converta números no padrão brasileiro (vírgula decimal, ex.: 'R$ 52,92' -> 52.92; '5,00' -> 5.0; 'R$ 1.250,40' -> 1250.40). " +
                        "Regra crítica de segurança: NUNCA invente preço, quantidade ou data que estejam cortados ou cobertos por botões/barra inferior. " +
                        "Para cartões parcialmente cortados (ex.: apenas ticker e selo visíveis), inclua o item com isIncomplete=true e 0 nos campos não visíveis."
                )
            }
        )
    }

    suspend fun recognizeFromUri(uri: Uri): OrderScreenshotRecognitionResult = withContext(Dispatchers.IO) {
        val bitmap = decodeScaledBitmap(uri, maxDimensionPx = 1600)
            ?: throw IllegalStateException("Não foi possível abrir a imagem selecionada. Escolha um print válido (PNG/JPG).")

        try {
            val prompt = content {
                image(bitmap)
                text(
                    """
                    Analise este print de corretora/home broker e extraia todas as ordens exibidas para lançamento na carteira.
                    Regras de leitura:
                    1. Identifique a corretora/conta no cabeçalho (ex.: "GENIAL - SWINGTRADE") em `brokerHeader`.
                    2. Em apps como Profit Mobile / Genial, o selo ao lado do ticker indica quantidade e lado:
                       - "5C" = quantity 5, side "COMPRA"
                       - "10C" = quantity 10, side "COMPRA"
                       - "10V" = quantity 10, side "VENDA"
                    3. Priorize `Preço Médio` (ou `Preço Exec.`) para `unitPrice`; use `Preço` apenas se `Preço Médio` não existir.
                    4. Use `Qtd Exec.` para `quantity` e `Total Exec` para `totalValue`.
                    5. Use `Fechamento` ou `Data` para `dateBr` (dd/MM/yyyy) e `Atualizado` ou `Hora` para `timeLabel`.
                    6. Leia o status (`Executada`, `Parcial`, `Aberta`, `Cancelada`, `Rejeitada`).
                    7. Se um cartão estiver parcialmente cortado ou sobreposto pela barra inferior (ex.: ticker e 10C visíveis, mas preço e data ocultos), registre o item com `isIncomplete = true`, `unitPrice = 0`, `totalValue = 0`, e explique em `notes` e `warnings`.
                    8. Se a imagem não contiver nenhuma ordem financeira, retorne `orders = []` e explique em `warnings`.
                    """.trimIndent()
                )
            }

            val response = withTimeout(60_000L) {
                visionModel.generateContent(prompt)
            }
            val rawJson = response.text?.trim()
                ?: throw IllegalStateException("O modelo Gemini não retornou dados estruturados para este print.")

            OrderScreenshotJsonParser.parse(rawJson)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
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
