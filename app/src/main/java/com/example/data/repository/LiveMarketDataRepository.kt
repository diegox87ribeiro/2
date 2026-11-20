package com.example.data.repository

import com.example.domain.model.AssetClass
import com.example.domain.model.LiveMarketQuote
import com.example.domain.model.MarketAsset
import com.example.domain.model.MarketQuoteProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class PartialQuoteResult(
    val sourceName: String,
    val ticker: String,
    val companyName: String? = null,
    val price: Double? = null,
    val changePercent1d: Double? = null,
    val openPrice: Double? = null,
    val dayLow: Double? = null,
    val dayHigh: Double? = null,
    val previousClose: Double? = null,
    val high52w: Double? = null,
    val low52w: Double? = null,
    val dailyVolumeBrlMillions: Double? = null,
    val closesHistory: List<Double> = emptyList(),
    val lowsHistory: List<Double> = emptyList(),
    val highsHistory: List<Double> = emptyList(),
    val priceToEarnings: Double? = null,
    val priceToBook: Double? = null,
    val dividendYield12m: Double? = null,
    val roePercent: Double? = null,
    val marketCapBrl: Double? = null,
    val marketTimestampLabel: String? = null
)

object LiveQuoteParsers {

    private val brLocale = Locale("pt", "BR")

    fun normalizeTicker(raw: String): String {
        return raw.trim()
            .uppercase(Locale.ROOT)
            .removePrefix("BOV:")
            .removePrefix("BVMF:")
            .removeSuffix(".SA")
            .replace(Regex("[^A-Z0-9]"), "")
    }

    fun inferAssetClassFromTicker(ticker: String): AssetClass {
        return when {
            ticker.endsWith("11") && (ticker.startsWith("KNCA") || ticker.startsWith("RZAG") ||
                ticker.startsWith("VGIA") || ticker.startsWith("XPCA") || ticker.startsWith("FGAA") ||
                ticker.startsWith("RURA") || ticker.startsWith("CPTR")) -> AssetClass.FIAGRO
            ticker.endsWith("11") && (ticker.startsWith("IVVB") || ticker.startsWith("BOVA") ||
                ticker.startsWith("SMAL") || ticker.startsWith("HASH") || ticker.startsWith("DIVO") ||
                ticker.startsWith("SPXI") || ticker.startsWith("NASD")) -> AssetClass.ETF
            ticker.endsWith("11") -> AssetClass.FII
            else -> AssetClass.ACOES_BR
        }
    }

