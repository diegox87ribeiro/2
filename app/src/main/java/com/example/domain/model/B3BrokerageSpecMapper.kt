package com.example.domain.model

import java.text.Normalizer

/**
 * Mapeador determinístico de especificações SINACOR (B3 RV Listado e BM&F Derivativos Futuros)
 * para tickers oficiais negociados na B3.
 *
 * Notas de corretagem brasileiras no padrão SINACOR exibem "Especificação do título"
 * (ex.: "ALLOS ON ED NM", "FII DEVANT CI ER", "ISA ENERGIA PN EJ N1", "WIN Z26") em vez do ticker.
 * Este utilitário normaliza tanto a saída da IA quanto especificações brutas diretamente.
 */
object B3BrokerageSpecMapper {

    private val FUTURES_PREFIXES = setOf(
        "WIN", "WDO", "IND", "DOL", "WSP", "ISP", "BIT", "CCM", "BGI", "ICF", "DI1", "SFI", "DAP"
    )

    private val FUTURES_TICKER_REGEX =
        Regex("^(WIN|WDO|IND|DOL|WSP|ISP|BIT|CCM|BGI|ICF|DI1|SFI|DAP)[FGHJKMNQUVXZ]\\d{2}$")

    private val SPOT_TICKER_REGEX =
        Regex("^[A-Z]{4}(3|4|5|6|11|12|13|14|31|32|33|34|35|39)$")

    private val FUTURES_WITH_SPACE_REGEX =
        Regex("""\b(WIN|WDO|IND|DOL|WSP|ISP|BIT|CCM|BGI|ICF|DI1)\s*[-/]?\s*([FGHJKMNQUVXZ]\d{2})\b""")

    private val FII_DIRECT_CODE_REGEX =
        Regex("""\b(?:FII|FIAGRO|FI-INFRA|FIP)\s+([A-Z]{4})\b""")

    private data class EquityShareClasses(
        val on: String,
        val pn: String = on,
        val pna: String = pn,
        val pnb: String = pn,
        val unt: String = on
    )

    /**
     * Mapeamento direto de fundos (FIIs / FIAGROs / ETFs / FI-Infra) cujo nome SINACOR
     * não traz o código de 4 letras explícito após "FII".
     */
    private val FUND_SPEC_ALIASES: List<Pair<String, String>> = listOf(
        "HECTARE" to "HCTR11",
        "DEVANT" to "DEVA11",
        "BTG AGRO" to "BTAG11",
        "FIAGRO BTAG" to "BTAG11",
        "MAUA RE" to "MCRE11",
        "MAUA RECEB" to "MCCI11",
        "RIZA AKN" to "RZAK11",
        "RIZA AKIN" to "RZAK11",
        "RIZA TERRAX" to "RZTR11",
        "RIZA AGRO" to "RZAG11",
        "CARTESIA" to "CACR11",
        "REC REND" to "RECR11",
        "REC RECEB" to "RECR11",
        "EQI RECE" to "EQIR11",
        "FIAGRO ECO" to "EGAF11",
        "ECOAGRO" to "EGAF11",
        "RB YIELD" to "RBRI11",
        "RB REND" to "RBRR11",
        "RBR REND" to "RBRR11",
        "RBR ALPHA" to "RBRF11",
        "TRX REAL" to "TRXF11",
        "MAXI RENDA" to "MXRF11",
        "CSHG LOG" to "HGLG11",
        "PATRIA LOG" to "HGLG11",
        "CSHG RENDA" to "HGRU11",
        "PATRIA RENDA" to "HGRU11",
        "KINEA REND" to "KNCR11",
        "KINEA IND" to "KNIP11",
        "KINEA RI" to "KNRI11",
        "KINEA AGRO" to "KNCA11",
        "KINEA HIGH" to "KNHY11",
        "KINEA SEC" to "KNSC11",
        "XP MALLS" to "XPML11",
        "XP LOG" to "XPLG11",
        "VINCI SHOP" to "VISC11",
        "VINCI LOG" to "VILG11",
        "BTG LOG" to "BTLG11",
        "BRESCO LOG" to "BRCO11",
        "CAPITANIA" to "CPTS11",
        "IRIDIUM" to "IRDM11",
        "VALORA RE" to "VGIR11",
        "VALORA HEDGE" to "VGHF11",
        "VALORA CRA" to "VGIA11",
        "ALIANZA" to "ALZR11",
        "SUNO AGRO" to "SNAG11",
        "SUNO FOF" to "SNFF11",
        "FG AGRO" to "FGAA11",
        "GUARDIAN LOG" to "GALG11",
        "GUARDIAN REAL" to "GARE11",
        "GGR COVEPI" to "GGRC11",
        "VBI LOG" to "LVBI11",
        "VBI PRIME" to "PVBI11",
        "VBI CRI" to "CVBI11",
        "HABITAT" to "HABT11",
        "TORDE" to "TORD11",
        "VERSALHES" to "VSLH11",
        "URCA PRIME" to "URPR11",
        "JHSF" to "JHSF3",
        "ISHARES BOVA" to "BOVA11",
        "ISHARES IVVB" to "IVVB11",
        "ISHARES SMAL" to "SMAL11",
        "IT NOW DIVO" to "DIVO11",
        "HASHDEX" to "HASH11"
    )

