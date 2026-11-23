package com.example.domain.model

import java.util.Calendar
import java.util.Locale

enum class DividendDateEventType(val label: String) {
    DATA_COM("Data-Com"),
    DATA_EX("Data-Ex")
}

data class UpcomingDividendDateAlert(
    val ticker: String,
    val assetName: String,
    val eventType: DividendDateEventType,
    val eventDayOfMonth: Int,
    val eventDateFormatted: String,
    val daysUntil: Int,
    val quantity: Double,
    val projectedDividendBrl: Double,
    val dedupeKey: String
)

object DividendDateAlertEvaluator {

    fun findUpcomingAlerts(
        realPositions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>,
        liveQuotesByTicker: Map<String, LiveMarketQuote> = emptyMap(),
        leadDays: Int = 3,
        notifyDataCom: Boolean = true,
        notifyDataEx: Boolean = true,
        referenceDateMillis: Long = System.currentTimeMillis()
    ): List<UpcomingDividendDateAlert> {
        val safeLead = leadDays.coerceIn(0, 31)
        return findNextEventsForPortfolio(
            realPositions = realPositions,
            marketAssets = marketAssets,
            liveQuotesByTicker = liveQuotesByTicker,
            notifyDataCom = notifyDataCom,
            notifyDataEx = notifyDataEx,
            referenceDateMillis = referenceDateMillis
        ).filter { it.daysUntil in 0..safeLead }
    }

    fun findNextEventsForPortfolio(
        realPositions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>,
        liveQuotesByTicker: Map<String, LiveMarketQuote> = emptyMap(),
        notifyDataCom: Boolean = true,
        notifyDataEx: Boolean = true,
        referenceDateMillis: Long = System.currentTimeMillis()
    ): List<UpcomingDividendDateAlert> {
        if (realPositions.isEmpty() || (!notifyDataCom && !notifyDataEx)) return emptyList()

        val catalogByTicker = marketAssets.associateBy { it.ticker.trim().uppercase() }
        val liveByTicker = liveQuotesByTicker.entries.associate { it.key.trim().uppercase() to it.value }
        val groupedPositions = realPositions
            .filter { it.quantity > 0.0 && it.ticker.isNotBlank() && !it.ticker.equals("CAIXA", ignoreCase = true) }
            .groupBy { it.ticker.trim().uppercase() }

        val todayStart = startOfDayCalendar(referenceDateMillis)
        val events = mutableListOf<UpcomingDividendDateAlert>()

        for ((ticker, lots) in groupedPositions) {
            val firstLot = lots.first()
            val qty = lots.sumOf { it.quantity }
            val catalog = catalogByTicker[ticker]
            val live = liveByTicker[ticker]

            val dataComDay = firstLot.dataComDay ?: live?.dataComDay ?: catalog?.dataComDay
            val explicitDataExDay = live?.dataExDay
            val perUnitDiv = live?.lastDividendBrl?.takeIf { it > 0.0 }
                ?: catalog?.lastDividendBrl?.takeIf { it > 0.0 }
                ?: 0.0
            val projectedBrl = qty * perUnitDiv
            val displayName = firstLot.name.ifBlank { live?.companyName ?: catalog?.name ?: ticker }

            if (notifyDataCom && dataComDay != null && dataComDay in 1..31) {
                val nextComCal = nextMonthlyOccurrence(todayStart, dataComDay)
                val days = daysBetween(todayStart, nextComCal)
                val dateStr = formatCalDate(nextComCal)
                events.add(
                    UpcomingDividendDateAlert(
                        ticker = ticker,
                        assetName = displayName,
                        eventType = DividendDateEventType.DATA_COM,
                        eventDayOfMonth = nextComCal.get(Calendar.DAY_OF_MONTH),
                        eventDateFormatted = dateStr,
                        daysUntil = days,
                        quantity = qty,
                        projectedDividendBrl = projectedBrl,
                        dedupeKey = "$ticker:DATA_COM:$dateStr"
                    )
                )
            }

            if (notifyDataEx) {
                val nextExCal = when {
                    explicitDataExDay != null && explicitDataExDay in 1..31 -> {
                        nextMonthlyOccurrence(todayStart, explicitDataExDay)
                    }
                    dataComDay != null && dataComDay in 1..31 -> {
                        nextDataExFromDataCom(todayStart, dataComDay)
                    }
                    else -> null
                }

                if (nextExCal != null) {
                    val days = daysBetween(todayStart, nextExCal)
                    val dateStr = formatCalDate(nextExCal)
                    events.add(
                        UpcomingDividendDateAlert(
                            ticker = ticker,
                            assetName = displayName,
                            eventType = DividendDateEventType.DATA_EX,
                            eventDayOfMonth = nextExCal.get(Calendar.DAY_OF_MONTH),
                            eventDateFormatted = dateStr,
                            daysUntil = days,
                            quantity = qty,
                            projectedDividendBrl = projectedBrl,
                            dedupeKey = "$ticker:DATA_EX:$dateStr"
                        )
                    )
                }
            }
        }

        return events.sortedWith(
            compareBy<UpcomingDividendDateAlert> { it.daysUntil }
                .thenBy { it.eventType.ordinal }
                .thenBy { it.ticker }
        )
    }

    internal fun nextMonthlyOccurrence(todayStart: Calendar, targetDay: Int): Calendar {
        val thisMonth = buildClampedDate(
            year = todayStart.get(Calendar.YEAR),
            month = todayStart.get(Calendar.MONTH),
            day = targetDay
        )
        if (!thisMonth.before(todayStart)) {
            return thisMonth
        }
        val nextMonthBase = (todayStart.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        return buildClampedDate(
            year = nextMonthBase.get(Calendar.YEAR),
            month = nextMonthBase.get(Calendar.MONTH),
            day = targetDay
        )
    }

    internal fun nextDataExFromDataCom(todayStart: Calendar, dataComDay: Int): Calendar {
        // Verifica ciclo do mês anterior, mês atual e próximo mês para não perder Data-Ex
        // logo após uma Data-Com no fim do mês (ex.: Data-Com dia 30/31 -> Data-Ex dia 1..3)
        val candidates = (-1..1).map { monthOffset ->
            val base = (todayStart.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, monthOffset)
            }
            val comDate = buildClampedDate(
                year = base.get(Calendar.YEAR),
                month = base.get(Calendar.MONTH),
                day = dataComDay
            )
            nextBusinessDayAfter(comDate)
        }
        return candidates
            .filter { !it.before(todayStart) }
            .minByOrNull { it.timeInMillis }
            ?: nextBusinessDayAfter(nextMonthlyOccurrence(todayStart, dataComDay))
    }

    internal fun nextBusinessDayAfter(date: Calendar): Calendar {
        val next = (date.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        while (
            next.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            next.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        ) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return startOfDayCalendar(next.timeInMillis)
    }

    private fun buildClampedDate(year: Int, month: Int, day: Int): Calendar {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, maxDay))
        return cal
    }

    private fun startOfDayCalendar(millis: Long): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun daysBetween(start: Calendar, end: Calendar): Int {
        val diffMs = end.timeInMillis - start.timeInMillis
        val dayMs = 24L * 60L * 60L * 1000L
        return kotlin.math.round(diffMs.toDouble() / dayMs.toDouble()).toInt().coerceAtLeast(0)
    }

    private fun formatCalDate(cal: Calendar): String {
        return String.format(
            Locale.US,
            "%02d/%02d/%04d",
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
    }
}