    fun parseB3FluctuationJson(ticker: String, jsonText: String): PartialQuoteResult? {
        return runCatching {
            val root = JSONObject(jsonText)
            val tradgFlr = root.optJSONObject("TradgFlr") ?: return null
            val scty = tradgFlr.optJSONObject("scty") ?: return null
            val qtn = scty.optJSONObject("SctyQtn") ?: return null

            val curPrc = qtn.optDouble("curPrc").takeIf { !it.isNaN() && it > 0.0 } ?: return null
            val prcFlcn = qtn.optDouble("prcFlcn").takeIf { !it.isNaN() }
            val openPrc = qtn.optDouble("opngPric").takeIf { !it.isNaN() && it > 0.0 }
            val minPrc = qtn.optDouble("minPric").takeIf { !it.isNaN() && it > 0.0 }
            val maxPrc = qtn.optDouble("maxPric").takeIf { !it.isNaN() && it > 0.0 }

            val lstQtn = scty.optJSONArray("lstQtn")
            val intradayCloses = mutableListOf<Double>()
            if (lstQtn != null) {
                for (i in 0 until lstQtn.length()) {
                    val item = lstQtn.optJSONObject(i) ?: continue
                    val p = item.optDouble("closPric").takeIf { !it.isNaN() && it > 0.0 }
                    if (p != null) intradayCloses.add(p)
                }
            }

            val dateStr = tradgFlr.optString("date").takeIf { it.isNotBlank() }
            val msgTime = root.optJSONObject("Msg")?.optString("dtTm")?.takeIf { it.isNotBlank() }
            val desc = scty.optString("desc").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }

            PartialQuoteResult(
                sourceName = MarketQuoteProvider.B3.displayName,
                ticker = ticker,
                companyName = desc,
                price = curPrc,
                changePercent1d = prcFlcn,
                openPrice = openPrc,
                dayLow = minPrc,
                dayHigh = maxPrc,
                closesHistory = intradayCloses,
                marketTimestampLabel = msgTime ?: dateStr
            )
        }.getOrNull()
    }

    fun parseYahooChartJson(ticker: String, jsonText: String): PartialQuoteResult? {
        return runCatching {
            val root = JSONObject(jsonText)
            val chart = root.optJSONObject("chart") ?: return null
            val results = chart.optJSONArray("result") ?: return null
            if (results.length() == 0) return null
            val item = results.optJSONObject(0) ?: return null
            val meta = item.optJSONObject("meta") ?: return null

            val regularPrice = meta.optDouble("regularMarketPrice").takeIf { !it.isNaN() && it > 0.0 }
            val prevClose = meta.optDouble("chartPreviousClose")
                .takeIf { !it.isNaN() && it > 0.0 }
                ?: meta.optDouble("previousClose").takeIf { !it.isNaN() && it > 0.0 }
            val dayHigh = meta.optDouble("regularMarketDayHigh").takeIf { !it.isNaN() && it > 0.0 }
            val dayLow = meta.optDouble("regularMarketDayLow").takeIf { !it.isNaN() && it > 0.0 }
            val high52 = meta.optDouble("fiftyTwoWeekHigh").takeIf { !it.isNaN() && it > 0.0 }
            val low52 = meta.optDouble("fiftyTwoWeekLow").takeIf { !it.isNaN() && it > 0.0 }
            val volumeShares = meta.optDouble("regularMarketVolume").takeIf { !it.isNaN() && it > 0.0 }

            val quoteArr = item.optJSONObject("indicators")?.optJSONArray("quote")
            val quoteObj = quoteArr?.optJSONObject(0)
            val closes = extractDoubleList(quoteObj, "close")
            val lows = extractDoubleList(quoteObj, "low")
            val highs = extractDoubleList(quoteObj, "high")
            val opens = extractDoubleList(quoteObj, "open")

            val effectivePrice = regularPrice ?: closes.lastOrNull() ?: return null
            val effectivePrev = if (closes.size >= 2) {
                closes[closes.lastIndex - 1]
            } else {
                prevClose
            }
            val effectivePrevForDay = meta.optDouble("regularMarketPreviousClose")
                .takeIf { !it.isNaN() && it > 0.0 }
                ?: prevClose
                ?: effectivePrev

            val changePct = if (effectivePrevForDay != null && effectivePrevForDay > 0.0) {
                ((effectivePrice - effectivePrevForDay) / effectivePrevForDay) * 100.0
            } else {
                null
            }

            val volumeMillions = if (volumeShares != null && effectivePrice > 0.0) {
                (volumeShares * effectivePrice) / 1_000_000.0
            } else {
                null
            }

            val companyName = meta.optString("longName").takeIf { it.isNotBlank() }
                ?: meta.optString("shortName").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }

            val regTimeSec = meta.optLong("regularMarketTime", 0L)
            val formattedTime = if (regTimeSec > 0L) {
                SimpleDateFormat("dd/MM HH:mm", brLocale).apply {
                    timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
                }.format(Date(regTimeSec * 1000L))
            } else {
                null
            }

            PartialQuoteResult(
                sourceName = MarketQuoteProvider.YAHOO_B3.displayName,
                ticker = ticker,
                companyName = companyName,
                price = effectivePrice,
                changePercent1d = changePct,
                openPrice = opens.lastOrNull(),
                dayLow = dayLow ?: lows.lastOrNull(),
                dayHigh = dayHigh ?: highs.lastOrNull(),
                previousClose = effectivePrevForDay,
                high52w = high52,
                low52w = low52,
                dailyVolumeBrlMillions = volumeMillions,
                closesHistory = closes,
                lowsHistory = lows,
                highsHistory = highs,
                marketTimestampLabel = formattedTime
            )
        }.getOrNull()
    }

    fun parseBrapiQuoteJson(ticker: String, jsonText: String): PartialQuoteResult? {
        return runCatching {
            val root = JSONObject(jsonText)
            val results = root.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val obj = results.optJSONObject(0) ?: return null

            val price = obj.optDouble("regularMarketPrice").takeIf { !it.isNaN() && it > 0.0 }
            val change = obj.optDouble("regularMarketChangePercent").takeIf { !it.isNaN() }
            val open = obj.optDouble("regularMarketOpen").takeIf { !it.isNaN() && it > 0.0 }
            val low = obj.optDouble("regularMarketDayLow").takeIf { !it.isNaN() && it > 0.0 }
            val high = obj.optDouble("regularMarketDayHigh").takeIf { !it.isNaN() && it > 0.0 }
            val prev = obj.optDouble("regularMarketPreviousClose").takeIf { !it.isNaN() && it > 0.0 }
            val high52 = obj.optDouble("fiftyTwoWeekHigh").takeIf { !it.isNaN() && it > 0.0 }
            val low52 = obj.optDouble("fiftyTwoWeekLow").takeIf { !it.isNaN() && it > 0.0 }
            val vol = obj.optDouble("regularMarketVolume").takeIf { !it.isNaN() && it > 0.0 }
            val pe = obj.optDouble("priceEarnings").takeIf { !it.isNaN() && it > 0.0 }
            val mCap = obj.optDouble("marketCap").takeIf { !it.isNaN() && it > 0.0 }
            val name = obj.optString("longName").takeIf { it.isNotBlank() }
                ?: obj.optString("shortName").takeIf { it.isNotBlank() }

            if (price == null && pe == null && mCap == null) return null

            PartialQuoteResult(
                sourceName = MarketQuoteProvider.BRAPI.displayName,
                ticker = ticker,
                companyName = name,
                price = price,
                changePercent1d = change,
                openPrice = open,
                dayLow = low,
                dayHigh = high,
                previousClose = prev,
                high52w = high52,
                low52w = low52,
                dailyVolumeBrlMillions = if (vol != null && price != null) (vol * price) / 1_000_000.0 else null,
                priceToEarnings = pe,
                marketCapBrl = mCap
            )
        }.getOrNull()
    }

    fun parseAdvfnQuoteHtml(ticker: String, htmlText: String): PartialQuoteResult? {
        return runCatching {
            val pricePatterns = listOf(
                Regex("""id=["']quoteElementPiece1["'][^>]*>\s*([0-9.,]+)\s*<""", RegexOption.IGNORE_CASE),
                Regex(""""last"\s*:\s*"?([0-9.,]+)"?""", RegexOption.IGNORE_CASE),
                Regex("""R\$\s*([0-9]+,[0-9]{2})""", RegexOption.IGNORE_CASE)
            )
            val parsedPrice = pricePatterns.firstNotNullOfOrNull { regex ->
                regex.find(htmlText)?.groupValues?.getOrNull(1)?.let(::parseFlexibleDecimal)
            }?.takeIf { it > 0.0 } ?: return null

            val pctPatterns = listOf(
                Regex("""id=["']quoteElementPiece2["'][^>]*>\s*\(?([+-]?[0-9.,]+)%?\)?\s*<""", RegexOption.IGNORE_CASE),
                Regex(""""change_pct"\s*:\s*"?([+-]?[0-9.,]+)"?""", RegexOption.IGNORE_CASE)
            )
            val parsedPct = pctPatterns.firstNotNullOfOrNull { regex ->
                regex.find(htmlText)?.groupValues?.getOrNull(1)?.let(::parseFlexibleDecimal)
            }

            PartialQuoteResult(
                sourceName = MarketQuoteProvider.ADVFN.displayName,
                ticker = ticker,
                price = parsedPrice,
                changePercent1d = parsedPct
            )
        }.getOrNull()
    }

    fun calculateRsi14(closes: List<Double>): Double? {
        if (closes.size < 15) return null
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..14) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gainSum += diff else lossSum += abs(diff)
        }
        var avgGain = gainSum / 14.0
        var avgLoss = lossSum / 14.0
        for (i in 15 until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) abs(diff) else 0.0
            avgGain = ((avgGain * 13.0) + gain) / 14.0
            avgLoss = ((avgLoss * 13.0) + loss) / 14.0
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun extractDoubleList(obj: JSONObject?, key: String): List<Double> {
        val arr = obj?.optJSONArray(key) ?: return emptyList()
        val list = ArrayList<Double>(arr.length())
        for (i in 0 until arr.length()) {
            if (!arr.isNull(i)) {
                val v = arr.optDouble(i)
                if (!v.isNaN() && v > 0.0) list.add(v)
            }
        }
        return list
    }

    private fun parseFlexibleDecimal(raw: String): Double? {
        val cleaned = raw.trim().replace(" ", "")
        if (cleaned.isEmpty()) return null
        val normalized = when {
            cleaned.contains(",") && cleaned.contains(".") -> cleaned.replace(".", "").replace(",", ".")
            cleaned.contains(",") -> cleaned.replace(",", ".")
            else -> cleaned
        }
        return normalized.toDoubleOrNull()
    }
}