    /**
     * Mapeamento de empresas listadas na B3 por nome SINACOR -> classes ON / PN / PNA / PNB / UNT.
     */
    private val EQUITY_SPEC_ALIASES: List<Pair<String, EquityShareClasses>> = listOf(
        "ALLOS" to EquityShareClasses(on = "ALOS3"),
        "VULCABRAS" to EquityShareClasses(on = "VULC3"),
        "WIZ CO" to EquityShareClasses(on = "WIZC3"),
        "WIZ S" to EquityShareClasses(on = "WIZC3"),
        "MINERVA" to EquityShareClasses(on = "BEEF3"),
        "KLABIN" to EquityShareClasses(on = "KLBN3", pn = "KLBN4", unt = "KLBN11"),
        "DEXXOS" to EquityShareClasses(on = "DEXP3", pn = "DEXP4"),
        "SCHULZ" to EquityShareClasses(on = "SHUL3", pn = "SHUL4"),
        "MITRE REALTY" to EquityShareClasses(on = "MTRE3"),
        "MITRE" to EquityShareClasses(on = "MTRE3"),
        "CURY" to EquityShareClasses(on = "CURY3"),
        "CAIXA SEGUR" to EquityShareClasses(on = "CXSE3"),
        "TENDA" to EquityShareClasses(on = "TEND3"),
        "LAVVI" to EquityShareClasses(on = "LAVV3"),
        "ISA ENERGIA" to EquityShareClasses(on = "ISAE3", pn = "ISAE4"),
        "ISA CTEEP" to EquityShareClasses(on = "ISAE3", pn = "ISAE4"),
        "TRAN PAULIST" to EquityShareClasses(on = "ISAE3", pn = "ISAE4"),
        "CEA MODAS" to EquityShareClasses(on = "CEAB3"),
        "COPEL" to EquityShareClasses(on = "CPLE3", pn = "CPLE6", pna = "CPLE5", pnb = "CPLE6", unt = "CPLE11"),
        "PETROBRAS" to EquityShareClasses(on = "PETR3", pn = "PETR4"),
        "VALE" to EquityShareClasses(on = "VALE3"),
        "ITAUUNIBANCO" to EquityShareClasses(on = "ITUB3", pn = "ITUB4"),
        "ITAU UNIBANCO" to EquityShareClasses(on = "ITUB3", pn = "ITUB4"),
        "ITAUSA" to EquityShareClasses(on = "ITSA3", pn = "ITSA4"),
        "BRADESCO" to EquityShareClasses(on = "BBDC3", pn = "BBDC4"),
        "BRASIL ON" to EquityShareClasses(on = "BBAS3"),
        "BANCO DO BRASIL" to EquityShareClasses(on = "BBAS3"),
        "BBSEGURIDADE" to EquityShareClasses(on = "BBSE3"),
        "BB SEGURIDADE" to EquityShareClasses(on = "BBSE3"),
        "BTGP BANCO" to EquityShareClasses(on = "BPAC3", pn = "BPAC5", unt = "BPAC11"),
        "BTG PACTUAL" to EquityShareClasses(on = "BPAC3", pn = "BPAC5", unt = "BPAC11"),
        "SANTANDER BR" to EquityShareClasses(on = "SANB3", pn = "SANB4", unt = "SANB11"),
        "WEG" to EquityShareClasses(on = "WEGE3"),
        "AMBEV" to EquityShareClasses(on = "ABEV3"),
        "ELETROBRAS" to EquityShareClasses(on = "ELET3", pn = "ELET6", pnb = "ELET6"),
        "TAESA" to EquityShareClasses(on = "TAEE3", pn = "TAEE4", unt = "TAEE11"),
        "CEMIG" to EquityShareClasses(on = "CMIG3", pn = "CMIG4"),
        "SANEPAR" to EquityShareClasses(on = "SAPR3", pn = "SAPR4", unt = "SAPR11"),
        "SABESP" to EquityShareClasses(on = "SBSP3"),
        "GERDAU MET" to EquityShareClasses(on = "GOAU3", pn = "GOAU4"),
        "GERDAU" to EquityShareClasses(on = "GGBR3", pn = "GGBR4"),
        "SID NACIONAL" to EquityShareClasses(on = "CSNA3"),
        "CSN MINERACAO" to EquityShareClasses(on = "CMIN3"),
        "USIMINAS" to EquityShareClasses(on = "USIM3", pn = "USIM5", pna = "USIM5"),
        "SUZANO" to EquityShareClasses(on = "SUZB3"),
        "JBS" to EquityShareClasses(on = "JBSS3"),
        "MARFRIG" to EquityShareClasses(on = "MRFG3"),
        "BRF" to EquityShareClasses(on = "BRFS3"),
        "PETRORIO" to EquityShareClasses(on = "PRIO3"),
        "PRIO" to EquityShareClasses(on = "PRIO3"),
        "BRAVA" to EquityShareClasses(on = "BRAV3"),
        "VIBRA" to EquityShareClasses(on = "VBBR3"),
        "RAIZEN" to EquityShareClasses(on = "RAIZ4", pn = "RAIZ4"),
        "COSAN" to EquityShareClasses(on = "CSAN3"),
        "LOCALIZA" to EquityShareClasses(on = "RENT3"),
        "RUMO" to EquityShareClasses(on = "RAIL3"),
        "EMBRAER" to EquityShareClasses(on = "EMBR3"),
        "TOTVS" to EquityShareClasses(on = "TOTS3"),
        "ENGIE BRASIL" to EquityShareClasses(on = "EGIE3"),
        "CPFL ENERGIA" to EquityShareClasses(on = "CPFE3"),
        "EQUATORIAL" to EquityShareClasses(on = "EQTL3"),
        "ENERGISA" to EquityShareClasses(on = "ENGI3", pn = "ENGI4", unt = "ENGI11"),
        "CYRELA" to EquityShareClasses(on = "CYRE3"),
        "MRV" to EquityShareClasses(on = "MRVE3"),
        "EZTEC" to EquityShareClasses(on = "EZTC3"),
        "DIRECIONAL" to EquityShareClasses(on = "DIRR3"),
        "PLANOEPLANO" to EquityShareClasses(on = "PLPL3"),
        "MULTIPLAN" to EquityShareClasses(on = "MULT3"),
        "IGUATEMI" to EquityShareClasses(on = "IGTI3", unt = "IGTI11"),
        "LOJAS RENNER" to EquityShareClasses(on = "LREN3"),
        "RAIADROGASIL" to EquityShareClasses(on = "RADL3"),
        "RD SAUDE" to EquityShareClasses(on = "RADL3"),
        "FLEURY" to EquityShareClasses(on = "FLRY3"),
        "HAPVIDA" to EquityShareClasses(on = "HAPV3"),
        "HYPERA" to EquityShareClasses(on = "HYPE3"),
        "MAGAZ LUIZA" to EquityShareClasses(on = "MGLU3"),
        "TELEF BRASIL" to EquityShareClasses(on = "VIVT3"),
        "TIM" to EquityShareClasses(on = "TIMS3"),
        "PORTO SEGURO" to EquityShareClasses(on = "PSSA3"),
        "IRBBRASIL" to EquityShareClasses(on = "IRBR3"),
        "ODONTOPREV" to EquityShareClasses(on = "ODPV3"),
        "SLC AGRICOLA" to EquityShareClasses(on = "SLCE3"),
        "BRASILAGRO" to EquityShareClasses(on = "AGRO3"),
        "3TENTOS" to EquityShareClasses(on = "TTEN3"),
        "KEPLER WEBER" to EquityShareClasses(on = "KEPL3"),
        "POMIFRUITS" to EquityShareClasses(on = "FRTA3"),
        "MARCOPOLO" to EquityShareClasses(on = "POMO3", pn = "POMO4"),
        "RANDON" to EquityShareClasses(on = "RAPT3", pn = "RAPT4"),
        "TUPY" to EquityShareClasses(on = "TUPY3"),
        "UNIPAR" to EquityShareClasses(on = "UNIP3", pn = "UNIP6", pna = "UNIP5", pnb = "UNIP6"),
        "BRASKEM" to EquityShareClasses(on = "BRKM3", pn = "BRKM5", pna = "BRKM5"),
        "COPASA" to EquityShareClasses(on = "CSMG3"),
        "ALUPAR" to EquityShareClasses(on = "ALUP3", pn = "ALUP4", unt = "ALUP11"),
        "AUREN" to EquityShareClasses(on = "AURE3"),
        "ENERGIAS BR" to EquityShareClasses(on = "ENBR3"),
        "SANTOS BRP" to EquityShareClasses(on = "STBP3"),
        "CCRO" to EquityShareClasses(on = "CCRO3"),
        "MOTIVA" to EquityShareClasses(on = "MOTV3"),
        "ECO RODOVIAS" to EquityShareClasses(on = "ECOR3"),
        "B3" to EquityShareClasses(on = "B3SA3")
    )

