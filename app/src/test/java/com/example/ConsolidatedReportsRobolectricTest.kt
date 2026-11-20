package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        composeRule.onNodeWithTag("profitchart_endpoint_input").assertIsDisplayed()
        composeRule.onNodeWithTag("profitchart_lazy_column")
            .performScrollToNode(hasTestTag("profitchart_connect_sync_button"))
        composeRule.onNodeWithTag("profitchart_connect_sync_button").assertIsDisplayed()
        composeRule.onNodeWithTag("profitchart_lazy_column")
            .performScrollToNode(hasTestTag("profitchart_import_file_button"))
        composeRule.onNodeWithTag("profitchart_import_file_button").assertIsDisplayed()
    }

    @Test
    fun `dashboard hides banners, displays dividend monitor, and opens class asset screen on chart click`() {
        val samplePositions = listOf(
            com.example.domain.model.PortfolioItemView(
                id = 1L,
                portfolioType = com.example.domain.model.PortfolioType.REAL,
                ticker = "VALE3",
                name = "Vale S.A.",
                assetClass = com.example.domain.model.AssetClass.ACOES_BR,
                quantity = 20.0,
                averagePrice = 66.82,
                currentPrice = 64.96,
                sector = "Mineração",
                dataComDay = 11,
                dataSourceLabel = "Importação XLSX",
                isDemo = false,
                hasMarketPrice = true
            ),
            com.example.domain.model.PortfolioItemView(
                id = 2L,
                portfolioType = com.example.domain.model.PortfolioType.REAL,
                ticker = "HGLG11",
                name = "Pátria Logística",
                assetClass = com.example.domain.model.AssetClass.FII,
                quantity = 10.0,
                averagePrice = 155.0,
                currentPrice = 156.40,
                sector = "Logística",
                dataComDay = 30,
                dataSourceLabel = "Importação XLSX",
                isDemo = false,
                hasMarketPrice = true
            )
        )
        val state = com.example.ui.viewmodel.InvestDashboardState(
            realPositions = samplePositions,
            realHhi = com.example.domain.model.HhiRiskReport.calculate(samplePositions, 0.0),
            marketAssets = com.example.data.repository.MarketCatalogRepository.catalogAssets
        )

        var openedClass: com.example.domain.model.AssetClass? = null
        var hideToggledTo: Boolean? = null

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.DashboardScreen(
                    state = state,
                    onNavigate = {},
                    onLoadDemoPortfolio = {},
                    onOpenPortfolioClass = { openedClass = it },
                    onToggleHideBanners = { hideToggledTo = it }
                )
            }
        }

        // Ocultar mensagens
        composeRule.onNodeWithTag("dashboard_hide_banners_button").assertIsDisplayed().performClick()
        assert(hideToggledTo == true)

        // Clicar na fatia/classe do gráfico abre a classe correspondente
        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_class_slice_0"))
        composeRule.onNodeWithTag("dashboard_class_slice_0").performClick()
        assert(openedClass == com.example.domain.model.AssetClass.FII)

        // Monitor de dividendos visível com recebido/projetado mensal e anual
        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_dividend_monitor_card"))
        composeRule.onNodeWithTag("dashboard_dividend_monitor_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dividend_received_monthly").assertExists()
        composeRule.onNodeWithTag("dividend_projected_annual").assertExists()
        composeRule.onNodeWithTag("dividend_monitor_record_button").assertExists()
        composeRule.onNodeWithTag("dividend_tab_jan_dez").performClick()
        composeRule.onNodeWithTag("dividend_jan_dec_chart").assertExists()
    }

    @Test
    fun `navigating to Comparador, Rebalanceamento, and IR DARF renders all 3 new modules`() {
        composeRule.setContent {
            InvestAiTheme {
                InvestAppRoot(userDisplayName = "Investidor Teste")
            }
        }

        // 17. Comparador & Valuation
        composeRule.onNodeWithTag("open_drawer_button").performClick()
        composeRule.onNodeWithTag("drawer_item_comparador_valuation").performScrollTo().performClick()
        composeRule.onNodeWithTag("comparator_lazy_column")
            .performScrollToNode(hasTestTag("comparator_radar_card"))
        composeRule.onNodeWithTag("comparator_radar_card").assertIsDisplayed()
        composeRule.onNodeWithTag("comparator_lazy_column")
            .performScrollToNode(hasTestTag("comparator_table_card"))
        composeRule.onNodeWithTag("comparator_table_card").assertIsDisplayed()

        // 18. Aporte & Rebalanceamento
        composeRule.onNodeWithTag("open_drawer_button").performClick()
        composeRule.onNodeWithTag("drawer_item_rebalanceamento_aporte").performScrollTo().performClick()
        composeRule.onNodeWithTag("rebalance_lazy_column")
            .performScrollToNode(hasTestTag("rebalance_contribution_input"))
        composeRule.onNodeWithTag("rebalance_contribution_input").assertIsDisplayed()
        composeRule.onNodeWithTag("rebalance_lazy_column")
            .performScrollToNode(hasTestTag("rebalance_impact_card"))
        composeRule.onNodeWithTag("rebalance_impact_card").assertIsDisplayed()

        // 19. IR Mensal & DARF
        composeRule.onNodeWithTag("open_drawer_button").performClick()
        composeRule.onNodeWithTag("drawer_item_imposto_renda_darf").performScrollTo().performClick()
        composeRule.onNodeWithTag("tax_darf_lazy_column")
            .performScrollToNode(hasTestTag("tax_carry_common_input"))
        composeRule.onNodeWithTag("tax_carry_common_input").assertIsDisplayed()
        composeRule.onNodeWithTag("tax_darf_lazy_column")
            .performScrollToNode(hasTestTag("save_tax_carry_button"))
        composeRule.onNodeWithTag("save_tax_carry_button").assertIsDisplayed()
        composeRule.onNodeWithTag("tax_darf_lazy_column")
            .performScrollToNode(hasTestTag("tax_empty_card"))
        composeRule.onNodeWithTag("tax_empty_card").assertIsDisplayed()
    }

    @Test
    fun `import preview dialog displays detected dividends alongside asset positions`() {
        val parsed = com.example.data.importexport.SpreadsheetImporter.parseCsv(
            "modelo.csv",
            com.example.data.importexport.SpreadsheetImporter.generateStandardCsvTemplate()
        )
        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.ImportPreviewDialog(
                    preview = parsed,
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        composeRule.onNodeWithTag("preview_counts_label").assertIsDisplayed()
        composeRule.onNodeWithText("5 ativos • 3 provento(s)").assertIsDisplayed()
    }

    @Test
    fun `fundamental analysis ranking shows only matching assets and displays empty state when none match`() {
        val strictNoMatchState = com.example.ui.viewmodel.InvestDashboardState(
            strategyCriteria = com.example.data.local.StrategyCriteriaEntity(
                minDyPercent = 15.5,
                maxPvp = 0.70,
                minDailyLiquidityMillions = 20.0,
                maxInadimplenciaPercent = 0.0,
                minRoePercent = 25.0
            )
        )

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.FundamentalAnalysisScreen(
                    state = strictNoMatchState,
                    onUpdateCriteria = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("fundamental_lazy_column")
            .performScrollToNode(hasTestTag("fundamental_ranking_header"))
        composeRule.onNodeWithText("Ranking Fundamentalista (0/15)").assertIsDisplayed()
        composeRule.onNodeWithTag("fundamental_lazy_column")
            .performScrollToNode(hasTestTag("fundamental_ranking_empty_card"))
        composeRule.onNodeWithTag("fundamental_ranking_empty_card").assertIsDisplayed()
        composeRule.onNodeWithTag("fundamental_asset_card_WEGE3").assertDoesNotExist()
        composeRule.onNodeWithTag("fundamental_asset_card_ITUB4").assertDoesNotExist()

        composeRule.onNodeWithTag("reset_fundamental_filters_button").performClick()

        composeRule.onNodeWithTag("fundamental_ranking_empty_card").assertDoesNotExist()
        composeRule.onNodeWithTag("fundamental_lazy_column")
            .performScrollToNode(hasTestTag("fundamental_asset_card_KNCR11"))
        composeRule.onNodeWithTag("fundamental_asset_card_KNCR11").assertIsDisplayed()
        composeRule.onNodeWithTag("fundamental_asset_card_WEGE3").assertDoesNotExist()
        composeRule.onNodeWithTag("fundamental_asset_card_ITUB4").assertDoesNotExist()
    }

    @Test
    fun `typing ticker in Dashboard comparison chart and AssetComparator adds asset and triggers live lookup when needed`() {
        var queriedTickers by mutableStateOf(emptyList<String>())
        var extraAssets by mutableStateOf(emptyList<com.example.domain.model.MarketAsset>())

        composeRule.setContent {
            InvestAiTheme {
                val baseCatalog = com.example.data.repository.MarketCatalogRepository.catalogAssets
                val state = com.example.ui.viewmodel.InvestDashboardState(
                    marketAssets = extraAssets + baseCatalog
                )
                com.example.ui.screens.DashboardScreen(
                    state = state,
                    onNavigate = {},
                    onLoadDemoPortfolio = {},
                    onQueryLiveQuote = { ticker ->
                        queriedTickers = queriedTickers + ticker
                        if (ticker.equals("PETR4", ignoreCase = true)) {
                            val liveQuote = com.example.domain.model.LiveMarketQuote(
                                ticker = "PETR4",
                                companyName = "Petrobras PN",
                                price = 38.50,
                                changePercent1d = 1.4,
                                priceSeries30d = listOf(35.0, 35.8, 36.4, 37.1, 37.9, 38.5),
                                primarySourceLabel = "B3 Oficial",
                                consultedSources = listOf("B3 Oficial"),
                                fetchedAtMillis = 1_763_578_800_000L,
                                marketTimestampLabel = "20/11 12:00",
                                b3PageUrl = "https://www.b3.com.br/",
                                advfnPageUrl = "https://br.advfn.com/"
                            )
                            extraAssets = listOf(
                                com.example.data.repository.LiveMarketDataRepository.mergeLiveQuoteIntoMarketAsset(null, liveQuote)
                            )
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_compare_ticker_input"))
        composeRule.onNodeWithTag("dashboard_compare_ticker_input").performTextInput("WEGE3")
        composeRule.onNodeWithTag("dashboard_compare_ticker_button").performClick()

        composeRule.onNodeWithTag("compare_picker_chip_WEGE3").assertIsDisplayed()

        composeRule.onNodeWithTag("dashboard_compare_ticker_input").performTextInput("PETR4")
        composeRule.onNodeWithTag("dashboard_compare_ticker_button").performClick()

        org.junit.Assert.assertTrue("PETR4" in queriedTickers)
        composeRule.onNodeWithTag("compare_picker_chip_PETR4").assertIsDisplayed()
    }

    @Test
    fun `typing ticker in AssetComparatorScreen selects it for Radar comparison`() {
        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.AssetComparatorScreen(
                    state = com.example.ui.viewmodel.InvestDashboardState()
                )
            }
        }

        composeRule.onNodeWithTag("comparator_ticker_input").performTextInput("WEGE3")
        composeRule.onNodeWithTag("comparator_add_ticker_button").performClick()

        composeRule.onNodeWithTag("comparator_lookup_feedback").assertIsDisplayed()
        composeRule.onNodeWithTag("comparator_lazy_column")
            .performScrollToNode(hasTestTag("comparator_table_header_WEGE3"))
        composeRule.onNodeWithTag("comparator_table_header_WEGE3").assertIsDisplayed()
    }

    @Test
    fun `analysis screen creates price target alert and notification helper dispatches Android push notification`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val appShadow = org.robolectric.Shadows.shadowOf(context)
        appShadow.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        var createdAlerts by mutableStateOf(emptyList<com.example.data.local.PriceTargetAlertEntity>())

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.FundamentalAnalysisScreen(
                    state = com.example.ui.viewmodel.InvestDashboardState(
                        priceTargetAlerts = createdAlerts
                    ),
                    onUpdateCriteria = { _, _ -> },
                    onCreatePriceAlert = { ticker, target, cond, notes ->
                        createdAlerts = createdAlerts + com.example.data.local.PriceTargetAlertEntity(
                            id = 1L,
                            ticker = ticker,
                            targetPriceBrl = target,
                            condition = cond.code,
                            isEnabled = true,
                            hasTriggered = true,
                            triggeredAt = 1_763_578_800_000L,
                            lastEvaluatedPriceBrl = 59.50,
                            lastEvaluatedAt = 1_763_578_800_000L,
                            notes = notes
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithTag("fundamental_lazy_column")
            .performScrollToNode(hasTestTag("alert_target_price_input"))
        composeRule.onNodeWithTag("alert_target_price_input").performTextInput("60,00")
        composeRule.onNodeWithTag("fundamental_lazy_column")
            .performScrollToNode(hasTestTag("create_price_alert_button"))
        composeRule.onNodeWithTag("create_price_alert_button").performClick()

        composeRule.onNodeWithTag("fundamental_lazy_column")
            .performScrollToNode(hasTestTag("price_alert_item_ITUB4"))
        composeRule.onNodeWithTag("price_alert_item_ITUB4").assertIsDisplayed()
        composeRule.onNodeWithText("META ATINGIDA").performScrollTo().assertIsDisplayed()

        val alertEntity = createdAlerts.first()
        val dispatched = com.example.notifications.PriceAlertNotificationHelper.sendPriceTargetNotification(
            context = context,
            alert = alertEntity,
            currentPriceBrl = 59.50,
            sourceLabel = "B3 Oficial"
        )
        org.junit.Assert.assertTrue(dispatched)

        val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)
        org.junit.Assert.assertTrue(shadowNm.allNotifications.isNotEmpty())
        val postedTitle = shadowNm.allNotifications.first().extras
            .getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        org.junit.Assert.assertTrue(postedTitle.contains("Meta atingida"))
    }
}



