package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.ui.navigation.InvestAppRoot
import com.example.ui.theme.InvestAiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConsolidatedReportsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `authenticated app root navigates across modules and renders consolidated reports dashboard`() {
        composeRule.setContent {
            InvestAiTheme {
                InvestAppRoot(userDisplayName = "Investidor Teste")
            }
        }

        composeRule.onNodeWithText("Dashboard Geral").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_nav_carteira").performClick()
        composeRule.onNodeWithTag("import_spreadsheet_button").assertIsDisplayed()
        composeRule.onNodeWithTag("download_csv_template_button").assertExists()

        composeRule.onNodeWithTag("bottom_nav_analise_fundamentalista").performClick()
        composeRule.onNodeWithText("Análise Fundamentalista").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_nav_simulador_carteira").performClick()
        composeRule.onNodeWithText("Simulador de Carteira").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_nav_oportunidades_ia").performClick()
        composeRule.onNodeWithTag("ai_consent_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_mode_chat_tab").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_message_input").assertExists()
        composeRule.onNodeWithTag("send_chat_message_button").assertExists()
        composeRule.onNodeWithTag("ai_mode_structured_tab").performClick()
        composeRule.onNodeWithTag("run_ai_agent_button").assertExists()

        composeRule.onNodeWithTag("open_drawer_button").performClick()
        composeRule.onNodeWithTag("drawer_item_relatorios_gerenciais").performScrollTo().performClick()
        composeRule.onNodeWithText("Relatórios Gerenciais").assertIsDisplayed()
        composeRule.onNodeWithTag("run_consolidated_agents_button").assertIsDisplayed()
        composeRule.onNodeWithTag("reports_lazy_column")
            .performScrollToNode(hasTestTag("consolidated_consensus_card"))
        composeRule.onNodeWithTag("consolidated_consensus_card").assertExists()
        composeRule.onNodeWithTag("reports_lazy_column")
            .performScrollToNode(hasTestTag("agent_panel_analista"))
        composeRule.onNodeWithTag("agent_panel_analista").assertExists()
        composeRule.onNodeWithTag("reports_lazy_column")
            .performScrollToNode(hasTestTag("agent_panel_gestor"))
        composeRule.onNodeWithTag("agent_panel_gestor").assertExists()
        composeRule.onNodeWithTag("reports_lazy_column")
            .performScrollToNode(hasTestTag("agent_panel_auditor"))
        composeRule.onNodeWithTag("agent_panel_auditor").assertExists()
        composeRule.onNodeWithTag("reports_lazy_column")
            .performScrollToNode(hasTestTag("consolidated_risks_card"))
        composeRule.onNodeWithTag("consolidated_risks_card").assertExists()
        composeRule.onNodeWithTag("reports_lazy_column")
            .performScrollToNode(hasTestTag("sync_report_firestore_button"))
        composeRule.onNodeWithTag("sync_report_firestore_button").assertExists()
    }

    @Test
    fun `clicking Data-Com colored bar on dashboard opens group asset list screen`() {
        composeRule.setContent {
            InvestAiTheme {
                InvestAppRoot(userDisplayName = "Investidor Teste")
            }
        }

        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_datacom_bar_0"))
        composeRule.onNodeWithTag("dashboard_datacom_bar_0").performClick()

        composeRule.onNodeWithText("Grupo G1 • Datas-Com").assertIsDisplayed()
        composeRule.onNodeWithTag("topbar_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("datacom_group_detail_list")
            .performScrollToNode(hasTestTag("datacom_group_back_button"))
        composeRule.onNodeWithTag("datacom_group_back_button").assertIsDisplayed()
    }

    @Test
    fun `clicking green ticker chip in Portfolio opens asset profile and edit sheet`() {
        val samplePos = com.example.domain.model.PortfolioItemView(
            id = 10L,
            portfolioType = com.example.domain.model.PortfolioType.REAL,
            ticker = "VALE3",
            name = "Vale ON",
            assetClass = com.example.domain.model.AssetClass.ACOES_BR,
            quantity = 20.0,
            averagePrice = 66.82,
            currentPrice = 64.96,
            sector = "Mineração",
            dataComDay = 11,
            dataSourceLabel = "Importação XLSX",
            isDemo = false,
            hasMarketPrice = true
        )
        val state = com.example.ui.viewmodel.InvestDashboardState(
            realPositions = listOf(samplePos),
            realHhi = com.example.domain.model.HhiRiskReport.calculate(listOf(samplePos), 0.0),
            marketAssets = com.example.data.repository.MarketCatalogRepository.catalogAssets
        )

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.PortfolioScreen(
                    state = state,
                    selectedType = com.example.domain.model.PortfolioType.REAL,
                    importFeedback = null,
                    pendingImportPreview = null,
                    onSelectType = {},
                    onImportUri = {},
                    onConfirmPendingImport = {},
                    onDismissPendingImport = {},
                    onDismissImportFeedback = {},
                    onBuildTemplateCsv = { "" },
                    onBuildPortfolioCsv = { "" },
                    onNotify = {},
                    onLoadDemoReal = {},
                    onClearPortfolio = {},
                    onAddManualPosition = { _, _, _, _, _, _, _, _, _ -> },
                    onRecordOperation = { _, _, _, _, _, _ -> },
                    onDeletePosition = {},
                    onUpdateCash = { _, _ -> },
                    onUpdatePosition = { _, _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("portfolio_lazy_column")
            .performScrollToNode(hasTestTag("ticker_edit_button_VALE3"))
        composeRule.onNodeWithTag("ticker_edit_button_VALE3").performClick()

        composeRule.onNodeWithText("Cadastro e Edição • VALE3").assertIsDisplayed()
        composeRule.onNodeWithTag("save_position_edit_button").assertExists()
    }

    @Test
    fun `asset analysis screen displays live B3 ADVFN lookup panel and renders live quote result`() {
        val sampleQuote = com.example.domain.model.LiveMarketQuote(
            ticker = "VALE3",
            companyName = "Vale S.A.",
            price = 65.42,
            changePercent1d = 1.35,
            openPrice = 64.50,
            dayLow = 64.10,
            dayHigh = 65.80,
            dailyVolumeBrlMillions = 1250.4,
            mm9 = 64.90,
            mm21 = 63.80,
            rsi14 = 58.2,
            supportPrice = 62.50,
            resistancePrice = 66.80,
            primarySourceLabel = "B3 Oficial + Yahoo Finance (B3 .SA)",
            consultedSources = listOf("B3 Oficial", "Yahoo Finance (B3 .SA)"),
            fetchedAtMillis = 1_763_578_800_000L,
            marketTimestampLabel = "19/11 16:40",
            b3PageUrl = "https://www.b3.com.br/",
            advfnPageUrl = "https://br.advfn.com/p.php?pid=qkquote&symbol=BOV%3AVALE3"
        )
        val state = com.example.ui.viewmodel.InvestDashboardState(
            liveLookupState = com.example.domain.model.LiveQuoteLookupState.Success(sampleQuote),
            liveQuotesByTicker = mapOf("VALE3" to sampleQuote)
        )

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.FundamentalAnalysisScreen(
                    state = state,
                    onUpdateCriteria = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("live_ticker_input").assertIsDisplayed()
        composeRule.onNodeWithTag("live_lookup_button").assertIsDisplayed()
        composeRule.onNodeWithTag("live_quote_result_card").assertIsDisplayed()
        composeRule.onNodeWithTag("open_b3_button").assertExists()
        composeRule.onNodeWithTag("open_advfn_button").assertExists()
    }

    @Test
    fun `navigating from Carteira to ProfitChart screen shows connector and import controls`() {
        composeRule.setContent {
            InvestAiTheme {
                InvestAppRoot(userDisplayName = "Investidor Teste")
            }
        }

        composeRule.onNodeWithTag("bottom_nav_carteira").performClick()
        composeRule.onNodeWithTag("open_profitchart_button").performScrollTo().performClick()

        composeRule.onNodeWithTag("profitchart_lazy_column")
            .performScrollToNode(hasTestTag("profitchart_endpoint_input"))
        composeRule.onNodeWithTag("profitchart_endpoint_input").assertExists()
        composeRule.onNodeWithTag("profitchart_lazy_column")
            .performScrollToNode(hasTestTag("profitchart_connect_sync_button"))
        composeRule.onNodeWithTag("profitchart_connect_sync_button").assertExists()
        composeRule.onNodeWithTag("profitchart_lazy_column")
            .performScrollToNode(hasTestTag("profitchart_import_file_button"))
        composeRule.onNodeWithTag("profitchart_import_file_button").assertExists()
    }
}