class LiveMarketDataRepository(
    private val client: OkHttpClient = defaultOkHttpClient()
) {
    private val brLocale = Locale("pt", "BR")

    suspend fun fetchLiveQuote(rawTicker: String): Result<LiveMarketQuote> = withContext(Dispatchers.IO) {
        val ticker = LiveQuoteParsers.normalizeTicker(rawTicker)
        if (ticker.length < 4) {
            return@withContext Result.failure(IllegalArgumentException("Informe um código de ativo válido da B3 (ex.: VALE3, ITUB4, HGLG11)."))
        }

        try {
            coroutineScope {
                val b3Deferred = async { fetchFromB3(ticker) }
                val yahooDeferred = async { fetchFromYahoo(ticker) }
                val brapiDeferred = async { fetchFromBrapi(ticker) }

                val b3Res = b3Deferred.await()
                val yahooRes = yahooDeferred.await()
                val brapiRes = brapiDeferred.await()

                val advfnRes = if (b3Res?.price == null && yahooRes?.price == null && brapiRes?.price == null) {
                    fetchFromAdvfn(ticker)
                } else {
                    null
                }

                val partials = listOfNotNull(b3Res, yahooRes, brapiRes, advfnRes)
                val priceSource = listOfNotNull(b3Res, yahooRes, brapiRes, advfnRes)
                    .firstOrNull { (it.price ?: 0.0) > 0.0 }
                    ?: return@coroutineScope Result.failure(
                        IllegalStateException(
                            "Não foi possível obter cotação em tempo real para $ticker na B3, ADVFN ou Yahoo Finance. Verifique sua conexão ou o código do ativo."
                        )
                    )

                val closes = yahooRes?.closesHistory?.takeIf { it.isNotEmpty() }
                    ?: b3Res?.closesHistory?.takeIf { it.isNotEmpty() }
                    ?: emptyList()
                val lows = yahooRes?.lowsHistory?.takeIf { it.isNotEmpty() } ?: closes
                val highs = yahooRes?.highsHistory?.takeIf { it.isNotEmpty() } ?: closes

                val mm9 = closes.takeLast(9).takeIf { it.isNotEmpty() }?.average()
                val mm21 = closes.takeLast(21).takeIf { it.isNotEmpty() }?.average()
                val mm200 = closes.takeLast(200).takeIf { it.size >= 50 }?.average()
                val rsi14 = LiveQuoteParsers.calculateRsi14(closes)
                val support = lows.takeLast(20).minOrNull()
                val resistance = highs.takeLast(20).maxOrNull()

                val nowMillis = System.currentTimeMillis()
                val timeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", brLocale).apply {
                    timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
                }.format(Date(nowMillis))

                val consulted = partials.map { it.sourceName }.distinct()
                val company = partials.firstNotNullOfOrNull { it.companyName } ?: ticker

                Result.success(
                    LiveMarketQuote(
                        ticker = ticker,
                        companyName = company,
                        price = priceSource.price!!,
                        changePercent1d = priceSource.changePercent1d
                            ?: yahooRes?.changePercent1d
                            ?: brapiRes?.changePercent1d
                            ?: advfnRes?.changePercent1d
                            ?: 0.0,
                        openPrice = partials.firstNotNullOfOrNull { it.openPrice },
                        dayLow = partials.firstNotNullOfOrNull { it.dayLow },
                        dayHigh = partials.firstNotNullOfOrNull { it.dayHigh },
                        previousClose = partials.firstNotNullOfOrNull { it.previousClose },
                        high52w = partials.firstNotNullOfOrNull { it.high52w },
                        low52w = partials.firstNotNullOfOrNull { it.low52w },
                        dailyVolumeBrlMillions = partials.firstNotNullOfOrNull { it.dailyVolumeBrlMillions },
                        priceSeries30d = closes.takeLast(30),
                        mm9 = mm9,
                        mm21 = mm21,
                        mm200 = mm200,
                        rsi14 = rsi14,
                        supportPrice = support,
                        resistancePrice = resistance,
                        priceToEarnings = partials.firstNotNullOfOrNull { it.priceToEarnings },
                        priceToBook = partials.firstNotNullOfOrNull { it.priceToBook },
                        dividendYield12m = partials.firstNotNullOfOrNull { it.dividendYield12m },
                        roePercent = partials.firstNotNullOfOrNull { it.roePercent },
                        marketCapBrl = partials.firstNotNullOfOrNull { it.marketCapBrl },
                        primarySourceLabel = consulted.joinToString(" + "),
                        consultedSources = consulted,
                        fetchedAtMillis = nowMillis,
                        marketTimestampLabel = partials.firstNotNullOfOrNull { it.marketTimestampLabel } ?: timeFmt,
                        b3PageUrl = "https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/cotacoes/cotacoes/",
                        advfnPageUrl = "https://br.advfn.com/p.php?pid=qkquote&symbol=BOV%3A$ticker"
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Falha na consulta em tempo real para $ticker: ${e.localizedMessage ?: "erro de rede"}"
                )
            )
        }
    }

    suspend fun fetchBatchQuotes(tickers: List<String>): Map<String, LiveMarketQuote> = withContext(Dispatchers.IO) {
        val normalized = tickers.map(LiveQuoteParsers::normalizeTicker).filter { it.length >= 4 }.distinct()
        if (normalized.isEmpty()) return@withContext emptyMap()
        val semaphore = Semaphore(4)
        coroutineScope {
            normalized.map { ticker ->
                async {
                    semaphore.withPermit {
                        fetchLiveQuote(ticker).getOrNull()?.let { ticker to it }
                    }
                }
            }.mapNotNull { it.await() }.toMap()
        }
    }

    private fun fetchFromB3(ticker: String): PartialQuoteResult? {
        val url = "https://cotacao.b3.com.br/mds/api/v1/DailyFluctuationHistory/$ticker"
        val body = executeGet(url, acceptHeader = "application/json, text/plain, */*") ?: return null
        return LiveQuoteParsers.parseB3FluctuationJson(ticker, body)
    }

    private fun fetchFromYahoo(ticker: String): PartialQuoteResult? {
        val hosts = listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")
        for (host in hosts) {
            val url = "https://$host/v8/finance/chart/$ticker.SA?interval=1d&range=1y"
            val body = executeGet(url, acceptHeader = "application/json") ?: continue
            val parsed = LiveQuoteParsers.parseYahooChartJson(ticker, body)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun fetchFromBrapi(ticker: String): PartialQuoteResult? {
        val url = "https://brapi.dev/api/quote/$ticker?fundamental=true"
        val body = executeGet(url, acceptHeader = "application/json") ?: return null
        return LiveQuoteParsers.parseBrapiQuoteJson(ticker, body)
    }

    private fun fetchFromAdvfn(ticker: String): PartialQuoteResult? {
        val url = "https://br.advfn.com/p.php?pid=qkquote&symbol=BOV%3A$ticker"
        val body = executeGet(url, acceptHeader = "text/html,application/xhtml+xml,*/*") ?: return null
        return LiveQuoteParsers.parseAdvfnQuoteHtml(ticker, body)
    }

    private fun executeGet(url: String, acceptHeader: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", acceptHeader)
                .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    companion object {
        private fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }

        fun mergeLiveQuoteIntoMarketAsset(
            existing: MarketAsset?,
            quote: LiveMarketQuote
        ): MarketAsset {
            val timeLabel = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply {
                timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
            }.format(Date(quote.fetchedAtMillis))

            if (existing != null) {
                val series = quote.priceSeries30d.takeIf { it.size >= 5 } ?: existing.priceSeries30d
                return existing.copy(
                    name = quote.companyName.takeIf { it.isNotBlank() && it != existing.ticker } ?: existing.name,
                    price = quote.price,
                    changePercent1d = quote.changePercent1d,
                    pl = quote.priceToEarnings ?: existing.pl,
                    pvp = quote.priceToBook ?: existing.pvp,
                    dy12m = quote.dividendYield12m ?: existing.dy12m,
                    roe = quote.roePercent ?: existing.roe,
                    liquidezDiariaMilhoes = quote.dailyVolumeBrlMillions ?: existing.liquidezDiariaMilhoes,
                    priceSeries30d = series,
                    mm9 = quote.mm9 ?: existing.mm9,
                    mm21 = quote.mm21 ?: existing.mm21,
                    mm200 = quote.mm200 ?: existing.mm200,
                    rsi14 = quote.rsi14 ?: existing.rsi14,
                    supportPrice = quote.supportPrice ?: existing.supportPrice,
                    resistancePrice = quote.resistancePrice ?: existing.resistancePrice,
                    sourceLabel = "${quote.primarySourceLabel} (Tempo Real)",
                    dataBaseDate = timeLabel,
                    isDemoData = false
                )
            }

            val inferredClass = LiveQuoteParsers.inferAssetClassFromTicker(quote.ticker)
            val fallbackSeries = if (quote.priceSeries30d.size >= 5) {
                quote.priceSeries30d
            } else {
                listOf(quote.previousClose ?: quote.price, quote.price)
            }
            return MarketAsset(
                ticker = quote.ticker,
                name = quote.companyName,
                assetClass = inferredClass,
                sector = "Consulta em Tempo Real",
                price = quote.price,
                changePercent1d = quote.changePercent1d,
                dy12m = quote.dividendYield12m ?: 0.0,
                pvp = quote.priceToBook ?: 0.0,
                pl = quote.priceToEarnings ?: 0.0,
                roe = quote.roePercent ?: 0.0,
                liquidezDiariaMilhoes = quote.dailyVolumeBrlMillions ?: 0.0,
                inadimplenciaPercent = 0.0,
                vacanciaPercent = 0.0,
                dividaLiquidaEbitda = 0.0,
                dataComDay = null,
                paymentDay = null,
                lastDividendBrl = 0.0,
                dividendConsistencyMonths = 0,
                dividendHistory6m = emptyList(),
                priceSeries30d = fallbackSeries,
                rsi14 = quote.rsi14 ?: 50.0,
                mm9 = quote.mm9 ?: quote.price,
                mm21 = quote.mm21 ?: quote.price,
                mm200 = quote.mm200 ?: quote.price,
                supportPrice = quote.supportPrice ?: quote.dayLow ?: (quote.price * 0.95),
                resistancePrice = quote.resistancePrice ?: quote.dayHigh ?: (quote.price * 1.05),
                indexer = "B3 / Mercado",
                maturityDate = null,
                adminFeePercent = 0.0,
                fgcCovered = false,
                sourceLabel = "${quote.primarySourceLabel} (Tempo Real)",
                dataBaseDate = timeLabel,
                riskSummary = "Ativo consultado ao vivo via ${quote.primarySourceLabel}. Múltiplos fundamentalistas adicionais dependem de balanço/RI ou informe da B3.",
                isDemoData = false
            )
        }
    }
}
