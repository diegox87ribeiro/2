package com.example.domain.model

enum class MarketQuoteProvider(val displayName: String, val webHomeUrl: String) {
    B3(
        displayName = "B3 Oficial",
        webHomeUrl = "https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/cotacoes/cotacoes/"
    ),
    ADVFN(
        displayName = "ADVFN Brasil",
        webHomeUrl = "https://br.advfn.com/bolsa-de-valores/bovespa"
    ),
    YAHOO_B3(
        displayName = "Yahoo Finance (B3 .SA)",
        webHomeUrl = "https://finance.yahoo.com"
    ),
    BRAPI(
        displayName = "Brapi / Dados B3",
        webHomeUrl = "https://brapi.dev"
    )
}

data class LiveMarketQuote(
    val ticker: String,
    val companyName: String,
    val price: Double,
    val changePercent1d: Double,
    val openPrice: Double? = null,
    val dayLow: Double? = null,
    val dayHigh: Double? = null,
    val previousClose: Double? = null,
    val high52w: Double? = null,
    val low52w: Double? = null,
    val dailyVolumeBrlMillions: Double? = null,
    val priceSeries30d: List<Double> = emptyList(),
    val mm9: Double? = null,
    val mm21: Double? = null,
    val mm200: Double? = null,
    val rsi14: Double? = null,
    val supportPrice: Double? = null,
    val resistancePrice: Double? = null,
    val priceToEarnings: Double? = null,
    val priceToBook: Double? = null,
    val dividendYield12m: Double? = null,
    val roePercent: Double? = null,
    val marketCapBrl: Double? = null,
    val primarySourceLabel: String,
    val consultedSources: List<String>,
    val fetchedAtMillis: Long,
    val marketTimestampLabel: String,
    val b3PageUrl: String,
    val advfnPageUrl: String
)

sealed interface LiveQuoteLookupState {
    data object Idle : LiveQuoteLookupState
    data class Loading(val ticker: String) : LiveQuoteLookupState
    data class Success(val quote: LiveMarketQuote) : LiveQuoteLookupState
    data class Error(val ticker: String, val message: String) : LiveQuoteLookupState
}
