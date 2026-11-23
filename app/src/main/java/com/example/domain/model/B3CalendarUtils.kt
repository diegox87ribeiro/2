package com.example.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Calendário de dias úteis da B3 (fins de semana + feriados nacionais brasileiros fixos e móveis).
 * Garante que cálculos de Data-Com e Data-Ex nunca caiam em sábados, domingos ou feriados sem pregão.
 */
object B3CalendarUtils {

    private val saoPauloTz: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")

    fun isB3BusinessDay(cal: Calendar): Boolean {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
        return !isBrazilianNationalHoliday(
            year = cal.get(Calendar.YEAR),
            monthZeroBased = cal.get(Calendar.MONTH),
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Se a data cair em fim de semana ou feriado nacional da B3, recua para o dia útil anterior.
     * Usado para Data-Com (regra de fechamento de pregão da B3: ex. último dia útil do mês).
     */
    fun adjustToPreviousOrSameB3BusinessDay(cal: Calendar): Calendar {
        val adjusted = startOfDay(cal)
        while (!isB3BusinessDay(adjusted)) {
            adjusted.add(Calendar.DAY_OF_YEAR, -1)
        }
        return adjusted
    }

    /**
     * Retorna o próximo dia útil da B3 estritamente posterior à data informada.
     * Usado para Data-Ex a partir da Data-Com (1º pregão seguinte sem direito ao provento).
     */
    fun nextB3BusinessDayAfter(cal: Calendar): Calendar {
        val next = startOfDay(cal)
        next.add(Calendar.DAY_OF_YEAR, 1)
        while (!isB3BusinessDay(next)) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return next
    }

    /**
     * Retorna o dia útil da B3 estritamente anterior à data informada.
     * Usado para derivar a Data-Com oficial a partir de uma Data-Ex exata (ex.: 03/11 após Finados -> 30/10).
     */
    fun previousB3BusinessDayBefore(cal: Calendar): Calendar {
        val prev = startOfDay(cal)
        prev.add(Calendar.DAY_OF_YEAR, -1)
        while (!isB3BusinessDay(prev)) {
            prev.add(Calendar.DAY_OF_YEAR, -1)
        }
        return prev
    }

    /**
     * Deriva o dia do mês da Data-Com a partir de um timestamp Unix (segundos) da Data-Ex (ex.: Yahoo Finance).
     */
    fun deriveDataComDayFromExEpochSeconds(exEpochSeconds: Long): Int? {
        if (exEpochSeconds <= 0L) return null
        val exCal = Calendar.getInstance(saoPauloTz).apply {
            timeInMillis = exEpochSeconds * 1000L
        }
        val comCal = previousB3BusinessDayBefore(exCal)
        return comCal.get(Calendar.DAY_OF_MONTH).takeIf { it in 1..31 }
    }

    fun isBrazilianNationalHoliday(year: Int, monthZeroBased: Int, day: Int): Boolean {
        val month = monthZeroBased + 1
        // Feriados nacionais fixos em que a B3 não abre pregão
        val isFixed = when {
            month == 1 && day == 1 -> true   // Confraternização Universal
            month == 4 && day == 21 -> true  // Tiradentes
            month == 5 && day == 1 -> true   // Dia do Trabalho
            month == 9 && day == 7 -> true   // Independência do Brasil
            month == 10 && day == 12 -> true // Nossa Senhora Aparecida
            month == 11 && day == 2 -> true  // Finados
            month == 11 && day == 15 -> true // Proclamação da República
            month == 11 && day == 20 -> true // Dia Nacional de Zumbi e da Consciência Negra (Lei 14.759/2023)
            month == 12 && day == 25 -> true // Natal
            month == 12 && day == 31 -> true // Sem pregão na B3 no dia 31/12
            else -> false
        }
        if (isFixed) return true

        // Feriados móveis vinculados à Páscoa (Carnaval Seg/Ter, Sexta-Feira Santa, Corpus Christi)
        val easter = computeEasterSunday(year)
        val movableOffsets = intArrayOf(-48, -47, -2, 60)
        for (offset in movableOffsets) {
            val hol = (easter.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            if (hol.get(Calendar.MONTH) == monthZeroBased && hol.get(Calendar.DAY_OF_MONTH) == day) {
                return true
            }
        }
        return false
    }

    /**
     * Algoritmo gregoriano anônimo (Meeus/Jones/Butcher) para cálculo do Domingo de Páscoa.
     */
    private fun computeEasterSunday(year: Int): Calendar {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31 // 3 = Março, 4 = Abril
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun startOfDay(source: Calendar): Calendar {
        return (source.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