    fun isFuturesTicker(ticker: String): Boolean {
        val clean = normalizeRawToken(ticker)
        if (FUTURES_TICKER_REGEX.matches(clean)) return true
        return FUTURES_PREFIXES.any { prefix ->
            clean.startsWith(prefix) && clean.length in 5..7 && clean.drop(prefix.length).any { it.isDigit() }
        }
    }

    fun isValidB3Ticker(ticker: String): Boolean {
        val clean = normalizeRawToken(ticker)
        return SPOT_TICKER_REGEX.matches(clean) || FUTURES_TICKER_REGEX.matches(clean)
    }

    /**
     * Resolve o ticker oficial da B3 a partir do `rawTicker` retornado pela IA e da
     * `specificationTitle` da nota SINACOR (ex.: "ALLOS ON ED NM", "FII IRIM CI", "WIN Z26").
     */
    fun resolveTicker(rawTicker: String, specificationTitle: String = ""): String {
        val sanitizedRaw = stripFractionalSuffix(normalizeRawToken(rawTicker))
        if (isValidB3Ticker(sanitizedRaw)) {
            return sanitizedRaw
        }

        // Tenta resolver pela especificação da nota (prioritária se rawTicker veio vazio ou como nome)
        resolveFromSpecification(specificationTitle)?.let { return it }
        resolveFromSpecification(rawTicker)?.let { return it }

        // Fallback: retorna token limpo para permitir edição manual no diálogo de revisão
        return sanitizedRaw
    }

