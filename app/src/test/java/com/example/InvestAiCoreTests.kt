package com.example

import com.example.data.importexport.SpreadsheetImporter
import com.example.data.local.SavedAiRecommendationEntity
import com.example.data.repository.ChatTurn
import com.example.data.repository.GeminiTaskModel
import com.example.data.repository.MarketCatalogRepository
import com.example.data.repository.PlaceholderAiRepository
import com.example.data.repository.ReportExporter
import com.example.domain.model.AiAgentRole
import com.example.domain.model.AiConclusion
import com.example.domain.model.AssetClass
import com.example.domain.model.ConsolidatedReportAggregator
import com.example.domain.model.DataComGroup
import com.example.domain.model.HhiRiskReport
import com.example.domain.model.OperationType
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InvestAiCoreTests {

    @Test
    fun `dataComGroup maps day ranges G1 to G4 accurately`() {
        assertEquals(DataComGroup.G1, DataComGroup.fromDay(1))
        assertEquals(DataComGroup.G1, DataComGroup.fromDay(8))
        assertEquals(DataComGroup.G2, DataComGroup.fromDay(9))
        assertEquals(DataComGroup.G2, DataComGroup.fromDay(16))
        assertEquals(DataComGroup.G3, DataComGroup.fromDay(17))
        assertEquals(DataComGroup.G3, DataComGroup.fromDay(24))
        assertEquals(DataComGroup.G4, DataComGroup.fromDay(25))
        assertEquals(DataComGroup.G4, DataComGroup.fromDay(31))
        assertEquals(null, DataComGroup.fromDay(null))
    }

    @Test
    fun `hhi calculates exact concentration score and alerts`() {
        val singleAsset = listOf(
            PortfolioItemView(
                id = 1L,
                portfolioType = PortfolioType.REAL,
                ticker = "ITUB4",
                name = "Itau",
                assetClass = AssetClass.ACOES_BR,
                quantity = 100.0,
                averagePrice = 30.0,
                currentPrice = 35.0,
                sector = "Bancos",
                dataComDay = 3,
                dataSourceLabel = "CSV",
                isDemo = false,
                hasMarketPrice = true
            )
        )
        val concentrated = HhiRiskReport.calculate(singleAsset, cashBrl = 0.0)
        assertEquals(10000.0, concentrated.hhiScore, 0.1)
        assertTrue(concentrated.classification.contains("Alta"))
        assertTrue(concentrated.alerts.any { it.contains("2.500") })

        val balanced = listOf(
            singleAsset.first(),
            singleAsset.first().copy(id = 2L, ticker = "HGLG11", assetClass = AssetClass.FII, sector = "Logística", quantity = 20.0, currentPrice = 175.0)
        )
        val moderateOrHigh = HhiRiskReport.calculate(balanced, cashBrl = 1000.0)
        assertEquals(5000.0, moderateOrHigh.hhiScore, 0.1)
    }

    @Test
    fun `csv importer parses brazilian decimals and never invents missing market prices`() {
        val csv = """
            Ticker;Classe;Quantidade;Preço Médio;Preço Atual;Setor;Data-Com
            HGLG11;FII;10;155,50;160,20;Logística;30
            KNCA11;FIAGRO;20;95,00;;Agro;30
            CAIXA;Renda Fixa;1;1200,00;;;
        """.trimIndent()

        val result = SpreadsheetImporter.parseCsv("minha_carteira.csv", csv)
        assertEquals(2, result.positions.size)
        assertEquals(1200.0, result.cashBalanceBrl ?: 0.0, 0.01)

        val hglg = result.positions[0]
        assertEquals("HGLG11", hglg.ticker)
        assertEquals(AssetClass.FII.name, hglg.assetClass)
        assertTrue(hglg.hasMarketPrice)
        assertEquals(160.20, hglg.currentPrice, 0.01)

        val knca = result.positions[1]
        assertEquals("KNCA11", knca.ticker)
        assertFalse(knca.hasMarketPrice)
        assertEquals(0.0, knca.currentPrice, 0.01)
        assertTrue(result.warnings.any { it.contains("KNCA11") })
    }

    @Test
    fun `xlsx importer parses native zip sharedStrings and sheet1 xml`() {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
              <si><t>Ticker</t></si>
              <si><t>Quantidade</t></si>
              <si><t>Preco Medio</t></si>
              <si><t>BBAS3</t></si>
            </sst>
        """.trimIndent()

        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                  <c r="C1" t="s"><v>2</v></c>
                </row>
                <row r="2">
                  <c r="A2" t="s"><v>3</v></c>
                  <c r="B2" t="n"><v>50</v></c>
                  <c r="C2" t="n"><v>26.40</v></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(sharedStringsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val result = SpreadsheetImporter.parseXlsx("carteira.xlsx", ByteArrayInputStream(baos.toByteArray()))
        assertEquals(1, result.positions.size)
        assertEquals("BBAS3", result.positions.first().ticker)
        assertEquals(50.0, result.positions.first().quantity, 0.01)
        assertEquals(26.40, result.positions.first().averagePrice, 0.01)
    }

    @Test
    fun `placeholder ai repository returns all required fields and respects privacy consent`() = runTest {
        val repo = PlaceholderAiRepository()
        val asset = MarketCatalogRepository.catalogAssets.first()
        val hhi = HhiRiskReport.calculate(emptyList(), 0.0)

        val rec = repo.generateRecommendation(
            role = AiAgentRole.AUDITOR,
            asset = asset,
            hhiReport = hhi,
            includePersonalPortfolio = false,
            customPrompt = ""
        )
        assertTrue(rec.isDemoPlaceholder)
        assertFalse(rec.usedPersonalPortfolioData)
        assertTrue(rec.tese.isNotBlank())
        assertTrue(rec.fonteConsultada.isNotBlank())
        assertTrue(rec.indicadoresRelevantes.isNotEmpty())
        assertTrue(rec.riscosIdentificados.isNotEmpty())
        assertTrue(rec.cenarioOtimista.isNotBlank())
        assertTrue(rec.cenarioBase.isNotBlank())
        assertTrue(rec.cenarioPessimista.isNotBlank())
        assertTrue(rec.nivelConfianca in 1..100)
    }

    @Test
    fun `csv importer decodes windows-1252 bytes and consolidates duplicate tickers with weighted average`() {
        val rawCsv = """
            Ticker;Classe;Quantidade;Preço Médio;Preço Atual;Setor;Data-Com
            ITUB4;Ações BR;100;30,00;;Bancos;3
            ITUB4;Ações BR;100;34,00;36,50;Bancos;
        """.trimIndent()
        val win1252Bytes = rawCsv.toByteArray(java.nio.charset.Charset.forName("windows-1252"))

        val result = SpreadsheetImporter.parseCsvBytes("corretora_ansi.csv", win1252Bytes)
        assertEquals(1, result.positions.size)
        val itub = result.positions.first()
        assertEquals("ITUB4", itub.ticker)
        assertEquals(AssetClass.ACOES_BR.name, itub.assetClass)
        assertEquals(200.0, itub.quantity, 0.01)
        assertEquals(32.0, itub.averagePrice, 0.01)
        assertTrue(itub.hasMarketPrice)
        assertEquals(36.50, itub.currentPrice, 0.01)
        assertEquals(3, itub.dataComDay)
        assertTrue(result.warnings.any { it.contains("consolidado") })
    }

    @Test
    fun `standard csv template round-trips cleanly through importer`() {
        val template = SpreadsheetImporter.generateStandardCsvTemplate()
        val parsed = SpreadsheetImporter.parseCsv("invest_ai_modelo_carteira.csv", template)

        assertEquals(5, parsed.positions.size)
        assertEquals(2500.0, parsed.cashBalanceBrl ?: 0.0, 0.01)
        assertTrue(parsed.positions.any { it.ticker == "ITUB4" && it.assetClass == AssetClass.ACOES_BR.name })
        assertTrue(parsed.positions.any { it.ticker == "HGLG11" && it.assetClass == AssetClass.FII.name })
        assertTrue(parsed.positions.any { it.ticker == "KNCA11" && it.assetClass == AssetClass.FIAGRO.name })
        assertTrue(parsed.positions.any { it.ticker == "IVVB11" && it.assetClass == AssetClass.ETF.name })
        assertTrue(parsed.positions.any { it.ticker == "SELIC29" && it.assetClass == AssetClass.RENDA_FIXA.name })

        val asViews = parsed.positions.mapIndexed { idx, e ->
            PortfolioItemView(
                id = idx.toLong() + 1,
                portfolioType = PortfolioType.REAL,
                ticker = e.ticker,
                name = e.name,
                assetClass = AssetClass.valueOf(e.assetClass),
                quantity = e.quantity,
                averagePrice = e.averagePrice,
                currentPrice = e.currentPrice,
                sector = e.sector,
                dataComDay = e.dataComDay,
                dataSourceLabel = e.dataSourceLabel,
                isDemo = false,
                hasMarketPrice = e.hasMarketPrice
            )
        }
        val exported = SpreadsheetImporter.exportPositionsToCsv(asViews, parsed.cashBalanceBrl ?: 0.0)
        val reimported = SpreadsheetImporter.parseCsv("export.csv", exported)
        assertEquals(5, reimported.positions.size)
        assertEquals(2500.0, reimported.cashBalanceBrl ?: 0.0, 0.01)
    }

    @Test
    fun `xlsx importer handles formula strings rich text shared strings and error cells without inventing prices`() {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
              <si><t>Ativo</t></si>
              <si><t>Qtd</t></si>
              <si><t>Custo Médio</t></si>
              <si><t>Cotação</t></si>
            </sst>
        """.trimIndent()

        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                  <c r="C1" t="s"><v>2</v></c>
                  <c r="D1" t="s"><v>3</v></c>
                </row>
                <row r="2">
                  <c r="A2" t="str"><v>XPML11</v></c>
                  <c r="B2" t="n"><v>30</v></c>
                  <c r="C2" t="n"><v>104.50</v></c>
                  <c r="D2" t="e"><v>#N/A</v></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(sharedStringsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val result = SpreadsheetImporter.parseXlsx("carteira_formula.xlsx", ByteArrayInputStream(baos.toByteArray()))
        assertEquals(1, result.positions.size)
        val xpml = result.positions.first()
        assertEquals("XPML11", xpml.ticker)
        assertEquals(AssetClass.FII.name, xpml.assetClass)
        assertFalse(xpml.hasMarketPrice)
        assertEquals(0.0, xpml.currentPrice, 0.01)
        assertTrue(result.warnings.any { it.contains("XPML11") })
    }

    @Test
    fun `consolidated report aggregator synthesizes all three agents consensus and risks`() {
        val insights = listOf(
            SavedAiRecommendationEntity(
                id = 1L,
                agentRole = AiAgentRole.ANALISTA.name,
                ticker = "HGLG11",
                conclusion = AiConclusion.COMPRAR.label,
                confidence = 80,
                thesis = "DY sólido e vacância baixa.",
                usedPersonalData = false,
                sourceLabel = "RI Pátria / B3",
                dataBase = "2026-11-12",
                risksSummary = "Abertura de juros longos",
                createdAt = 300L
            ),
            SavedAiRecommendationEntity(
                id = 2L,
                agentRole = AiAgentRole.GESTOR.name,
                ticker = "HGLG11",
                conclusion = AiConclusion.MANTER.label,
                confidence = 75,
                thesis = "Peso dentro do teto de 15%.",
                usedPersonalData = true,
                sourceLabel = "Motor Local HHI",
                dataBase = "2026-11-12",
                risksSummary = "Concentração em logística",
                createdAt = 200L
            ),
            SavedAiRecommendationEntity(
                id = 3L,
                agentRole = AiAgentRole.AUDITOR.name,
                ticker = "HGLG11",
                conclusion = AiConclusion.MANTER.label,
                confidence = 91,
                thesis = "Múltiplos conferidos contra informe mensal.",
                usedPersonalData = true,
                sourceLabel = "CVM / B3",
                dataBase = "2026-11-12",
                risksSummary = "Validar último informe",
                createdAt = 100L
            )
        )
        val missingPricePos = PortfolioItemView(
            id = 1L,
            portfolioType = PortfolioType.REAL,
            ticker = "KNCA11",
            name = "Kinea Agro",
            assetClass = AssetClass.FIAGRO,
            quantity = 10.0,
            averagePrice = 95.0,
            currentPrice = 0.0,
            sector = "Agro",
            dataComDay = 30,
            dataSourceLabel = "CSV",
            isDemo = false,
            hasMarketPrice = false
        )
        val hhi = HhiRiskReport.calculate(listOf(missingPricePos), cashBrl = 500.0)
        val report = ConsolidatedReportAggregator.aggregate(
            savedInsights = insights,
            realPositions = listOf(missingPricePos),
            realHhi = hhi,
            tickerFilter = "HGLG11"
        )

        assertEquals(3, report.coveredAgentsCount)
        assertEquals(82, report.averageConfidence)
        assertEquals(AiConclusion.MANTER, report.consensusConclusion)
        assertTrue(report.hasConsensusDivergence)
        assertEquals(1, report.missingPriceCount)
        assertTrue(report.combinedRisks.any { it.contains("Abertura de juros longos") })
        assertTrue(report.combinedRisks.any { it.contains("sem cotação") })

        val exportedTxt = ReportExporter.buildExecutiveReportText(
            realPositions = listOf(missingPricePos),
            simPositions = emptyList(),
            realHhi = hhi,
            simHhi = HhiRiskReport.calculate(emptyList(), 25000.0),
            latestImport = null,
            savedInsights = insights
        )
        assertTrue(exportedTxt.contains("SÍNTESE CONSOLIDADA DOS AGENTES IA"))
        assertTrue(exportedTxt.contains("Agente Analista [HGLG11]"))
        assertTrue(exportedTxt.contains("Agente Gestor [HGLG11]"))
        assertTrue(exportedTxt.contains("Agente Auditor [HGLG11]"))
    }

    @Test
    fun `multi-turn chat repository streams reply with role, model tier, and conversation history`() = runBlocking {
        val repo = PlaceholderAiRepository()
        val asset = MarketCatalogRepository.catalogAssets.first()
        val hhi = HhiRiskReport.calculate(emptyList(), cashBrl = 1000.0)
        val history = listOf(
            ChatTurn(senderRole = "USER", text = "Qual é o foco do Analista?"),
            ChatTurn(senderRole = "MODEL", text = "Fundamentos, múltiplos e técnica.")
        )

        val reply = repo.streamChatReply(
            role = AiAgentRole.GESTOR,
            modelOption = GeminiTaskModel.FAST,
            history = history,
            userMessage = "Como equilibrar G1–G4?",
            focusedAsset = asset,
            hhiReport = hhi,
            includePersonalPortfolio = false
        ).first()

        assertTrue(reply.contains("Gestor"))
        assertTrue(reply.contains(GeminiTaskModel.FAST.label))
        assertTrue(reply.contains(asset.ticker))
        assertEquals("gemini-3.8-flash", GeminiTaskModel.GENERAL.modelId)
        assertEquals("gemini-3.5-flash-lite", GeminiTaskModel.FAST.modelId)
        assertEquals("gemini-4-argon", GeminiTaskModel.DEEP_REASONING.modelId)
    }

    @Test
    fun `parseBrlDecimal parses Brazilian and US formatted numbers accurately`() {
        assertEquals(66.82, com.example.ui.screens.parseBrlDecimal("66,82")!!, 0.001)
        assertEquals(1234.56, com.example.ui.screens.parseBrlDecimal("R$ 1.234,56")!!, 0.001)
        assertEquals(150.25, com.example.ui.screens.parseBrlDecimal("150.25")!!, 0.001)
        assertEquals(null, com.example.ui.screens.parseBrlDecimal("   "))
    }

    @Test
    fun `dividend monitor computes monthly annual received and projected per asset`() {
        val hglg = PortfolioItemView(
            id = 1L,
            portfolioType = PortfolioType.REAL,
            ticker = "HGLG11",
            name = "Pátria Logística",
            assetClass = AssetClass.FII,
            quantity = 100.0,
            averagePrice = 150.0,
            currentPrice = 156.40,
            sector = "Logística",
            dataComDay = 30,
            dataSourceLabel = "XLSX",
            isDemo = false,
            hasMarketPrice = true
        )
        val itub = PortfolioItemView(
            id = 2L,
            portfolioType = PortfolioType.REAL,
            ticker = "ITUB4",
            name = "Itaú PN",
            assetClass = AssetClass.ACOES_BR,
            quantity = 200.0,
            averagePrice = 30.0,
            currentPrice = 35.80,
            sector = "Bancos",
            dataComDay = 3,
            dataSourceLabel = "XLSX",
            isDemo = false,
            hasMarketPrice = true
        )
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 19, 10, 0, 0)
        }
        val todayStr = "19/11/2026"
        val prevMonthStr = "15/10/2026"
        val ops = listOf(
            com.example.data.local.PortfolioOperationEntity(
                id = 1L,
                portfolioType = PortfolioType.REAL.name,
                ticker = "HGLG11",
                operationType = OperationType.PROVENTO.name,
                quantity = 1.0,
                unitPrice = 110.0,
                totalValue = 110.0,
                dateIso = todayStr,
                notes = "Rendimento nov",
                createdAt = cal.timeInMillis
            ),
            com.example.data.local.PortfolioOperationEntity(
                id = 2L,
                portfolioType = PortfolioType.REAL.name,
                ticker = "ITUB4",
                operationType = OperationType.PROVENTO.name,
                quantity = 1.0,
                unitPrice = 85.0,
                totalValue = 85.0,
                dateIso = prevMonthStr,
                notes = "JCP out",
                createdAt = cal.timeInMillis - 30L * 24 * 60 * 60 * 1000
            )
        )

        val summary = com.example.domain.model.DividendMonitorCalculator.compute(
            realPositions = listOf(hglg, itub),
            marketAssets = MarketCatalogRepository.catalogAssets,
            operations = ops,
            referenceDateMillis = cal.timeInMillis
        )

        assertEquals(110.0, summary.receivedMonthlyBrl, 0.01)
        assertEquals(195.0, summary.receivedAnnualBrl, 0.01)
        assertTrue(summary.projectedMonthlyBrl > 150.0)
        assertTrue(summary.projectedAnnualBrl > summary.projectedMonthlyBrl * 11.9)
        assertEquals(2, summary.perAssetRows.size)
        val hglgRow = summary.perAssetRows.first { it.ticker == "HGLG11" }
        assertEquals(110.0, hglgRow.projectedMonthBrl, 0.01)
        assertEquals(1320.0, hglgRow.projectedAnnualBrl, 0.01)
    }

    @Test
    fun `comparison series builder normalizes portfolio assets and benchmarks to base 100`() {
        val pos = PortfolioItemView(
            id = 1L,
            portfolioType = PortfolioType.REAL,
            ticker = "WEGE3",
            name = "WEG ON",
            assetClass = AssetClass.ACOES_BR,
            quantity = 10.0,
            averagePrice = 45.0,
            currentPrice = 52.90,
            sector = "Bens Industriais",
            dataComDay = 22,
            dataSourceLabel = "XLSX",
            isDemo = false,
            hasMarketPrice = true
        )
        val candidates = com.example.domain.model.ComparisonSeriesBuilder.buildCandidates(
            benchmarks = MarketCatalogRepository.benchmarks12m,
            realPositions = listOf(pos),
            marketAssets = MarketCatalogRepository.catalogAssets
        )
        val wege = candidates.first { it.shortLabel == "WEGE3" }
        assertEquals(12, wege.series.normalizedPoints12m.size)
        assertEquals(100.0, wege.series.normalizedPoints12m.first(), 0.001)
        assertEquals("Carteira Real", wege.groupLabel)
    }

    @Test
    fun `csv importer parses dividends in same asset table and standalone dividend history rows`() {
        val csv = """
            Ticker;Nome;Classe;Quantidade;Preço Médio;Preço Atual;Setor;Data-Com;Dividendo Valor;Data Provento;Tipo Provento
            HGLG11;Pátria Logística;FII;20;155,00;156,40;Logística;30;22,00;14/11/2026;Rendimento
            HGLG11;;;;;;;;22,00;14/10/2026;Rendimento
            ITUB4;Itaú Unibanco;Ações BR;100;31,50;35,80;Bancos;3;28,40;01/11/2026;JCP
            CAIXA;Reserva;Renda Fixa;1;1000,00;;;;;;
        """.trimIndent()

        val result = SpreadsheetImporter.parseCsv("carteira_e_proventos.csv", csv)
        assertEquals(2, result.positions.size)
        assertEquals(1000.0, result.cashBalanceBrl ?: 0.0, 0.01)
        assertEquals(3, result.dividends.size)
        assertTrue(result.dividends.all { it.operationType == OperationType.PROVENTO.name })
        assertEquals(22.0, result.dividends.first { it.ticker == "HGLG11" && it.dateIso == "14/11/2026" }.totalValue, 0.01)
        assertEquals(22.0, result.dividends.first { it.ticker == "HGLG11" && it.dateIso == "14/10/2026" }.totalValue, 0.01)
        assertEquals(28.40, result.dividends.first { it.ticker == "ITUB4" }.totalValue, 0.01)

        val templateParsed = SpreadsheetImporter.parseCsv("template.csv", SpreadsheetImporter.generateStandardCsvTemplate())
        assertEquals(5, templateParsed.positions.size)
        assertEquals(3, templateParsed.dividends.size)
    }

    @Test
    fun `valuation calculator computes Bazin ceiling Graham fair price and 5 pillar scores`() {
        val itub = MarketCatalogRepository.catalogAssets.first { it.ticker == "ITUB4" }
        val valResult = com.example.domain.model.ValuationAndComparisonCalculator.computeValuation(itub, targetBazinDyPercent = 6.0)

        val expectedBazin = (itub.price * (itub.dy12m / 100.0)) / 0.06
        assertEquals(expectedBazin, valResult.bazinCeilingPriceBrl ?: 0.0, 0.05)
        assertTrue((valResult.bazinMarginPercent ?: 0.0) > 0.0)
        assertTrue((valResult.grahamFairPriceBrl ?: 0.0) > itub.price)
        assertEquals(5, valResult.pillarScores.asList().size)
        assertTrue(valResult.overallScore in 1.0..100.0)
    }

    @Test
    fun `rebalance advisor allocates new contribution toward underweight classes and lowers HHI`() {
        val heavyStock = PortfolioItemView(
            id = 1L,
            portfolioType = PortfolioType.REAL,
            ticker = "ITUB4",
            name = "Itaú PN",
            assetClass = AssetClass.ACOES_BR,
            quantity = 200.0,
            averagePrice = 30.0,
            currentPrice = 35.0,
            sector = "Bancos",
            dataComDay = 3,
            dataSourceLabel = "CSV",
            isDemo = false,
            hasMarketPrice = true
        )
        val plan = com.example.domain.model.RebalanceAdvisorCalculator.calculatePlan(
            realPositions = listOf(heavyStock),
            marketAssets = MarketCatalogRepository.catalogAssets,
            rawTargets = mapOf(
                AssetClass.ACOES_BR to 40.0,
                AssetClass.FII to 40.0,
                AssetClass.RENDA_FIXA to 20.0
            ),
            contributionBrl = 3000.0,
            respectHhiCap = true
        )
        assertEquals(10000.0, plan.hhiBefore, 0.5)
        assertTrue(plan.hhiAfter < plan.hhiBefore)
        assertTrue(plan.suggestions.any { it.assetClass == AssetClass.FII })
        assertEquals(3000.0, plan.suggestions.sumOf { it.suggestedAmountBrl }, 5.0)
    }

    @Test
    fun `tax report calculator enforces 20k stock exemption, 20pct FII tax, carry-forward losses, and DARF`() {
        val ops = listOf(
            // Compra ITUB4 1000x a 30 = 30.000
            com.example.data.local.PortfolioOperationEntity(
                id = 1L, portfolioType = PortfolioType.REAL.name, ticker = "ITUB4",
                operationType = OperationType.COMPRA.name, quantity = 1000.0, unitPrice = 30.0,
                totalValue = 30000.0, dateIso = "02/10/2026", notes = "Compra teste", createdAt = 100L
            ),
            // Venda ITUB4 500x a 36 = 18.000 (ISENTO <= 20k, lucro 3.000)
            com.example.data.local.PortfolioOperationEntity(
                id = 2L, portfolioType = PortfolioType.REAL.name, ticker = "ITUB4",
                operationType = OperationType.VENDA.name, quantity = 500.0, unitPrice = 36.0,
                totalValue = 18000.0, dateIso = "10/10/2026", notes = "Venda teste", createdAt = 200L
            ),
            // Compra HGLG11 100x a 150 = 15.000
            com.example.data.local.PortfolioOperationEntity(
                id = 3L, portfolioType = PortfolioType.REAL.name, ticker = "HGLG11",
                operationType = OperationType.COMPRA.name, quantity = 100.0, unitPrice = 150.0,
                totalValue = 15000.0, dateIso = "02/10/2026", notes = "Compra FII teste", createdAt = 300L
            ),
            // Venda HGLG11 50x a 160 = 8.000 (SEM ISENCAO em FII! Lucro 500 -> 20% = R$ 100)
            com.example.data.local.PortfolioOperationEntity(
                id = 4L, portfolioType = PortfolioType.REAL.name, ticker = "HGLG11",
                operationType = OperationType.VENDA.name, quantity = 50.0, unitPrice = 160.0,
                totalValue = 8000.0, dateIso = "15/10/2026", notes = "Venda FII teste", createdAt = 400L
            )
        )

        val reports = com.example.domain.model.TaxReportCalculator.computeMonthlyReports(
            operations = ops,
            realPositions = emptyList(),
            marketAssets = MarketCatalogRepository.catalogAssets,
            initialCarryLossCommonBrl = 500.0,
            initialCarryLossFiiBrl = 100.0
        )

        assertEquals(1, reports.size)
        val oct = reports.first()
        assertTrue(oct.isStockExempt20k)
        assertEquals(0.0, oct.darfCommonBrl, 0.01)
        // Lucro isento em acoes NAO consome prejuizo acumulado comum!
        assertEquals(500.0, oct.endingCarryLossCommonBrl, 0.01)
        // FII: 500 lucro - 100 prejuizo FII = 400 base * 20% = R$ 80,00 DARF
        assertEquals(400.0, oct.taxableFiiBaseBrl, 0.01)
        assertEquals(80.0, oct.darfFiiBrl, 0.01)
        assertEquals(80.0, oct.totalDarfBrl, 0.01)
        assertFalse(oct.isBelowDarfMinimum10Brl)
    }

    @Test
    fun `fundamental ranking filter hides non-matching assets and reveals them when criteria relax`() {
        val defaultCriteria = com.example.domain.model.FundamentalFilterCriteria(
            minDyPercent = 6.0,
            maxPvp = 1.25,
            minDailyLiquidityMillions = 1.5,
            maxInadimplenciaPercent = 3.0,
            minRoePercent = 12.0
        )

        val defaultRanked = com.example.domain.model.ValuationAndComparisonCalculator.filterFundamentalRanking(
            assets = MarketCatalogRepository.catalogAssets,
            criteria = defaultCriteria
        )
        val defaultTickers = defaultRanked.map { it.asset.ticker }.toSet()

        assertTrue("KNCR11" in defaultTickers)
        assertTrue("HGLG11" in defaultTickers)
        assertFalse("WEGE3" in defaultTickers) // DY 1.6% < 6%, P/VP 10.9 > 1.25
        assertFalse("ITUB4" in defaultTickers) // P/VP 1.85 > 1.25
        assertFalse("BBAS3" in defaultTickers) // Inadimplência 3.2% > 3.0%
        assertFalse("SELIC29" in defaultTickers) // Renda Fixa excluída

        val relaxedCriteria = defaultCriteria.copy(
            maxPvp = 2.0,
            maxInadimplenciaPercent = 3.5
        )
        val relaxedTickers = com.example.domain.model.ValuationAndComparisonCalculator.filterFundamentalRanking(
            assets = MarketCatalogRepository.catalogAssets,
            criteria = relaxedCriteria
        ).map { it.asset.ticker }.toSet()

        assertTrue("ITUB4" in relaxedTickers)
        assertTrue("BBAS3" in relaxedTickers)
        assertFalse("WEGE3" in relaxedTickers)
    }

    @Test
    fun `price target evaluator triggers AT_OR_BELOW and AT_OR_ABOVE accurately and ignores disabled or already triggered`() {
        val buyDip = com.example.data.local.PriceTargetAlertEntity(
            id = 1L,
            ticker = "VALE3",
            targetPriceBrl = 60.0,
            condition = com.example.domain.model.PriceAlertCondition.AT_OR_BELOW.code,
            isEnabled = true,
            hasTriggered = false
        )
        val takeProfit = com.example.data.local.PriceTargetAlertEntity(
            id = 2L,
            ticker = "PETR4",
            targetPriceBrl = 40.0,
            condition = com.example.domain.model.PriceAlertCondition.AT_OR_ABOVE.code,
            isEnabled = true,
            hasTriggered = false
        )
        val alreadyTriggered = buyDip.copy(id = 3L, hasTriggered = true)
        val paused = takeProfit.copy(id = 4L, isEnabled = false)

        val triggered = com.example.domain.model.PriceTargetEvaluator.evaluateAlerts(
            alerts = listOf(buyDip, takeProfit, alreadyTriggered, paused),
            quotesByTicker = mapOf(
                "VALE3" to (59.80 to "B3 Oficial"),
                "PETR4" to (40.10 to "Yahoo B3")
            )
        )

        assertEquals(2, triggered.size)
        assertEquals(listOf(1L, 2L), triggered.map { it.alert.id })
        assertFalse(com.example.domain.model.PriceTargetEvaluator.isTargetReached(buyDip, 61.50))
        assertFalse(com.example.domain.model.PriceTargetEvaluator.isTargetReached(takeProfit, 39.20))
        assertEquals(
            com.example.domain.model.PriceAlertCondition.AT_OR_BELOW,
            com.example.domain.model.PriceTargetEvaluator.suggestCondition(58.0, 64.0)
        )
        assertEquals(
            com.example.domain.model.PriceAlertCondition.AT_OR_ABOVE,
            com.example.domain.model.PriceTargetEvaluator.suggestCondition(70.0, 64.0)
        )
    }

    @Test
    fun `dividend monitor computes per-asset monthly received and projected Jan to Dec series accurately`() {
        val refCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 18, 12, 0, 0)
        }
        val hglg = PortfolioItemView(
            id = 10L,
            portfolioType = PortfolioType.REAL,
            ticker = "HGLG11",
            name = "Pátria Logística",
            assetClass = AssetClass.FII,
            quantity = 100.0,
            averagePrice = 155.0,
            currentPrice = 156.40,
            sector = "Logística",
            dataComDay = 30,
            dataSourceLabel = "XLSX",
            isDemo = false,
            hasMarketPrice = true
        )
        val smreCustom = PortfolioItemView(
            id = 11L,
            portfolioType = PortfolioType.REAL,
            ticker = "SMRE11",
            name = "Smart Real Estate FII",
            assetClass = AssetClass.FII,
            quantity = 50.0,
            averagePrice = 55.17,
            currentPrice = 56.30,
            sector = "FIIs",
            dataComDay = 30,
            dataSourceLabel = "XLSX",
            isDemo = false,
            hasMarketPrice = true
        )
        val ops = listOf(
            com.example.data.local.PortfolioOperationEntity(
                id = 101L,
                portfolioType = PortfolioType.REAL.name,
                ticker = "HGLG11",
                operationType = OperationType.PROVENTO.name,
                quantity = 100.0,
                unitPrice = 1.10,
                totalValue = 110.0,
                dateIso = "14/10/2026",
                notes = "Rendimento Out",
                createdAt = refCal.timeInMillis - 30L * 24 * 60 * 60 * 1000
            ),
            com.example.data.local.PortfolioOperationEntity(
                id = 102L,
                portfolioType = PortfolioType.REAL.name,
                ticker = "HGLG11",
                operationType = OperationType.PROVENTO.name,
                quantity = 100.0,
                unitPrice = 1.15,
                totalValue = 115.0,
                dateIso = "14/11/2026",
                notes = "Rendimento Nov",
                createdAt = refCal.timeInMillis
            ),
            com.example.data.local.PortfolioOperationEntity(
                id = 103L,
                portfolioType = PortfolioType.REAL.name,
                ticker = "SMRE11",
                operationType = OperationType.PROVENTO.name,
                quantity = 50.0,
                unitPrice = 0.60,
                totalValue = 30.0,
                dateIso = "16/10/2026",
                notes = "Rendimento SMRE11",
                createdAt = refCal.timeInMillis - 28L * 24 * 60 * 60 * 1000
            )
        )

        val summary = com.example.domain.model.DividendMonitorCalculator.compute(
            realPositions = listOf(hglg, smreCustom),
            marketAssets = MarketCatalogRepository.catalogAssets,
            operations = ops,
            referenceDateMillis = refCal.timeInMillis,
            selectedYear = 2026
        )

        assertEquals(2026, summary.selectedYear)
        assertTrue(summary.availableYears.containsAll(listOf(2025, 2026, 2027)))
        assertEquals(255.0, summary.receivedYearTotalBrl, 0.01)

        val hglgRow = summary.perAssetRows.first { it.ticker == "HGLG11" }
        assertEquals(12, hglgRow.monthlyPoints.size)
        assertEquals(110.0, hglgRow.receivedForMonth(java.util.Calendar.OCTOBER), 0.01)
        assertEquals(115.0, hglgRow.receivedForMonth(java.util.Calendar.NOVEMBER), 0.01)
        assertEquals(110.0, hglgRow.projectedForMonth(java.util.Calendar.NOVEMBER), 0.01)
        assertEquals(225.0, hglgRow.receivedYearBrl, 0.01)
        assertEquals(1, hglgRow.operationsForMonth(java.util.Calendar.NOVEMBER).size)

        val smreRow = summary.perAssetRows.first { it.ticker == "SMRE11" }
        assertEquals(30.0, smreRow.receivedForMonth(java.util.Calendar.OCTOBER), 0.01)
        assertEquals(30.0, smreRow.projectedForMonth(java.util.Calendar.NOVEMBER), 0.01)
        assertEquals(360.0, smreRow.projectedAnnualBrl, 0.01)

        for (m in 0..11) {
            val sumRec = summary.perAssetRows.sumOf { it.receivedForMonth(m) }
            val sumProj = summary.perAssetRows.sumOf { it.projectedForMonth(m) }
            assertEquals(sumRec, summary.monthlyJanToDec[m].receivedBrl, 0.01)
            assertEquals(sumProj, summary.monthlyJanToDec[m].projectedBrl, 0.01)
        }
    }

    @Test
    fun `dividend date alert evaluator detects upcoming Data-Com and Data-Ex respecting weekends and explicit Data-Ex`() {
        // 28/11/2026 (Saturday) -> Data-Com day 30/11/2026 (Monday, 2 days away) -> derived Data-Ex 01/12/2026 (Tuesday, 3 days away)
        val refCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 28, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val hglg = PortfolioItemView(
            id = 20L,
            portfolioType = PortfolioType.REAL,
            ticker = "HGLG11",
            name = "Pátria Logística",
            assetClass = AssetClass.FII,
            quantity = 20.0,
            averagePrice = 155.0,
            currentPrice = 156.40,
            sector = "Logística",
            dataComDay = 30,
            dataSourceLabel = "XLSX",
            isDemo = false,
            hasMarketPrice = true
        )
        val smre = PortfolioItemView(
            id = 21L,
            portfolioType = PortfolioType.REAL,
            ticker = "SMRE11",
            name = "Smart Real Estate",
            assetClass = AssetClass.FII,
            quantity = 10.0,
            averagePrice = 55.0,
            currentPrice = 56.30,
            sector = "FIIs",
            dataComDay = 30,
            dataSourceLabel = "XLSX",
            isDemo = false,
            hasMarketPrice = true
        )
        val smreQuote = com.example.domain.model.LiveMarketQuote(
            ticker = "SMRE11",
            companyName = "Smart Real Estate",
            price = 56.30,
            changePercent1d = 0.0,
            dataComDay = 30,
            dataExDay = 3,
            lastDividendBrl = 0.60,
            primarySourceLabel = "Investidor10 + Yahoo",
            consultedSources = listOf("Investidor10", "Yahoo Finance"),
            marketTimestampLabel = "28/11/2026 10:00",
            b3PageUrl = "",
            advfnPageUrl = "",
            fetchedAtMillis = refCal.timeInMillis
        )

        val upcomingWithin3Days = com.example.domain.model.DividendDateAlertEvaluator.findUpcomingAlerts(
            realPositions = listOf(hglg, smre),
            marketAssets = MarketCatalogRepository.catalogAssets,
            liveQuotesByTicker = mapOf("SMRE11" to smreQuote),
            leadDays = 3,
            notifyDataCom = true,
            notifyDataEx = true,
            referenceDateMillis = refCal.timeInMillis
        )

        val hglgCom = upcomingWithin3Days.firstOrNull {
            it.ticker == "HGLG11" && it.eventType == com.example.domain.model.DividendDateEventType.DATA_COM
        }
        val hglgEx = upcomingWithin3Days.firstOrNull {
            it.ticker == "HGLG11" && it.eventType == com.example.domain.model.DividendDateEventType.DATA_EX
        }
        val smreCom = upcomingWithin3Days.firstOrNull {
            it.ticker == "SMRE11" && it.eventType == com.example.domain.model.DividendDateEventType.DATA_COM
        }
        val smreExIn3d = upcomingWithin3Days.firstOrNull {
            it.ticker == "SMRE11" && it.eventType == com.example.domain.model.DividendDateEventType.DATA_EX
        }

        org.junit.Assert.assertNotNull(hglgCom)
        assertEquals("30/11/2026", hglgCom!!.eventDateFormatted)
        assertEquals(2, hglgCom.daysUntil)

        org.junit.Assert.assertNotNull(hglgEx)
        assertEquals("01/12/2026", hglgEx!!.eventDateFormatted)
        assertEquals(3, hglgEx.daysUntil)

        org.junit.Assert.assertNotNull(smreCom)
        assertEquals(2, smreCom!!.daysUntil)
        // SMRE11 explicit Data-Ex is 03/12/2026 (5 days away) -> excluded from 3-day window, included in 5-day window
        assertEquals(null, smreExIn3d)

        val upcomingWithin5Days = com.example.domain.model.DividendDateAlertEvaluator.findUpcomingAlerts(
            realPositions = listOf(smre),
            marketAssets = MarketCatalogRepository.catalogAssets,
            liveQuotesByTicker = mapOf("SMRE11" to smreQuote),
            leadDays = 5,
            notifyDataCom = false,
            notifyDataEx = true,
            referenceDateMillis = refCal.timeInMillis
        )
        assertEquals(1, upcomingWithin5Days.size)
        assertEquals("03/12/2026", upcomingWithin5Days.first().eventDateFormatted)
        assertEquals(5, upcomingWithin5Days.first().daysUntil)
        assertEquals(6.0, upcomingWithin5Days.first().projectedDividendBrl, 0.01)
    }

    @Test
    fun `b3 calendar adjusts weekend Data-Com to previous business day, skips Finados holiday for Data-Ex, and parses full CSV dates`() {
        // 30/05/2026 é sábado -> Data-Com B3 deve recuar para sexta-feira 29/05/2026
        val refMay2026 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.MAY, 25, 9, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val mayCom = com.example.domain.model.DividendDateAlertEvaluator.nextMonthlyOccurrence(refMay2026, 30)
        assertEquals(29, mayCom.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(java.util.Calendar.MAY, mayCom.get(java.util.Calendar.MONTH))

        // 30/10/2026 (sexta) -> 31/10 (sáb), 01/11 (dom), 02/11 (Finados) -> Data-Ex 03/11/2026 (terça)
        val refOct2026 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 29, 9, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val novEx = com.example.domain.model.DividendDateAlertEvaluator.nextDataExFromDataCom(refOct2026, 30)
        assertEquals(3, novEx.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(java.util.Calendar.NOVEMBER, novEx.get(java.util.Calendar.MONTH))

        // Planilha com datas completas na coluna Data-Com
        assertEquals(30, SpreadsheetImporter.parseDataComDay("30/10/2026"))
        assertEquals(30, SpreadsheetImporter.parseDataComDay("2026-10-30"))
        assertEquals(18, SpreadsheetImporter.parseDataComDay("Dia 18"))
        assertEquals(null, SpreadsheetImporter.parseDataComDay(""))

        // Catálogo corrigido (sem datas fictícias para ações trimestrais e ITUB4/RURA11 no último dia útil)
        val byTicker = MarketCatalogRepository.catalogAssets.associateBy { it.ticker }
        assertEquals(30, byTicker["ITUB4"]?.dataComDay)
        assertEquals(30, byTicker["RURA11"]?.dataComDay)
        assertEquals(18, byTicker["XPML11"]?.dataComDay)
        assertEquals(12, byTicker["VGIA11"]?.dataComDay)
        assertEquals(null, byTicker["BBAS3"]?.dataComDay)
        assertEquals(null, byTicker["TAEE11"]?.dataComDay)
        assertEquals(null, byTicker["WEGE3"]?.dataComDay)
        assertEquals(null, byTicker["EGIE3"]?.dataComDay)
    }

    @Test
    fun `quick update csv template and minimal Ativo Preco Data Dividendos table parse cleanly`() {
        val template = SpreadsheetImporter.generateQuickUpdateCsvTemplate()
        val templateParsed = SpreadsheetImporter.parseCsv("invest_ai_atualizacao.csv", template)

        assertTrue(templateParsed.positions.isEmpty())
        assertEquals(5, templateParsed.quickUpdates.size)
        assertEquals(4, templateParsed.dividends.size)
        val itub = templateParsed.quickUpdates.first { it.ticker == "ITUB4" }
        assertEquals(36.40, itub.price ?: 0.0, 0.001)
        assertEquals(30, itub.dataComDay)
        assertEquals(28.40, itub.dividendAmount ?: 0.0, 0.001)

        // Tabela mínima exatamente com as 4 colunas solicitadas: Ativo;Preço;Data;Dividendos
        val minimalCsv = """
            Ativo;Preço;Data;Dividendos
            ITUB4;37,15;23/11/2026;32,50
            HGLG11;158,90;23/11/2026;
            MXRF11;;14/11/2026;19,20
        """.trimIndent()
        val parsed = SpreadsheetImporter.parseCsv("atualizacao_mensal.csv", minimalCsv)
        assertTrue(parsed.positions.isEmpty())
        assertEquals(3, parsed.quickUpdates.size)
        assertEquals(2, parsed.dividends.size)

        val rowItub = parsed.quickUpdates.first { it.ticker == "ITUB4" }
        assertEquals(37.15, rowItub.price ?: 0.0, 0.001)
        assertEquals("23/11/2026", rowItub.referenceDate)
        assertEquals(32.50, rowItub.dividendAmount ?: 0.0, 0.001)

        val rowHglg = parsed.quickUpdates.first { it.ticker == "HGLG11" }
        assertEquals(158.90, rowHglg.price ?: 0.0, 0.001)
        assertEquals("23/11/2026", rowHglg.referenceDate)
        assertEquals(null, rowHglg.dividendAmount)

        val rowMxrf = parsed.quickUpdates.first { it.ticker == "MXRF11" }
        assertEquals(null, rowMxrf.price)
        assertEquals("14/11/2026", rowMxrf.referenceDate)
        assertEquals(19.20, rowMxrf.dividendAmount ?: 0.0, 0.001)
        assertEquals("14/11/2026", parsed.dividends.first { it.ticker == "MXRF11" }.dateIso)
    }

    @Test
    fun `profitability evolution calculator rebases windows, adds dividends to total return, and computes alpha vs CDI`() {
        val empty = com.example.domain.model.PortfolioProfitabilityCalculator.compute(
            realPositions = emptyList(),
            marketAssets = emptyList(),
            operations = emptyList(),
            benchmarks = emptyList()
        )
        assertTrue(empty.points.isEmpty())

        val refCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 23, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val positions = listOf(
            PortfolioItemView(
                id = 1L,
                portfolioType = PortfolioType.REAL,
                ticker = "ITUB4",
                name = "Itaú Unibanco PN",
                assetClass = AssetClass.ACOES_BR,
                quantity = 100.0,
                averagePrice = 30.0,
                currentPrice = 36.0,
                sector = "Bancos",
                dataComDay = 30,
                dataSourceLabel = "Teste",
                isDemo = false,
                hasMarketPrice = true
            )
        )
        val operations = listOf(
            com.example.data.local.PortfolioOperationEntity(
                id = 10L,
                portfolioType = PortfolioType.REAL.name,
                ticker = "ITUB4",
                operationType = OperationType.PROVENTO.name,
                quantity = 1.0,
                unitPrice = 150.0,
                totalValue = 150.0,
                dateIso = "10/11/2026",
                notes = "JCP"
            )
        )
        val benchmarks = listOf(
            com.example.domain.model.BenchmarkSeries(
                name = "CDI (Selic)",
                currentLabel = "+11,0%",
                return12mPercent = 11.0,
                normalizedPoints12m = (0..11).map { 100.0 + it * 1.0 },
                source = "BCB"
            ),
            com.example.domain.model.BenchmarkSeries(
                name = "Ibovespa",
                currentLabel = "+8,0%",
                return12mPercent = 8.0,
                normalizedPoints12m = listOf(100.0, 102.0, 99.0, 101.0, 103.0, 104.0, 102.0, 105.0, 106.0, 107.0, 107.5, 108.0),
                source = "B3"
            )
        )

        val fullYear = com.example.domain.model.PortfolioProfitabilityCalculator.compute(
            realPositions = positions,
            marketAssets = emptyList(),
            operations = operations,
            benchmarks = benchmarks,
            range = com.example.domain.model.ProfitabilityRange.Y1,
            referenceDateMillis = refCal.timeInMillis
        )
        assertEquals(12, fullYear.points.size)
        assertEquals(0.0, fullYear.points.first().portfolioReturnPct, 0.001)
        assertEquals(20.0, fullYear.currentReturnPct, 0.05) // 30 -> 36 = +20%
        assertEquals(25.0, fullYear.totalReturnWithDividendsPct, 0.05) // (3600 + 150)/3000 - 1 = +25%
        assertEquals(11.0, fullYear.cdiReturnPct, 0.05)
        assertEquals(14.0, fullYear.alphaVsCdiPct, 0.05)
        assertEquals(150.0, fullYear.totalDividendsBrl, 0.01)

        val threeMonths = com.example.domain.model.PortfolioProfitabilityCalculator.compute(
            realPositions = positions,
            marketAssets = emptyList(),
            operations = operations,
            benchmarks = benchmarks,
            range = com.example.domain.model.ProfitabilityRange.M3,
            referenceDateMillis = refCal.timeInMillis
        )
        assertEquals(3, threeMonths.points.size)
        assertEquals(0.0, threeMonths.points.first().portfolioReturnPct, 0.001)
        assertEquals(0.0, threeMonths.points.first().cdiReturnPct, 0.001)
    }

    @Test
    fun `non-cyclical assets do not receive G1-G4 DataComGroup across domain models, dividend monitor, and CSV import`() {
        assertEquals(DataComGroup.G4, DataComGroup.fromDay(30, isCyclical = true))
        assertEquals(null, DataComGroup.fromDay(30, isCyclical = false))
        assertEquals(null, DataComGroup.fromDay(5, isCyclical = false))

        val cyclicalPos = PortfolioItemView(
            id = 1L,
            portfolioType = PortfolioType.REAL,
            ticker = "HGLG11",
            name = "Pátria Logística",
            assetClass = AssetClass.FII,
            quantity = 10.0,
            averagePrice = 155.0,
            currentPrice = 156.40,
            sector = "Logística",
            dataComDay = 30,
            dataSourceLabel = "Manual",
            isDemo = false,
            hasMarketPrice = true,
            isCyclical = true
        )
        val nonCyclicalPos = PortfolioItemView(
            id = 2L,
            portfolioType = PortfolioType.REAL,
            ticker = "WEGE3",
            name = "WEG ON",
            assetClass = AssetClass.ACOES_BR,
            quantity = 50.0,
            averagePrice = 42.0,
            currentPrice = 52.90,
            sector = "Bens Industriais",
            dataComDay = 22,
            dataSourceLabel = "Manual",
            isDemo = false,
            hasMarketPrice = true,
            isCyclical = false
        )

        assertEquals(DataComGroup.G4, cyclicalPos.dataComGroup)
        assertEquals(null, nonCyclicalPos.dataComGroup)

        val summary = com.example.domain.model.DividendMonitorCalculator.compute(
            realPositions = listOf(cyclicalPos, nonCyclicalPos),
            marketAssets = MarketCatalogRepository.catalogAssets,
            operations = emptyList()
        )
        val hglgRow = summary.perAssetRows.first { it.ticker == "HGLG11" }
        val wegeRow = summary.perAssetRows.first { it.ticker == "WEGE3" }
        assertTrue(hglgRow.isCyclical)
        assertEquals(DataComGroup.G4, hglgRow.dataComGroup)
        assertFalse(wegeRow.isCyclical)
        assertEquals(null, wegeRow.dataComGroup)

        val csv = """
            Ticker;Nome;Classe;Quantidade;Preço Médio;Preço Atual;Setor;Data-Com;Cíclico
            HGLG11;Pátria Logística;FII;10;155,00;156,40;Logística;30;Sim
            WEGE3;WEG ON;Ações BR;50;42,00;52,90;Industrial;22;Não
        """.trimIndent()
        val parsed = SpreadsheetImporter.parseCsv("ciclicos.csv", csv)
        assertEquals(2, parsed.positions.size)
        assertTrue(parsed.positions.first { it.ticker == "HGLG11" }.isCyclical)
        assertFalse(parsed.positions.first { it.ticker == "WEGE3" }.isCyclical)
    }

    @Test
    fun `order screenshot helpers sanitize tickers, statuses, dates, and build deterministic refs`() {
        assertEquals("SMRE11", com.example.domain.model.OrderScreenshotJsonParser.sanitizeTicker("|${'$'}MRE11"))
        assertEquals("VALE3", com.example.domain.model.OrderScreenshotJsonParser.sanitizeTicker("VALE3F"))
        assertEquals("TRXF11", com.example.domain.model.OrderScreenshotJsonParser.sanitizeTicker(" trxf11 "))
        assertTrue(com.example.domain.model.OrderScreenshotJsonParser.isExecutedStatus("Executada"))
        assertFalse(com.example.domain.model.OrderScreenshotJsonParser.isExecutedStatus("Cancelada"))
        assertEquals("23/11/2026", com.example.domain.model.OrderScreenshotJsonParser.normalizeBrazilianDate("2026-11-23"))
        assertEquals("23/11/2026", com.example.domain.model.OrderScreenshotJsonParser.normalizeBrazilianDate("23/11/26"))

        val draft = com.example.domain.model.RecognizedOrderDraft(
            ticker = "TRXF11",
            operationType = OperationType.COMPRA,
            quantity = 5.0,
            unitPrice = 52.92,
            totalValue = 264.60,
            dateIso = "23/11/2026",
            timeLabel = "14:13:21"
        )
        assertTrue(draft.isReadyToLaunch)
        assertEquals(264.60, draft.computedTotalValue, 0.01)
        assertTrue(draft.buildExternalRefId().startsWith("PRINT:TRXF11:COMPRA:23/11/2026:14:13:21:"))
    }

    @Test
    fun `smart alert evaluator triggers sharp swings respecting direction and same-day cooldown`() {
        val anyAlert = com.example.data.local.AssetSwingAlertEntity(
            id = 1L,
            ticker = "MTRE3",
            thresholdPercent = 5.0,
            direction = com.example.domain.model.SwingDirection.ANY.code,
            isEnabled = true
        )
        val upOnly = anyAlert.copy(id = 2L, direction = com.example.domain.model.SwingDirection.SURGE_UP.code)
        val downOnly = anyAlert.copy(id = 3L, direction = com.example.domain.model.SwingDirection.DROP_DOWN.code)
        val alreadyFiredToday = anyAlert.copy(id = 4L, lastTriggeredDayKey = "2026-11-23")
        val disabled = anyAlert.copy(id = 5L, isEnabled = false)

        assertTrue(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(anyAlert, 5.2, "2026-11-23"))
        assertTrue(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(anyAlert, -5.0, "2026-11-23"))
        assertFalse(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(anyAlert, 4.9, "2026-11-23"))

        assertTrue(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(upOnly, 6.1, "2026-11-23"))
        assertFalse(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(upOnly, -8.5, "2026-11-23"))

        assertTrue(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(downOnly, -5.4, "2026-11-23"))
        assertFalse(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(downOnly, 7.2, "2026-11-23"))

        // Cooldown evita spam no mesmo dia, mas libera no dia seguinte
        assertFalse(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(alreadyFiredToday, 9.0, "2026-11-23"))
        assertTrue(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(alreadyFiredToday, 9.0, "2026-11-24"))
        assertFalse(com.example.domain.model.SmartAlertEvaluator.isSharpSwingTriggered(disabled, 12.0, "2026-11-23"))
    }

    @Test
    fun `smart alert evaluator computes progress and trigger state for all investment goal types`() {
        val hglg = PortfolioItemView(
            id = 1L,
            portfolioType = PortfolioType.REAL,
            ticker = "HGLG11",
            name = "Pátria Logística",
            assetClass = AssetClass.FII,
            quantity = 100.0,
            averagePrice = 150.0,
            currentPrice = 160.0,
            sector = "Logística",
            dataComDay = 30,
            dataSourceLabel = "Manual",
            isDemo = false,
            hasMarketPrice = true
        )

        val equityGoal = com.example.data.local.InvestmentGoalEntity(
            title = "Patrimônio 100k",
            goalType = com.example.domain.model.InvestmentGoalType.TOTAL_EQUITY.code,
            targetValue = 100_000.0
        )
        val equitySnap = com.example.domain.model.SmartAlertEvaluator.computeGoalProgress(
            goal = equityGoal,
            totalEquityBrl = 105_000.0,
            estimatedMonthlyDividendsBrl = 850.0,
            realPositions = listOf(hglg)
        )
        assertTrue(equitySnap.isReached)
        assertEquals(1.0f, equitySnap.progressFraction, 0.001f)

        val divGoal = com.example.data.local.InvestmentGoalEntity(
            title = "Renda 1k/mês",
            goalType = com.example.domain.model.InvestmentGoalType.MONTHLY_DIVIDENDS.code,
            targetValue = 1_000.0
        )
        val divSnap = com.example.domain.model.SmartAlertEvaluator.computeGoalProgress(
            goal = divGoal,
            totalEquityBrl = 50_000.0,
            estimatedMonthlyDividendsBrl = 500.0,
            realPositions = listOf(hglg)
        )
        assertFalse(divSnap.isReached)
        assertEquals(50.0, divSnap.progressPercent, 0.01)

        val assetValGoal = com.example.data.local.InvestmentGoalEntity(
            title = "R$ 15k em HGLG11",
            goalType = com.example.domain.model.InvestmentGoalType.ASSET_POSITION_VALUE.code,
            ticker = "HGLG11",
            targetValue = 15_000.0
        )
        val assetValSnap = com.example.domain.model.SmartAlertEvaluator.computeGoalProgress(
            goal = assetValGoal,
            totalEquityBrl = 50_000.0,
            estimatedMonthlyDividendsBrl = 500.0,
            realPositions = listOf(hglg)
        )
        assertTrue(assetValSnap.isReached)
        assertEquals(16_000.0, assetValSnap.currentValue, 0.01)

        val assetQtyGoal = com.example.data.local.InvestmentGoalEntity(
            title = "100 cotas HGLG11",
            goalType = com.example.domain.model.InvestmentGoalType.ASSET_QUANTITY.code,
            ticker = "HGLG11",
            targetValue = 100.0
        )
        val assetQtySnap = com.example.domain.model.SmartAlertEvaluator.computeGoalProgress(
            goal = assetQtyGoal,
            totalEquityBrl = 50_000.0,
            estimatedMonthlyDividendsBrl = 500.0,
            realPositions = listOf(hglg)
        )
        assertTrue(assetQtySnap.isReached)
        assertEquals(100.0, assetQtySnap.currentValue, 0.01)
    }

    @Test
    fun `cloud asset mapping and Gemini executive audit prompt preserve Firestore indicators, cyclical flag, and prior insights`() {
        assertEquals("PETR4", com.example.domain.model.CloudAsset.normalizeAssetDocId(" petr4 "))
        assertEquals("HGLG11", com.example.domain.model.CloudAsset.normalizeAssetDocId("HGLG11"))

        val pos = PortfolioItemView(
            id = 5L,
            portfolioType = PortfolioType.REAL,
            ticker = "WEGE3",
            name = "WEG ON",
            assetClass = AssetClass.ACOES_BR,
            quantity = 40.0,
            averagePrice = 42.0,
            currentPrice = 52.90,
            sector = "Bens Industriais",
            dataComDay = 22,
            dataSourceLabel = "Carteira Real B3",
            isDemo = false,
            hasMarketPrice = true,
            isCyclical = false
        )
        val market = MarketCatalogRepository.catalogAssets.first { it.ticker == "WEGE3" }
        val cloudAsset = com.example.domain.model.CloudAsset.fromLocalData(
            position = pos,
            marketAsset = market,
            userId = "uid_test"
        )

        assertEquals("WEGE3", cloudAsset.id)
        assertEquals("WEGE3", cloudAsset.ticker)
        assertFalse(cloudAsset.cyclical)
        assertEquals(null, cloudAsset.resolvedDataComGroup())
        assertEquals(40.0 * 52.90, cloudAsset.totalPositionBrl(), 0.01)

        val priorInsight = com.example.data.repository.CloudAgentInsight(
            id = "ins_1",
            userId = "uid_test",
            agentRole = "ANALISTA",
            ticker = "WEGE3",
            conclusion = "Manter",
            confidence = 82L,
            thesis = "ROE elevado e expansão internacional consistente."
        )
        val prompt = com.example.data.repository.GeminiFirestoreAssetAuditService.buildExecutiveAuditPrompt(
            asset = cloudAsset,
            relatedInsights = listOf(priorInsight)
        )
        assertTrue(prompt.contains("Ticker: WEGE3"))
        assertTrue(prompt.contains("Ativo não cíclico (sem enquadramento em grupos G1–G4)"))
        assertTrue(prompt.contains("PARECERES ANTERIORES NO FIRESTORE"))
        assertTrue(prompt.contains("ROE elevado e expansão internacional consistente."))
    }

    @Test
    fun `B3BrokerageSpecMapper resolves real Genial SINACOR B3 RV and BMF specifications to tickers`() {
        val mapper = com.example.domain.model.B3BrokerageSpecMapper
        val expectedMappings = mapOf(
            "ALLOS ON ED NM" to "ALOS3",
            "FII IRIM CI" to "IRIM11",
            "FII HECTARE CI" to "HCTR11",
            "FII EXES CI" to "EXES11",
            "FII SMRE CI ER" to "SMRE11",
            "FII DEVANT CI ER" to "DEVA11",
            "FII BTG AGRO CI" to "BTAG11",
            "FIAGRO BTAG CI" to "BTAG11",
            "VULCABRAS ON NM" to "VULC3",
            "FII MAUA RE CI" to "MCRE11",
            "WIZ CO ON NM" to "WIZC3",
            "MINERVA ON NM" to "BEEF3",
            "KLABIN S/A ON N2" to "KLBN3",
            "DEXXOS PAR ON N1" to "DEXP3",
            "FII BTHF CI" to "BTHF11",
            "FII RIZA AKN CI" to "RZAK11",
            "B3 ON NM" to "B3SA3",
            "SCHULZ PN" to "SHUL4",
            "MITRE REALTY ON NM" to "MTRE3",
            "CURY S/A ON NM" to "CURY3",
            "CAIXA SEGURI ON ED NM" to "CXSE3",
            "TENDA ON NM" to "TEND3",
            "CARTESIA FII CI" to "CACR11",
            "FII REC REND CI" to "RECR11",
            "FII EQI RECE CI" to "EQIR11",
            "LAVVI ON NM" to "LAVV3",
            "ISA ENERGIA PN EJ N1" to "ISAE4",
            "CEA MODAS ON NM" to "CEAB3",
            "COPEL ON NM" to "CPLE3",
            "FIAGRO ECO CI" to "EGAF11",
            "FII RB YIELD CI" to "RBRI11",
            "WIN Z26" to "WINZ26",
            "WDO F27" to "WDOF27"
        )

        expectedMappings.forEach { (spec, expectedTicker) ->
            assertEquals("Falha ao mapear '$spec'", expectedTicker, mapper.resolveTicker("", spec))
        }
        assertTrue(mapper.isFuturesTicker("WINZ26"))
        assertTrue(mapper.isFuturesTicker("WDOF27"))
        assertFalse(mapper.isFuturesTicker("ALOS3"))
        assertFalse(mapper.isFuturesTicker("DEVA11"))
    }

    @Test
    fun `OrderScreenshotJsonParser consolidates B3 partial fills and preserves BMF futures BRL adjustments`() {
        val partial1 = com.example.domain.model.RecognizedOrderDraft(
            ticker = "ALOS3",
            operationType = com.example.domain.model.OperationType.COMPRA,
            quantity = 2.0,
            unitPrice = 26.90,
            totalValue = 53.80,
            dateIso = "12/11/2026",
            specificationTitle = "ALLOS ON NM",
            noteNumber = "1656",
            marketSegment = "FRACIONARIO",
            isDayTrade = false
        )
        val partial2 = com.example.domain.model.RecognizedOrderDraft(
            ticker = "ALOS3",
            operationType = com.example.domain.model.OperationType.COMPRA,
            quantity = 3.0,
            unitPrice = 27.00,
            totalValue = 81.00,
            dateIso = "12/11/2026",
            specificationTitle = "ALLOS ON NM",
            noteNumber = "1656",
            marketSegment = "FRACIONARIO",
            isDayTrade = false
        )
        val devaDt = com.example.domain.model.RecognizedOrderDraft(
            ticker = "DEVA11",
            operationType = com.example.domain.model.OperationType.VENDA,
            quantity = 10.0,
            unitPrice = 24.50,
            totalValue = 245.00,
            dateIso = "16/11/2026",
            specificationTitle = "FII DEVANT CI ER",
            noteNumber = "2104",
            marketSegment = "VISTA",
            isDayTrade = true
        )

        val consolidated = com.example.domain.model.OrderScreenshotJsonParser.consolidatePartialFills(
            listOf(partial1, partial2, devaDt)
        )
        assertEquals(2, consolidated.size)

        val alos = consolidated.first { it.ticker == "ALOS3" }
        assertEquals(5.0, alos.quantity, 0.001)
        assertEquals(26.96, alos.unitPrice, 0.001) // (2*26.90 + 3*27.00) / 5
        assertEquals(134.80, alos.computedTotalValue, 0.01)
        assertTrue(alos.buildExternalRefId().startsWith("NOTA:1656:ALOS3:COMPRA:"))
        assertTrue(alos.notes.contains("Consolidado de 2 execuções parciais"))

        val deva = consolidated.first { it.ticker == "DEVA11" }
        assertTrue(deva.isDayTrade)
        assertTrue(deva.buildExternalRefId().contains(":DT:"))

        // BM&F Futuros: unitPrice em pontos (167.625) NÃO pode ser multiplicado por contratos no totalValue em reais (49,60)
        val win = com.example.domain.model.RecognizedOrderDraft(
            ticker = "WINZ26",
            operationType = com.example.domain.model.OperationType.VENDA,
            quantity = 2.0,
            unitPrice = 167625.0,
            totalValue = 49.60,
            dateIso = "03/11/2026",
            specificationTitle = "WIN Z26 16/12/2026",
            noteNumber = "534005",
            marketSegment = "BMF_FUTUROS",
            isDayTrade = true
        )
        assertTrue(win.isFuturesContract)
        assertEquals(167625.0, win.unitPrice, 0.01)
        assertEquals(49.60, win.computedTotalValue, 0.01)
        assertTrue(win.isReadyToLaunch)
        assertTrue(win.buildExternalRefId().startsWith("NOTA:534005:WINZ26:VENDA:03/11/2026:DT:"))
    }

    @Test
    fun `resolveAppCheckMode uses debug token on emulator harness and reCAPTCHA Enterprise on physical debug APK`() {
        // 1. Emulador / Harness AI Studio com token injetado via Intent
        assertEquals(
            com.example.ai.AppCheckProviderMode.DEBUG_HARNESS_TOKEN,
            com.example.ai.resolveAppCheckMode(
                isDebugBuild = true,
                intentDebugToken = "12345678-aaaa-bbbb-cccc-1234567890ab",
                hasSavedHarnessDebugToken = false,
                recaptchaSiteKey = "6Lc2ZB0uAAAAAEFDwmkJPffRvbjNMf14qpKja0Wy"
            )
        )

        // 2. Reabertura do emulador que já recebeu token via Intent anteriormente
        assertEquals(
            com.example.ai.AppCheckProviderMode.DEBUG_HARNESS_TOKEN,
            com.example.ai.resolveAppCheckMode(
                isDebugBuild = true,
                intentDebugToken = null,
                hasSavedHarnessDebugToken = true,
                recaptchaSiteKey = "6Lc2ZB0uAAAAAEFDwmkJPffRvbjNMf14qpKja0Wy"
            )
        )

        // 3. Smartphone físico instalando app-debug.apk sem token de depuração via Intent -> usa reCAPTCHA Enterprise
        assertEquals(
            com.example.ai.AppCheckProviderMode.RECAPTCHA_ENTERPRISE,
            com.example.ai.resolveAppCheckMode(
                isDebugBuild = true,
                intentDebugToken = null,
                hasSavedHarnessDebugToken = false,
                recaptchaSiteKey = "6Lc2ZB0uAAAAAEFDwmkJPffRvbjNMf14qpKja0Wy"
            )
        )

        // 4. Build Release -> sempre reCAPTCHA Enterprise
        assertEquals(
            com.example.ai.AppCheckProviderMode.RECAPTCHA_ENTERPRISE,
            com.example.ai.resolveAppCheckMode(
                isDebugBuild = false,
                intentDebugToken = "ignored-in-release",
                hasSavedHarnessDebugToken = true,
                recaptchaSiteKey = "6Lc2ZB0uAAAAAEFDwmkJPffRvbjNMf14qpKja0Wy"
            )
        )

        // 5. Debug local sem Intent token e sem recaptcha_site_key -> fallback DebugAppCheckProviderFactory
        assertEquals(
            com.example.ai.AppCheckProviderMode.DEBUG_FALLBACK,
            com.example.ai.resolveAppCheckMode(
                isDebugBuild = true,
                intentDebugToken = null,
                hasSavedHarnessDebugToken = false,
                recaptchaSiteKey = null
            )
        )
    }
}

