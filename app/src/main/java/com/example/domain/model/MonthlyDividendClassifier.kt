package com.example.domain.model

import com.example.data.local.DividendEventEntity
import com.example.data.local.PortfolioOperationEntity
import java.util.Locale

data class MonthlyDividendEvaluation(
    val paysMonthlyDividends: Boolean,
    val resolvedDataComDay: Int?,
    val group: DataComGroup?,
    val reason: String
)

/**
 * Classificador objetivo de recorrência mensal de dividendos.
 *
 * Regra de negócio do INVEST AI:
 * - Todos os ativos são lançados SEM classificação de grupo (G1, G2, G3 ou G4).
 * - Um ativo SÓ recebe classificação de grupo (G1–G4) quando há evidência de que
 *   paga dividendos MENSALMENTE (histórico de proventos ou consistência ao vivo)
 *   e possui um dia Data-Com válido (1..31).
 */
object MonthlyDividendClassifier {

    /**
     * Avalia se [ticker] paga dividendos mensalmente e determina o grupo G1–G4 relacionado.
     *
     * Critérios objetivos de pagamento mensal:
     * 1) Histórico real do usuário (eventos RECEBIDO/CONFIRMADO ou operações PROVENTO):
     *    - >= 3 meses calendário consecutivos com pagamento positivo, OU
     *    - >= 10 meses distintos com pagamento nos últimos 12 meses.
     * 2) Dados de mercado ao vivo ([liveQuote]):
     *    - `dividendConsistencyMonths >= 10`, OU
     *    - `dividendHistory6m` com >= 5 meses positivos em 6.
     *
     * Se não houver comprovação de pagamento mensal (ex.: trimestral = 4/12, semestral = 2/12,
     * anual = 1/12 ou sem histórico mensal), retorna `paysMonthlyDividends = false` e `group = null`.
     */
    fun evaluate(
        ticker: String,
        liveQuote: LiveMarketQuote? = null,
        dividendEvents: List<DividendEventEntity> = emptyList(),
        operations: List<PortfolioOperationEntity> = emptyList(),
        existingDataComDay: Int? = null
    ): MonthlyDividendEvaluation {
        val cleanTicker = ticker.trim().uppercase(Locale.ROOT)
        if (cleanTicker.isBlank()) {
            return MonthlyDividendEvaluation(false, null, null, "Ticker vazio.")
        }

        // 1) Coleta meses distintos (year * 12 + (month - 1)) com proventos efetivos/confirmados do ativo
        val paidMonthIndices = mutableSetOf<Int>()
        var latestEventExDay: Int? = null
        var latestEventSortYmd = -1

        dividendEvents
            .filter {
                !it.isDeleted &&
                    it.assetTicker.trim().equals(cleanTicker, ignoreCase = true) &&
                    it.totalAmount > 0.0 &&
                    (it.status.equals(DividendStatus.RECEIVED.name, ignoreCase = true) ||
                        it.status.equals(DividendStatus.CONFIRMED.name, ignoreCase = true))
            }
            .forEach { ev ->
                val payYmd = DividendHistoryEngine.parseDateToYmd(ev.paymentDate)
                    ?: DividendHistoryEngine.parseDateToYmd(ev.exDate)
                if (payYmd != null) {
                    paidMonthIndices.add(payYmd.first * 12 + (payYmd.second - 1))
                    val sortInt = DividendHistoryEngine.ymdToInt(payYmd.first, payYmd.second, payYmd.third)
                    val exYmd = DividendHistoryEngine.parseDateToYmd(ev.exDate)
                    if (exYmd != null && sortInt >= latestEventSortYmd) {
                        latestEventSortYmd = sortInt
                        latestEventExDay = exYmd.third.takeIf { it in 1..31 }
                    }
                }
            }

        operations
            .filter {
                it.ticker.trim().equals(cleanTicker, ignoreCase = true) &&
                    it.operationType.equals(OperationType.PROVENTO.name, ignoreCase = true) &&
                    it.totalValue > 0.0
            }
            .forEach { op ->
                val ymd = DividendHistoryEngine.parseDateToYmd(op.dateIso)
                if (ymd != null) {
                    paidMonthIndices.add(ymd.first * 12 + (ymd.second - 1))
                }
            }

        val maxConsecutiveMonths = computeMaxConsecutiveRun(paidMonthIndices)
        val distinctInLast12Months = computeDistinctInLatestWindow(paidMonthIndices, windowMonths = 12)
        val provenMonthlyByUserHistory = maxConsecutiveMonths >= 3 || distinctInLast12Months >= 10

        // 2) Avalia evidência de pagamento mensal nos dados de mercado ao vivo
        val liveConsistency = liveQuote?.dividendConsistencyMonths
        val livePositive6m = liveQuote?.dividendHistory6m?.count { it > 0.0 } ?: 0
        val provenMonthlyByLiveQuote = when {
            liveConsistency != null -> liveConsistency >= 10 || livePositive6m >= 5
            liveQuote != null && liveQuote.dividendHistory6m.size >= 5 -> livePositive6m >= 5
            else -> false
        }

        val paysMonthly = provenMonthlyByUserHistory || provenMonthlyByLiveQuote

        if (!paysMonthly) {
            val reason = when {
                liveConsistency != null ->
                    "Consistência ao vivo de $liveConsistency/12 meses (não é pagador mensal)."
                paidMonthIndices.isNotEmpty() ->
                    "Histórico atual com ${paidMonthIndices.size} mês(es) (máx. $maxConsecutiveMonths consecutivo(s)) — sem padrão mensal confirmado."
                else ->
                    "Sem comprovação de pagamento mensal de dividendos."
            }
            return MonthlyDividendEvaluation(
                paysMonthlyDividends = false,
                resolvedDataComDay = null,
                group = null,
                reason = reason
            )
        }

        val resolvedDay = liveQuote?.dataComDay?.takeIf { it in 1..31 }
            ?: latestEventExDay?.takeIf { it in 1..31 }
            ?: existingDataComDay?.takeIf { it in 1..31 }

        val group = DataComGroup.fromDay(resolvedDay, isCyclical = true)
        val reason = buildString {
            append(
                if (provenMonthlyByLiveQuote) {
                    "Pagador mensal identificado ao vivo (${liveConsistency ?: livePositive6m} pagamentos recorrentes)"
                } else {
                    "Pagador mensal identificado pelo histórico ($maxConsecutiveMonths mês(es) consecutivos)"
                }
            )
            if (group != null && resolvedDay != null) {
                append(" • Data-Com dia $resolvedDay → Grupo ${group.code}")
            } else {
                append(" • Aguardando dia Data-Com para definir grupo G1–G4")
            }
        }

        return MonthlyDividendEvaluation(
            paysMonthlyDividends = true,
            resolvedDataComDay = resolvedDay,
            group = group,
            reason = reason
        )
    }

    private fun computeMaxConsecutiveRun(monthIndices: Set<Int>): Int {
        if (monthIndices.isEmpty()) return 0
        val sorted = monthIndices.sorted()
        var best = 1
        var current = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] == sorted[i - 1] + 1) {
                current++
                if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }

    private fun computeDistinctInLatestWindow(monthIndices: Set<Int>, windowMonths: Int): Int {
        if (monthIndices.isEmpty()) return 0
        val latest = monthIndices.maxOrNull() ?: return 0
        val minAllowed = latest - (windowMonths - 1)
        return monthIndices.count { it in minAllowed..latest }
    }
}
