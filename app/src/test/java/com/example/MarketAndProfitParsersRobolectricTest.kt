package com.example

import com.example.data.repository.LiveQuoteParsers
import com.example.data.repository.ProfitChartParsers
import com.example.domain.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarketAndProfitParsersRobolectricTest {

    @Test
    fun `live quote parsers decode B3, Yahoo SA, Brapi, ADVFN payloads and calculate RSI14`() {
        val b3Json = """
            {
              "Msg": { "dtTm": "2026-11-19 16:40:00" },
              "TradgFlr": {
                "date": "2026-11-19",
                "scty": {
                  "symb": "VALE3",
                  "desc": "VALE        ON      NM",
                  "SctyQtn": {
                    "curPrc": 65.42,
                    "prcFlcn": 1.35,
                    "opngPric": 64.50,
                    "minPric": 64.10,
                    "maxPric": 65.80
                  },
                  "lstQtn": [
                    { "closPric": 64.80 },
                    { "closPric": 65.42 }
                  ]
                }
              }
            }
        """.trimIndent()
        val b3 = LiveQuoteParsers.parseB3FluctuationJson("VALE3", b3Json)!!
        assertEquals(65.42, b3.price!!, 0.001)
        assertEquals(1.35, b3.changePercent1d!!, 0.001)
        assertEquals("VALE ON NM", b3.companyName)

        val yahooJson = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "longName": "Vulcabras S.A.",
                    "regularMarketPrice": 12.40,
                    "regularMarketPreviousClose": 12.00,
                    "regularMarketDayHigh": 12.55,
                    "regularMarketDayLow": 11.95,
                    "regularMarketVolume": 500000
                  },
                  "indicators": {
                    "quote": [{
                      "close": [10.0,10.2,10.4,10.3,10.5,10.8,11.0,11.1,11.2,11.4,11.5,11.7,11.9,12.0,12.1,12.4],
                      "low": [9.9,10.1,10.3,10.2,10.4,10.7,10.9,11.0,11.1,11.3,11.4,11.6,11.8,11.9,12.0,12.1],
                      "high": [10.1,10.3,10.5,10.4,10.6,10.9,11.1,11.2,11.3,11.5,11.6,11.8,12.0,12.1,12.2,12.55]
                    }]
                  }
                }]
              }
            }
        """.trimIndent()
        val yahoo = LiveQuoteParsers.parseYahooChartJson("VULC3", yahooJson)!!
        assertEquals(12.40, yahoo.price!!, 0.001)
        assertEquals(3.333, yahoo.changePercent1d!!, 0.01)
        assertEquals(6.2, yahoo.dailyVolumeBrlMillions!!, 0.01)
        val rsi = LiveQuoteParsers.calculateRsi14(yahoo.closesHistory)
        assertTrue(rsi != null && rsi in 70.0..100.0)

        val advfnHtml = """<span id="quoteElementPiece1">6,75</span><span id="quoteElementPiece2">(+2,15%)</span>"""
        val advfn = LiveQuoteParsers.parseAdvfnQuoteHtml("WIZC3", advfnHtml)!!
        assertEquals(6.75, advfn.price!!, 0.001)
        assertEquals(2.15, advfn.changePercent1d!!, 0.001)

        assertEquals("VULC3", LiveQuoteParsers.normalizeTicker(" vulc3.sa "))
        assertEquals("WIZC3", LiveQuoteParsers.normalizeTicker("BOV:WIZC3"))
    }

    @Test
    fun `profitChart parsers decode JSON and CSV executed operations and skip canceled orders`() {
        val json = """
            {
              "executions": [
                {
                  "orderId": "ORD-1001",
                  "ticker": "VALE3",
                  "side": "COMPRA",
                  "status": "EXECUTADA",
                  "executedQty": 100,
                  "avgPrice": 65.40,
                  "executedAt": "19/11/2026 14:10",
                  "broker": "XP"
                },
                {
                  "orderId": "ORD-1002",
                  "ticker": "PETR4",
                  "side": "VENDA",
                  "status": "CANCELADA",
                  "executedQty": 50,
                  "avgPrice": 38.00
                }
              ]
            }
        """.trimIndent()
        val jsonOutcome = ProfitChartParsers.parsePayload(json, "ProfitChart API")
        assertEquals(1, jsonOutcome.executions.size)
        assertEquals(1, jsonOutcome.skippedNonExecutedCount)
        assertEquals("VALE3", jsonOutcome.executions.first().ticker)
        assertEquals(OperationType.COMPRA, jsonOutcome.executions.first().operationType)
        assertEquals(6540.0, jsonOutcome.executions.first().totalValue, 0.01)

        val csv = """
            Ordem;Ativo;Lado;Status;Qtd Executada;Preço Médio;Atualização;Corretora
            7781;WIZC3;C;Executada;50;6,45;19/11/2026 14:15;Clear
            7782;ITUB4;V;Rejeitada;20;35,00;19/11/2026 14:16;Clear
        """.trimIndent()
        val csvOutcome = ProfitChartParsers.parsePayload(csv, "ProfitChart CSV")
        assertEquals(1, csvOutcome.executions.size)
        assertEquals(1, csvOutcome.skippedNonExecutedCount)
        assertEquals("WIZC3", csvOutcome.executions.first().ticker)
        assertEquals(6.45, csvOutcome.executions.first().unitPrice, 0.001)
    }

    @Test
    fun `live quote parsers extract dividends, DY 12m, PVP and Data-Com from Yahoo events and Investidor10`() {
        val yahooWithDivs = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "longName": "Maxi Renda FII",
                    "regularMarketPrice": 9.80,
                    "regularMarketPreviousClose": 9.75
                  },
                  "indicators": { "quote": [{ "close": [9.70, 9.75, 9.80] }] },
                  "events": {
                    "dividends": {
                      "1759287600": { "amount": 0.10, "date": 1759287600 },
                      "1761966000": { "amount": 0.10, "date": 1761966000 }
                    }
                  }
                }]
              }
            }
        """.trimIndent()
        val parsedYahoo = LiveQuoteParsers.parseYahooChartJson("MXRF11", yahooWithDivs)!!
        assertEquals(0.10, parsedYahoo.lastDividendBrl!!, 0.001)
        assertEquals(listOf(0.10, 0.10), parsedYahoo.dividendHistory6m)
        assertTrue((parsedYahoo.dividendYield12m ?: 0.0) > 2.0)

        val inv10Html = """
            <div class="_card"><span class="title">Cotação</span><div class="_card-body"><span class="value">R$ 9,82</span></div></div>
            <div class="_card"><span class="title">DY (12M)</span><div class="_card-body"><span class="value">12,45%</span></div></div>
            <div class="_card"><span class="title">P/VP</span><div class="_card-body"><span class="value">1,02</span></div></div>
            <table>
              <tr><td>Dividendos</td><td>30/10/26</td><td>16/11/26</td><td>R$ 0,10</td></tr>
              <tr><td>Dividendos</td><td>30/09/26</td><td>15/10/26</td><td>R$ 0,10</td></tr>
            </table>
        """.trimIndent()
        val parsedInv10 = LiveQuoteParsers.parseInvestidor10Html("MXRF11", inv10Html)!!
        assertEquals(9.82, parsedInv10.price!!, 0.001)
        assertEquals(12.45, parsedInv10.dividendYield12m!!, 0.001)
        assertEquals(1.02, parsedInv10.priceToBook!!, 0.001)
        assertEquals(0.10, parsedInv10.lastDividendBrl!!, 0.001)
        assertEquals(30, parsedInv10.dataComDay)
        assertEquals(16, parsedInv10.paymentDay)

        val catalogMxrf = com.example.data.repository.MarketCatalogRepository.catalogAssets.first { it.ticker == "MXRF11" }
        assertEquals(30, catalogMxrf.dataComDay)
        assertEquals(0.10, catalogMxrf.lastDividendBrl, 0.001)

        val liveQuote = com.example.domain.model.LiveMarketQuote(
            ticker = "MXRF11",
            companyName = "Maxi Renda FII",
            price = 9.82,
            changePercent1d = 0.5,
            priceToBook = 1.02,
            dividendYield12m = 12.45,
            lastDividendBrl = 0.10,
            dividendHistory6m = listOf(0.10, 0.10, 0.10, 0.10, 0.10, 0.10),
            dividendConsistencyMonths = 12,
            dataComDay = 30,
            paymentDay = 16,
            primarySourceLabel = "Investidor10 + B3",
            consultedSources = listOf("Investidor10", "B3"),
            fetchedAtMillis = 1_763_650_000_000L,
            marketTimestampLabel = "20/11/2026 14:37",
            b3PageUrl = "https://www.b3.com.br",
            advfnPageUrl = "https://br.advfn.com"
        )
        val merged = com.example.data.repository.LiveMarketDataRepository.mergeLiveQuoteIntoMarketAsset(catalogMxrf, liveQuote)
        assertEquals(9.82, merged.price, 0.001)
        assertEquals(1.02, merged.pvp, 0.001)
        assertEquals(12.45, merged.dy12m, 0.001)
        assertEquals(0.10, merged.lastDividendBrl, 0.001)
        assertEquals(30, merged.dataComDay)
        assertEquals(16, merged.paymentDay)
        assertEquals(false, merged.isDemoData)
    }

    @Test
    fun `parsers block PVP card bleed like 59360,38 and avoid bogus Yahoo G1 Data-Com for FIIs`() {
        val corruptedInv10Html = """
            <div class="_card"><span class="title">Cotação</span><div class="_card-body"><span class="value">R$ 56,30</span></div></div>
            <div class="_card"><span class="title">P/VP</span><div class="_card-body"><span class="value">-</span></div></div>
            <div class="_card"><span class="title">Liquidez Diária</span><div class="_card-body"><span class="value">59.360,38</span></div></div>
            <table>
              <tr><td><span>Dividendos</span></td><td><span>30/10/2026</span></td><td><span>16/11/2026</span></td><td><span>R$ 1,00</span></td></tr>
            </table>
        """.trimIndent()
        val parsedInv10 = LiveQuoteParsers.parseInvestidor10Html("SMRE11", corruptedInv10Html)!!
        assertEquals(56.30, parsedInv10.price!!, 0.001)
        org.junit.Assert.assertNull(parsedInv10.priceToBook)
        assertEquals(30, parsedInv10.dataComDay)
        assertEquals(16, parsedInv10.paymentDay)
        assertEquals(1.00, parsedInv10.lastDividendBrl!!, 0.001)

        val yahooSingleDivFii = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "longName": "Fundo De Investimento Imobiliario Smart Real Estate",
                    "regularMarketPrice": 56.30,
                    "regularMarketPreviousClose": 56.10
                  },
                  "indicators": { "quote": [{ "close": [56.0, 56.10, 56.30] }] },
                  "events": {
                    "dividends": {
                      "1762128000": { "amount": 1.00, "date": 1762128000 }
                    }
                  }
                }]
              }
            }
        """.trimIndent()
        val parsedYahoo = LiveQuoteParsers.parseYahooChartJson("SMRE11", yahooSingleDivFii)!!
        assertEquals(56.30, parsedYahoo.price!!, 0.001)
        org.junit.Assert.assertNull(parsedYahoo.dataComDay)
        org.junit.Assert.assertNull(parsedYahoo.dividendYield12m)

        val badQuote = com.example.domain.model.LiveMarketQuote(
            ticker = "SMRE11",
            companyName = "Smart Real Estate FII",
            price = 56.30,
            changePercent1d = 0.35,
            priceToBook = 59360.38,
            dividendYield12m = 1.78,
            primarySourceLabel = "Yahoo Finance",
            consultedSources = listOf("Yahoo Finance"),
            fetchedAtMillis = 1_763_650_000_000L,
            marketTimestampLabel = "20/11/2026 15:59",
            b3PageUrl = "https://www.b3.com.br",
            advfnPageUrl = "https://br.advfn.com"
        )
        val mergedNew = com.example.data.repository.LiveMarketDataRepository.mergeLiveQuoteIntoMarketAsset(null, badQuote)
        assertEquals(0.0, mergedNew.pvp, 0.001)
    }
}