    fun resolveFromSpecification(specification: String): String? {
        val normalized = normalizeSpecification(specification)
        if (normalized.isBlank()) return null

        // 1. Contratos futuros BM&F (ex.: "WIN Z26", "WDO F27", "WINZ26")
        FUTURES_WITH_SPACE_REGEX.find(normalized)?.let { match ->
            return "${match.groupValues[1]}${match.groupValues[2]}"
        }

        // 2. Ticker já embutido na especificação (ex.: "TRXF11", "ALOS3F")
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        tokens.firstOrNull { token ->
            val candidate = stripFractionalSuffix(token)
            isValidB3Ticker(candidate)
        }?.let { return stripFractionalSuffix(it) }

        // 3. Aliases explícitos de FIIs / FIAGROs / ETFs
        for ((alias, ticker) in FUND_SPEC_ALIASES) {
            if (normalized.contains(alias)) return ticker
        }

        // 4. Padrão SINACOR direto de FIIs/FIAGROs com sigla de 4 letras (ex.: "FII IRIM CI", "FII SMRE CI ER", "FII BTHF CI", "FII EXES CI")
        val isFundSpec = normalized.contains("FII") || normalized.contains("FIAGRO") || normalized.contains(" CI")
        if (isFundSpec) {
            FII_DIRECT_CODE_REGEX.find(normalized)?.let { match ->
                val code4 = match.groupValues[1]
                if (code4 !in setOf("REAL", "REND", "AGRO", "MAUA", "RIZA", "BTG", "REC", "EQI", "RB")) {
                    return "${code4}11"
                }
            }
        }

        // 5. Empresas listadas (Ações ON / PN / PNA / PNB / UNT)
        val shareClass = detectShareClass(tokens)
        for ((alias, classes) in EQUITY_SPEC_ALIASES) {
            val matched = if (alias.length <= 3) {
                tokens.firstOrNull() == alias
            } else {
                normalized.contains(alias)
            }
            if (matched) {
                return when (shareClass) {
                    "UNT" -> classes.unt
                    "PNA" -> classes.pna
                    "PNB" -> classes.pnb
                    "PN" -> classes.pn
                    else -> classes.on
                }
            }
        }

        return null
    }

    private fun detectShareClass(tokens: List<String>): String {
        return when {
            tokens.any { it == "UNT" || it == "UNIT" || it == "UNITS" } -> "UNT"
            tokens.any { it == "PNA" } -> "PNA"
            tokens.any { it == "PNB" } -> "PNB"
            tokens.any { it == "PN" || it.startsWith("PN") && it.length == 3 } -> "PN"
            else -> "ON"
        }
    }

    private fun normalizeRawToken(raw: String): String {
        return raw.trim()
            .uppercase()
            .replace("$", "S") // Artefato comum de OCR em telas Profit Mobile (ex.: $MRE11 -> SMRE11)
            .replace(Regex("[^A-Z0-9]"), "")
    }

    private fun stripFractionalSuffix(token: String): String {
        return if (token.length > 5 && token.endsWith("F") && token.dropLast(1).lastOrNull()?.isDigit() == true) {
            token.dropLast(1)
        } else {
            token
        }
    }

    private fun normalizeSpecification(raw: String): String {
        val noAccents = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noAccents
            .uppercase()
            .replace(Regex("[^A-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
