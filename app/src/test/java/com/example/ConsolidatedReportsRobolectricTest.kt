package com.example

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.flow.first
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
                    onAddManualPosition = { _, _, _, _, _, _, _, _, _, _ -> },
                    onRecordOperation = { _, _, _, _, _, _ -> },
                    onDeletePosition = {},
                    onUpdateCash = { _, _ -> },
                    onUpdatePosition = { _, _, _, _, _, _, _, _, _, _, _ -> }
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

    @Test
    fun `room persists price target alerts, offline queue, cached quotes and flushes pending notifications offline`() =
        kotlinx.coroutines.runBlocking {
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            org.robolectric.Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

            val db = androidx.room.Room.inMemoryDatabaseBuilder(
                context,
                com.example.data.local.InvestDatabase::class.java
            ).allowMainThreadQueries().build()

            try {
                val alertDao = db.priceTargetAlertDao()
                val cacheDao = db.cachedMarketQuoteDao()

                val alertId = alertDao.insert(
                    com.example.data.local.PriceTargetAlertEntity(
                        ticker = "MXRF11",
                        targetPriceBrl = 9.60,
                        condition = com.example.domain.model.PriceAlertCondition.AT_OR_BELOW.code,
                        isEnabled = true,
                        hasTriggered = false,
                        notes = "Aporte FII offline"
                    )
                )

                // Simula dispositivo temporariamente offline: alerta fica enfileirado no Room
                alertDao.markOfflineQueued(listOf(alertId))
                val queued = alertDao.getActiveAlerts().first { it.id == alertId }
                org.junit.Assert.assertTrue(queued.offlineQueuedCheck)
                org.junit.Assert.assertEquals("Offline • Aguardando conexão (Room)", queued.lastCheckStatus)

                // Persiste cotação em cache no Room e simula disparo com entrega pendente
                val now = System.currentTimeMillis()
                cacheDao.upsert(
                    com.example.data.local.CachedMarketQuoteEntity(
                        ticker = "MXRF11",
                        companyName = "Maxi Renda FII",
                        price = 9.52,
                        changePercent1d = -1.2,
                        dy12m = 12.6,
                        pvp = 0.98,
                        lastDividendBrl = 0.10,
                        dataComDay = 30,
                        paymentDay = 15,
                        primarySourceLabel = "B3 Oficial",
                        marketTimestampLabel = "20/11/2026 14:40",
                        fetchedAtMillis = now
                    )
                )
                val cachedQuote = cacheDao.getByTicker("MXRF11")
                org.junit.Assert.assertNotNull(cachedQuote)
                org.junit.Assert.assertEquals(9.52, cachedQuote!!.price, 0.001)

                alertDao.markTriggered(
                    id = alertId,
                    evaluatedPriceBrl = cachedQuote.price,
                    triggeredAt = now,
                    pendingNotification = true,
                    notificationDelivered = false,
                    status = "Meta atingida • Push pendente"
                )

                val pendingList = alertDao.getPendingNotificationAlerts()
                org.junit.Assert.assertEquals(1, pendingList.size)

                val delivered = com.example.notifications.PriceAlertNotificationHelper.sendPriceTargetNotification(
                    context = context,
                    alert = pendingList.first(),
                    currentPriceBrl = pendingList.first().lastEvaluatedPriceBrl!!,
                    sourceLabel = "Cache local Room"
                )
                org.junit.Assert.assertTrue(delivered)
                alertDao.markNotificationDelivered(alertId)

                org.junit.Assert.assertTrue(alertDao.getPendingNotificationAlerts().isEmpty())
                val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val notifications = org.robolectric.Shadows.shadowOf(nm).allNotifications
                org.junit.Assert.assertTrue(
                    notifications.any {
                        it.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.contains("MXRF11") == true
                    }
                )
            } finally {
                db.close()
            }
        }

    @Test
    fun `position edit sheet aligns Data-Com group G4 with user input, warns on market divergence and hides invalid PVP`() {
        val smrePosition = com.example.domain.model.PortfolioItemView(
            id = 99L,
            portfolioType = com.example.domain.model.PortfolioType.REAL,
            ticker = "SMRE11",
            name = "SMRE11",
            assetClass = com.example.domain.model.AssetClass.FII,
            quantity = 5.0,
            averagePrice = 55.17,
            currentPrice = 56.30,
            sector = "Fundos Imobiliários",
            dataComDay = 30,
            dataSourceLabel = "Importação XLSX (editado)",
            isDemo = false,
            hasMarketPrice = true
        )
        val divergentMarketAsset = com.example.data.repository.MarketCatalogRepository.catalogAssets.first().copy(
            ticker = "SMRE11",
            name = "Smart Real Estate FII",
            assetClass = com.example.domain.model.AssetClass.FII,
            sector = "Consulta em Tempo Real",
            price = 56.30,
            dy12m = 1.78,
            pvp = 59360.38,
            dataComDay = 2
        )

        composeRule.setContent {
            InvestAiTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                ) {
                    com.example.ui.screens.AssetPositionDetailAndEditSheet(
                        position = smrePosition,
                        catalogAsset = divergentMarketAsset,
                        portfolioTotalInvestedBrl = 30_000.0,
                        onDismiss = {},
                        onSave = { _, _, _, _, _, _, _, _, _, _, _ -> },
                        onDelete = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Grupo G4").assertExists()
        composeRule.onNodeWithTag("sheet_market_datacom_value").assertExists()
        composeRule.onNodeWithText("Dia 30 (Grupo G4)").assertExists()
        composeRule.onNodeWithTag("sheet_market_pvp_value").assertExists()
        composeRule.onNodeWithText("N/D").assertExists()
        composeRule.onNodeWithTag("datacom_divergence_banner").assertExists()
        composeRule.onNodeWithTag("edit_pos_group_helper").assertExists()

        composeRule.onNodeWithTag("sync_market_datacom_button").performClick()
        composeRule.onNodeWithText("Grupo G1").assertExists()
        composeRule.onNodeWithText("Dia 2 (Grupo G1)").assertExists()
        composeRule.onNodeWithTag("datacom_divergence_banner").assertDoesNotExist()

        // Ao marcar o ativo como Não cíclico, remove a sigla do grupo G1–G4
        composeRule.onNodeWithTag("edit_pos_cyclical_no").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Grupo G1").assertDoesNotExist()
        composeRule.onNodeWithText("Grupo G4").assertDoesNotExist()
        composeRule.onNodeWithTag("edit_pos_non_cyclical_badge").assertExists()
        composeRule.onNodeWithText("Dia 2 (Não cíclico)").assertExists()
    }

    @Test
    fun `ai agents screen renders Google Search Grounding toggle, audio transcription and voice conversation controls`() {
        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.AiAgentsScreen(
                    state = com.example.ui.viewmodel.InvestDashboardState(),
                    onSetConsent = {}
                )
            }
        }

        composeRule.onNodeWithTag("ai_agents_lazy_column")
            .performScrollToNode(hasTestTag("ai_google_search_switch"))
        composeRule.onNodeWithTag("ai_google_search_switch").assertExists()
        composeRule.onNodeWithTag("ai_transcribe_audio_button").assertExists()
        composeRule.onNodeWithTag("ai_voice_conversation_button").assertExists()
    }

    @Test
    fun `live lookup panel separates Investidor10 Data-Com day 30 G4 from Yahoo Data-Ex day 03 for SMRE11`() {
        val smreQuote = com.example.domain.model.LiveMarketQuote(
            ticker = "SMRE11",
            companyName = "Smart Real Estate FII",
            price = 56.30,
            changePercent1d = 0.45,
            lastDividendBrl = 1.00,
            dataComDay = 30,
            dataExDay = 3,
            paymentDay = 16,
            primarySourceLabel = "Yahoo Finance (B3 .SA) + Investidor10",
            consultedSources = listOf("Yahoo Finance (B3 .SA)", "Investidor10"),
            fetchedAtMillis = 1_763_650_000_000L,
            marketTimestampLabel = "21/11 07:00",
            b3PageUrl = "https://www.b3.com.br/",
            advfnPageUrl = "https://br.advfn.com/",
            investidor10PageUrl = "https://investidor10.com.br/fiis/smre11/"
        )

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.components.LiveMarketLookupPanel(
                    lookupState = com.example.domain.model.LiveQuoteLookupState.Success(smreQuote),
                    quickTickers = listOf("SMRE11"),
                    ownedTickers = setOf("SMRE11"),
                    onQueryTicker = {},
                    onApplyPriceToPortfolio = {}
                )
            }
        }

        composeRule.onNodeWithText("Data-Com (Investidor10)").assertExists()
        composeRule.onNodeWithText("Dia 30 (Grupo G4)").assertExists()
        composeRule.onNodeWithText("Data-Ex (Yahoo)").assertExists()
        composeRule.onNodeWithText("Dia 3").assertExists()
        composeRule.onNodeWithTag("live_quote_datacom_vs_dataex_note").assertExists()
        composeRule.onNodeWithTag("open_investidor10_button").assertExists()
    }

    @Test
    fun `order screenshot review dialog lists recognized orders and launches selected valid orders`() {
        val sampleResult = com.example.domain.model.OrderScreenshotRecognitionResult(
            brokerHeader = "GENIAL - SWINGTRADE",
            orders = listOf(
                com.example.domain.model.RecognizedOrderDraft(
                    ticker = "TRXF11",
                    operationType = com.example.domain.model.OperationType.COMPRA,
                    quantity = 5.0,
                    unitPrice = 52.92,
                    totalValue = 264.60,
                    dateIso = "23/11/2026",
                    timeLabel = "14:13:21",
                    statusLabel = "Executada",
                    brokerLabel = "GENIAL - SWINGTRADE",
                    isExecuted = true,
                    isIncomplete = false,
                    selected = true
                ),
                com.example.domain.model.RecognizedOrderDraft(
                    ticker = "EGAF11",
                    operationType = com.example.domain.model.OperationType.COMPRA,
                    quantity = 10.0,
                    unitPrice = 72.04,
                    totalValue = 720.40,
                    dateIso = "23/11/2026",
                    timeLabel = "13:55:53",
                    statusLabel = "Executada",
                    brokerLabel = "GENIAL - SWINGTRADE",
                    isExecuted = true,
                    isIncomplete = false,
                    selected = true
                ),
                com.example.domain.model.RecognizedOrderDraft(
                    ticker = "SMRE11",
                    operationType = com.example.domain.model.OperationType.COMPRA,
                    quantity = 10.0,
                    unitPrice = 0.0,
                    totalValue = 0.0,
                    dateIso = "23/11/2026",
                    statusLabel = "Executada",
                    brokerLabel = "GENIAL - SWINGTRADE",
                    isExecuted = true,
                    isIncomplete = true,
                    selected = false,
                    notes = "Cartão cortado na barra inferior"
                )
            ),
            warnings = listOf("1 ordem(ns) parcialmente cortada(s) desmarcada(s) para revisão.")
        )

        var launchedOrders: List<com.example.domain.model.RecognizedOrderDraft> = emptyList()
        var appliedPositions = false

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.OrderScreenshotReviewDialog(
                    state = com.example.domain.model.OrderScreenshotRecognitionState.Review(
                        result = sampleResult,
                        imageUriString = "content://screenshot/genial.jpg"
                    ),
                    targetPortfolioType = com.example.domain.model.PortfolioType.REAL,
                    onDismiss = {},
                    onPickAnotherImage = {},
                    onConfirmLaunch = { orders, apply ->
                        launchedOrders = orders
                        appliedPositions = apply
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("order_screenshot_dialog").assertExists()
        composeRule.onNodeWithText("Revisar Ordens do Print").assertExists()
        composeRule.onNodeWithTag("confirm_screenshot_orders_button").performClick()

        org.junit.Assert.assertEquals(2, launchedOrders.size)
        org.junit.Assert.assertEquals("TRXF11", launchedOrders[0].ticker)
        org.junit.Assert.assertEquals("EGAF11", launchedOrders[1].ticker)
        org.junit.Assert.assertTrue(appliedPositions)
    }

    @Test
    fun `order screenshot JSON parser extracts Genial Profit orders and flags occluded or non-executed rows`() {
        val sampleJson = """
            {
              "brokerHeader": "GENIAL - SWINGTRADE",
              "warnings": [],
              "orders": [
                {
                  "ticker": "TRXF11",
                  "side": "COMPRA",
                  "quantity": 5.0,
                  "unitPrice": 52.92,
                  "totalValue": 264.60,
                  "dateBr": "23/11/2026",
                  "timeLabel": "14:13:21",
                  "status": "Executada",
                  "orderType": "Limite",
                  "isIncomplete": false,
                  "notes": "Normal"
                },
                {
                  "ticker": "EGAF11",
                  "side": "COMPRA",
                  "quantity": 10.0,
                  "unitPrice": 72.04,
                  "totalValue": 720.40,
                  "dateBr": "23/11/2026",
                  "timeLabel": "13:55:53",
                  "status": "Executada",
                  "orderType": "Limite",
                  "isIncomplete": false,
                  "notes": "Normal"
                },
                {
                  "ticker": "|${'$'}MRE11",
                  "side": "COMPRA",
                  "quantity": 10.0,
                  "unitPrice": 0.0,
                  "totalValue": 0.0,
                  "dateBr": "",
                  "timeLabel": "",
                  "status": "Executada",
                  "orderType": "",
                  "isIncomplete": true,
                  "notes": "Cartão cortado atrás da barra inferior"
                },
                {
                  "ticker": "VALE3F",
                  "side": "VENDA",
                  "quantity": 20.0,
                  "unitPrice": 61.50,
                  "totalValue": 0.0,
                  "dateBr": "2026-11-23",
                  "timeLabel": "11:02:10",
                  "status": "Cancelada",
                  "orderType": "Limite",
                  "isIncomplete": false,
                  "notes": ""
                }
              ]
            }
        """.trimIndent()

        val result = com.example.domain.model.OrderScreenshotJsonParser.parse(
            jsonText = sampleJson,
            fallbackDateBr = "23/11/2026"
        )

        org.junit.Assert.assertEquals("GENIAL - SWINGTRADE", result.brokerHeader)
        org.junit.Assert.assertEquals(4, result.orders.size)
        org.junit.Assert.assertEquals("TRXF11", result.orders[0].ticker)
        org.junit.Assert.assertTrue(result.orders[0].selected)
        org.junit.Assert.assertEquals("EGAF11", result.orders[1].ticker)
        org.junit.Assert.assertTrue(result.orders[1].selected)
        org.junit.Assert.assertEquals("SMRE11", result.orders[2].ticker)
        org.junit.Assert.assertTrue(result.orders[2].isIncomplete)
        org.junit.Assert.assertFalse(result.orders[2].selected)
        org.junit.Assert.assertEquals("VALE3", result.orders[3].ticker)
        org.junit.Assert.assertEquals(com.example.domain.model.OperationType.VENDA, result.orders[3].operationType)
        org.junit.Assert.assertFalse(result.orders[3].isExecuted)
        org.junit.Assert.assertFalse(result.orders[3].selected)
        org.junit.Assert.assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `alerts and goals screen creates swing alert, investment goal, and dispatches local notifications via Room v9`() =
        kotlinx.coroutines.runBlocking {
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            org.robolectric.Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

            val db = androidx.room.Room.inMemoryDatabaseBuilder(
                context,
                com.example.data.local.InvestDatabase::class.java
            ).allowMainThreadQueries().build()

            try {
                val smartDao = db.smartAlertDao()
                val swingId = smartDao.insertSwingAlert(
                    com.example.data.local.AssetSwingAlertEntity(
                        ticker = "MTRE3",
                        thresholdPercent = 5.0,
                        direction = com.example.domain.model.SwingDirection.ANY.code,
                        isEnabled = true,
                        notes = "Monitorar volatilidade"
                    )
                )
                val goalId = smartDao.insertInvestmentGoal(
                    com.example.data.local.InvestmentGoalEntity(
                        title = "Primeiros 100k",
                        goalType = com.example.domain.model.InvestmentGoalType.TOTAL_EQUITY.code,
                        targetValue = 100_000.0,
                        isEnabled = true
                    )
                )
                org.junit.Assert.assertEquals(1, smartDao.getActiveSwingAlerts().size)
                org.junit.Assert.assertEquals(1, smartDao.getActiveInvestmentGoals().size)

                val swingAlert = smartDao.getActiveSwingAlerts().first { it.id == swingId }
                val goalEntity = smartDao.getActiveInvestmentGoals().first { it.id == goalId }

                val swingPushSent = com.example.notifications.SmartAlertNotificationHelper.sendSharpSwingNotification(
                    context = context,
                    alert = swingAlert,
                    changePercent1d = -6.45,
                    currentPriceBrl = 4.12,
                    sourceLabel = "B3 Oficial"
                )
                val goalPushSent = com.example.notifications.SmartAlertNotificationHelper.sendInvestmentGoalNotification(
                    context = context,
                    goal = goalEntity,
                    currentValue = 102_500.0,
                    targetValue = 100_000.0
                )
                org.junit.Assert.assertTrue(swingPushSent)
                org.junit.Assert.assertTrue(goalPushSent)

                smartDao.insertNotificationHistory(
                    com.example.data.local.AlertNotificationHistoryEntity(
                        category = "SWING",
                        ticker = "MTRE3",
                        title = "Queda brusca em MTRE3 (-6,45%)",
                        message = "Oscilação atingiu gatilho de 5,0%",
                        deliveredAsPush = true
                    )
                )
                val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val postedTitles = org.robolectric.Shadows.shadowOf(nm).allNotifications.map {
                    it.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
                }
                org.junit.Assert.assertTrue(postedTitles.any { it.contains("MTRE3") })
                org.junit.Assert.assertTrue(postedTitles.any { it.contains("Primeiros 100k") })
            } finally {
                db.close()
            }

            var createdSwingTicker = ""
            var createdGoalTitle = ""
            composeRule.setContent {
                InvestAiTheme {
                    com.example.ui.screens.AlertsAndGoalsScreen(
                        state = com.example.ui.viewmodel.InvestDashboardState(),
                        smartState = com.example.ui.viewmodel.SmartAlertsUiState(),
                        onCreateSwingAlert = { ticker, _, _, _ -> createdSwingTicker = ticker },
                        onToggleSwingAlert = { _, _ -> },
                        onResetSwingAlert = {},
                        onDeleteSwingAlert = {},
                        onCreateInvestmentGoal = { title, _, _, _, _ -> createdGoalTitle = title },
                        onToggleInvestmentGoal = { _, _ -> },
                        onRearmInvestmentGoal = {},
                        onDeleteInvestmentGoal = {},
                        onCreatePriceAlert = { _, _, _, _ -> },
                        onTogglePriceAlert = { _, _ -> },
                        onRearmPriceAlert = {},
                        onDeletePriceAlert = {},
                        onCheckPriceAlertsNow = {},
                        onCheckAllSmartAlertsNow = {},
                        onSendTestNotification = {},
                        onClearAlertHistory = {},
                        onNotificationPermissionGranted = {}
                    )
                }
            }

            composeRule.onNodeWithTag("alerts_and_goals_lazy_column")
                .performScrollToNode(hasTestTag("swing_ticker_input"))
            composeRule.onNodeWithTag("swing_ticker_input").performTextInput("MTRE3")
            composeRule.onNodeWithTag("alerts_and_goals_lazy_column")
                .performScrollToNode(hasTestTag("create_swing_alert_button"))
            composeRule.onNodeWithTag("create_swing_alert_button").performClick()
            org.junit.Assert.assertEquals("MTRE3", createdSwingTicker)

            composeRule.onNodeWithTag("alerts_and_goals_lazy_column")
                .performScrollToNode(hasTestTag("smart_alerts_tab_goals"))
            composeRule.onNodeWithTag("smart_alerts_tab_goals").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("alerts_and_goals_lazy_column")
                .performScrollToNode(hasTestTag("goal_title_input"))
            composeRule.onNodeWithTag("goal_title_input").performTextInput("Meta 100k")
            composeRule.onNodeWithTag("alerts_and_goals_lazy_column")
                .performScrollToNode(hasTestTag("goal_target_value_input"))
            composeRule.onNodeWithTag("goal_target_value_input").performTextInput("100000")
            composeRule.onNodeWithTag("alerts_and_goals_lazy_column")
                .performScrollToNode(hasTestTag("create_investment_goal_button"))
            composeRule.onNodeWithTag("create_investment_goal_button").performClick()
            org.junit.Assert.assertEquals("Meta 100k", createdGoalTitle)
        }

    @Test
    fun `firestore asset audit JSON codec parses Gemini structured audit and UI card triggers audit and displays risks and opportunities`() {
        val sampleAsset = com.example.domain.model.CloudAsset(
            id = "HGLG11",
            userId = "alice_123",
            ticker = "HGLG11",
            name = "CSHG Logística FII",
            assetClass = com.example.domain.model.AssetClass.FII.name,
            sector = "Logística",
            quantity = 20.0,
            averagePrice = 155.0,
            currentPrice = 160.50,
            dy12m = 8.65,
            pvp = 1.02,
            pl = 0.0,
            roe = 0.0,
            dailyLiquidityMillions = 14.2,
            dataComDay = 30L,
            cyclical = true,
            demoData = false,
            sourceLabel = "B3 Oficial",
            dataBaseDate = "23/11/2026",
            riskSummary = "Sensível à curva de juros longos."
        )

        val geminiJson = """
            {
              "resumoExecutivo": "Ativo logístico de elevada liquidez no Firestore com P/VP equilibrado e geração recorrente de proventos.",
              "classificacaoRisco": "BAIXO",
              "scoreAuditoria": 88,
              "veredito": "FAVORAVEL",
              "riscos": [
                {
                  "titulo": "Sensibilidade à curva de juros",
                  "severidade": "MEDIA",
                  "detalhe": "Abertura da NTN-B pressiona a cota no curto prazo.",
                  "mitigacao": "Balancear com fundos de recebíveis indexados ao CDI."
                }
              ],
              "oportunidades": [
                {
                  "titulo": "Renda mensal resiliente",
                  "potencial": "ALTO",
                  "detalhe": "DY de 8,65% a.a. com portfólio de galpões AAA.",
                  "catalisador": "Revisão positiva de aluguéis e redução da Selic."
                }
              ],
              "alertasInconsistencia": [
                "P/L e ROE zerados por ser fundo imobiliário (esperado para classe FII)."
              ],
              "recomendacaoAuditoria": "Validar vacância física no último relatório gerencial na B3."
            }
        """.trimIndent()

        val parsed = com.example.domain.model.AssetAuditJsonCodec.parseGeminiAuditResponse(
            jsonText = geminiJson,
            auditId = "audit_HGLG11_test",
            asset = sampleAsset,
            modelName = "gemini-3.8-flash"
        )

        org.junit.Assert.assertEquals("HGLG11", parsed.ticker)
        org.junit.Assert.assertEquals(com.example.domain.model.AuditRiskLevel.BAIXO, parsed.riskLevel)
        org.junit.Assert.assertEquals(com.example.domain.model.AuditVerdict.FAVORAVEL, parsed.verdict)
        org.junit.Assert.assertEquals(88, parsed.auditScore)
        org.junit.Assert.assertEquals(1, parsed.risks.size)
        org.junit.Assert.assertEquals(1, parsed.opportunities.size)
        org.junit.Assert.assertEquals(1, parsed.inconsistencyAlerts.size)

        // Round-trip encode/decode via CloudAssetAudit
        val cloudAudit = com.example.domain.model.CloudAssetAudit(
            id = parsed.id,
            userId = "alice_123",
            assetId = parsed.assetId,
            ticker = parsed.ticker,
            assetName = parsed.assetName,
            riskLevel = parsed.riskLevel.name,
            auditScore = parsed.auditScore.toLong(),
            verdict = parsed.verdict.name,
            executiveSummary = parsed.executiveSummary,
            risksJson = com.example.domain.model.AssetAuditJsonCodec.encodeRisks(parsed.risks),
            opportunitiesJson = com.example.domain.model.AssetAuditJsonCodec.encodeOpportunities(parsed.opportunities),
            inconsistencyAlertsJson = com.example.domain.model.AssetAuditJsonCodec.encodeStringList(parsed.inconsistencyAlerts),
            auditRecommendation = parsed.auditRecommendation,
            modelName = parsed.modelName,
            sourceSnapshotLabel = parsed.sourceSnapshotLabel
        )
        val roundTripped = cloudAudit.toDomainSummary()
        org.junit.Assert.assertEquals(parsed.risks.first().title, roundTripped.risks.first().title)
        org.junit.Assert.assertEquals(parsed.opportunities.first().title, roundTripped.opportunities.first().title)

        var auditedAssetId = ""
        var syncClicked = false

        composeRule.setContent {
            InvestAiTheme {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        com.example.ui.components.FirestoreAssetAuditCard(
                            cloudAssetsUiState = com.example.ui.viewmodel.CloudAssetsUiState.Success(listOf(sampleAsset)),
                            assetAuditsUiState = com.example.ui.viewmodel.CloudAssetAuditsUiState.Success(listOf(roundTripped)),
                            activeAuditRunState = com.example.ui.viewmodel.AssetAuditRunState.Success(roundTripped),
                            isSyncingAssets = false,
                            onSyncAssetsToFirestore = { syncClicked = true },
                            onRunGeminiAudit = { auditedAssetId = it },
                            onSelectSavedAudit = {},
                            onDismissAuditState = {}
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("firestore_asset_audit_card").assertExists()
        composeRule.onNodeWithTag("sync_assets_firestore_button").performClick()
        org.junit.Assert.assertTrue(syncClicked)

        composeRule.onNodeWithTag("run_firestore_asset_audit_button").performClick()
        org.junit.Assert.assertEquals("HGLG11", auditedAssetId)

        composeRule.onNodeWithTag("firestore_audit_result_card").assertExists()
        composeRule.onNodeWithText("88/100").assertExists()
        composeRule.onNodeWithText("Sensibilidade à curva de juros").assertExists()
        composeRule.onNodeWithText("Renda mensal resiliente").assertExists()
        composeRule.onNodeWithTag("saved_firestore_audit_HGLG11").assertExists()
    }

    @Test
    fun `OrderScreenshotReviewDialog displays PDF brokerage note summary, Day Trade and BMF badges, and launches selected orders`() {
        val pdfResult = com.example.domain.model.OrderScreenshotRecognitionResult(
            brokerHeader = "GENIAL CCTVM S/A",
            documentKind = "NOTA_B3",
            notesCount = 12,
            totalFeesBrl = 14.35,
            totalNetSettlementBrl = -4280.50,
            orders = listOf(
                com.example.domain.model.RecognizedOrderDraft(
                    ticker = "ALOS3",
                    operationType = com.example.domain.model.OperationType.COMPRA,
                    quantity = 10.0,
                    unitPrice = 26.92,
                    totalValue = 269.20,
                    dateIso = "12/11/2026",
                    statusLabel = "Executada",
                    brokerLabel = "GENIAL CCTVM S/A",
                    specificationTitle = "ALLOS ON NM",
                    noteNumber = "1656",
                    marketSegment = "FRACIONARIO",
                    isDayTrade = false,
                    isExecuted = true,
                    isIncomplete = false,
                    selected = true,
                    notes = "Consolidado de 10 execuções parciais"
                ),
                com.example.domain.model.RecognizedOrderDraft(
                    ticker = "DEVA11",
                    operationType = com.example.domain.model.OperationType.VENDA,
                    quantity = 15.0,
                    unitPrice = 24.40,
                    totalValue = 366.00,
                    dateIso = "16/11/2026",
                    statusLabel = "Executada",
                    brokerLabel = "GENIAL CCTVM S/A",
                    specificationTitle = "FII DEVANT CI ER",
                    noteNumber = "2104",
                    marketSegment = "VISTA",
                    isDayTrade = true,
                    isExecuted = true,
                    isIncomplete = false,
                    selected = true
                ),
                com.example.domain.model.RecognizedOrderDraft(
                    ticker = "WINZ26",
                    operationType = com.example.domain.model.OperationType.COMPRA,
                    quantity = 2.0,
                    unitPrice = 167625.0,
                    totalValue = 49.60,
                    dateIso = "03/11/2026",
                    statusLabel = "Executada",
                    brokerLabel = "GENIAL CCTVM S/A",
                    specificationTitle = "WIN Z26 16/12/2026",
                    noteNumber = "534005",
                    marketSegment = "BMF_FUTUROS",
                    isDayTrade = true,
                    isExecuted = true,
                    isIncomplete = false,
                    selected = true
                )
            ),
            warnings = emptyList()
        )

        var launchedOrders: List<com.example.domain.model.RecognizedOrderDraft> = emptyList()

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.OrderScreenshotReviewDialog(
                    state = com.example.domain.model.OrderScreenshotRecognitionState.Review(
                        result = pdfResult,
                        imageUriString = "content://downloads/NotaCorretagem_01-11-26_30-11-26.pdf"
                    ),
                    targetPortfolioType = com.example.domain.model.PortfolioType.REAL,
                    onDismiss = {},
                    onPickAnotherImage = {},
                    onConfirmLaunch = { orders, _ -> launchedOrders = orders }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("order_screenshot_dialog").assertExists()
        composeRule.onNodeWithText("Revisar Nota de Corretagem (PDF)").assertExists()
        composeRule.onNodeWithTag("brokerage_note_summary_card").assertExists()
        composeRule.onNodeWithTag("screenshot_toggle_all_button").assertExists()
        composeRule.onNodeWithTag("confirm_screenshot_orders_button").performClick()

        org.junit.Assert.assertEquals(3, launchedOrders.size)
        org.junit.Assert.assertEquals("ALOS3", launchedOrders[0].ticker)
        org.junit.Assert.assertEquals("DEVA11", launchedOrders[1].ticker)
        org.junit.Assert.assertTrue(launchedOrders[1].isDayTrade)
        org.junit.Assert.assertEquals("WINZ26", launchedOrders[2].ticker)
        org.junit.Assert.assertTrue(launchedOrders[2].isFuturesContract)
        org.junit.Assert.assertEquals(49.60, launchedOrders[2].computedTotalValue, 0.01)
    }

    @Test
    fun `OrderScreenshotJsonParser parses Genial B3 RV and BMF PDF JSON payloads with consolidation and ticker resolution`() {
        val b3NoteJson = """
            {
              "documentKind": "NOTA_B3",
              "brokerHeader": "GENIAL CCTVM S/A",
              "notesCount": 2,
              "totalFeesBrl": 4.82,
              "totalNetSettlementBrl": -1250.40,
              "warnings": [],
              "orders": [
                {
                  "ticker": "",
                  "specificationTitle": "ALLOS ON NM",
                  "side": "COMPRA",
                  "marketSegment": "FRACIONARIO",
                  "isDayTrade": false,
                  "noteNumber": "1656",
                  "quantity": 2.0,
                  "unitPrice": 26.90,
                  "totalValue": 53.80,
                  "dateBr": "12/11/2026",
                  "status": "Executada",
                  "orderType": "Normal",
                  "isIncomplete": false
                },
                {
                  "ticker": "",
                  "specificationTitle": "ALLOS ON NM",
                  "side": "COMPRA",
                  "marketSegment": "FRACIONARIO",
                  "isDayTrade": false,
                  "noteNumber": "1656",
                  "quantity": 3.0,
                  "unitPrice": 27.00,
                  "totalValue": 81.00,
                  "dateBr": "12/11/2026",
                  "status": "Executada",
                  "orderType": "Normal",
                  "isIncomplete": false
                },
                {
                  "ticker": "",
                  "specificationTitle": "FII DEVANT CI ER",
                  "side": "VENDA",
                  "marketSegment": "VISTA",
                  "isDayTrade": true,
                  "noteNumber": "2104",
                  "quantity": 10.0,
                  "unitPrice": 24.50,
                  "totalValue": 245.00,
                  "dateBr": "16/11/2026",
                  "status": "Executada",
                  "orderType": "Day Trade",
                  "isIncomplete": false
                }
              ]
            }
        """.trimIndent()

        val b3Parsed = com.example.domain.model.OrderScreenshotJsonParser.parse(b3NoteJson)
        org.junit.Assert.assertEquals("NOTA_B3", b3Parsed.documentKind)
        org.junit.Assert.assertTrue(b3Parsed.isBrokerageNote)
        org.junit.Assert.assertEquals(2, b3Parsed.notesCount)
        org.junit.Assert.assertEquals(4.82, b3Parsed.totalFeesBrl, 0.001)
        org.junit.Assert.assertEquals(2, b3Parsed.orders.size)

        val alos = b3Parsed.orders.first { it.ticker == "ALOS3" }
        org.junit.Assert.assertEquals(5.0, alos.quantity, 0.001)
        org.junit.Assert.assertEquals(26.96, alos.unitPrice, 0.001)
        org.junit.Assert.assertEquals(134.80, alos.computedTotalValue, 0.01)

        val bmfJson = """
            {
              "documentKind": "NOTA_BMF",
              "brokerHeader": "GENIAL CCTVM S/A",
              "notesCount": 1,
              "totalFeesBrl": 1.19,
              "totalNetSettlementBrl": 18.81,
              "warnings": [],
              "orders": [
                {
                  "ticker": "WIN Z26",
                  "specificationTitle": "WIN Z26 16/12/2026",
                  "side": "VENDA",
                  "marketSegment": "BMF_FUTUROS",
                  "isDayTrade": true,
                  "noteNumber": "534005",
                  "quantity": 2.0,
                  "unitPrice": 167625.0,
                  "totalValue": 49.60,
                  "dateBr": "03/11/2026",
                  "status": "Executada",
                  "orderType": "Day Trade",
                  "isIncomplete": false
                }
              ]
            }
        """.trimIndent()

        val bmfParsed = com.example.domain.model.OrderScreenshotJsonParser.parse(bmfJson)
        org.junit.Assert.assertEquals("NOTA_BMF", bmfParsed.documentKind)
        val win = bmfParsed.orders.first()
        org.junit.Assert.assertEquals("WINZ26", win.ticker)
        org.junit.Assert.assertTrue(win.isFuturesContract)
        org.junit.Assert.assertEquals(167625.0, win.unitPrice, 0.01)
        org.junit.Assert.assertEquals(49.60, win.computedTotalValue, 0.01)
    }

    @Test
    fun `dividend management helper persists CRUD, renames dividend-only ticker, prevents duplicate auto-sync, and restores deleted records with PM recalc`() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.InvestDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val helper = com.example.data.repository.DividendManagementHelper(
                portfolioDao = db.portfolioDao(),
                dividendEventDao = db.dividendEventDao(),
                onMirrorRealPositions = {},
                onMirrorRealOperation = {}
            )

            // 1. Cadastra provento de ativo sem posição na carteira (apenas dividendo)
            val divId = helper.saveDividendEvent(
                id = null,
                ticker = "ERRADO11",
                eventType = com.example.domain.model.DividendEventType.RENDIMENTO_FII,
                status = com.example.domain.model.DividendStatus.RECEIVED,
                exDate = "10/11/2026",
                paymentDate = "17/11/2026",
                unitAmount = 0.90,
                quantityReference = 10.0,
                totalAmount = 9.00,
                source = "Manual",
                notes = "Teste",
                isRecurring = true
            )
            org.junit.Assert.assertTrue(divId > 0L)

            // 2. Renomeia o ticker do ativo que só tem dividendo
            val renamedCount = helper.renameDividendTicker("ERRADO11", "MXRF11")
            org.junit.Assert.assertTrue(renamedCount >= 1)
            val reloaded = db.dividendEventDao().getById(divId)
            org.junit.Assert.assertEquals("MXRF11", reloaded?.assetTicker)

            // 3. Soft-delete + restauração via log de auditoria
            org.junit.Assert.assertTrue(helper.softDeleteDividendEvent(divId))
            org.junit.Assert.assertTrue(db.dividendEventDao().getById(divId)?.isDeleted == true)
            val deleteLog = db.dividendEventDao().observeAuditLogs(20).first()
                .first { it.entityType == "DIVIDEND_EVENT" && it.actionType == "DELETE" }
            val restoreMsg = helper.restoreFromAuditLog(deleteLog.id)
            org.junit.Assert.assertTrue(restoreMsg.contains("restaurado", ignoreCase = true))
            org.junit.Assert.assertTrue(db.dividendEventDao().getById(divId)?.isDeleted == false)

            // 4. Auto-sync cria CONFIRMADO e não duplica na segunda execução
            val pos = com.example.domain.model.PortfolioItemView(
                id = 1L,
                portfolioType = com.example.domain.model.PortfolioType.REAL,
                ticker = "EGAF11",
                name = "Ecoagro",
                assetClass = com.example.domain.model.AssetClass.FIAGRO,
                quantity = 25.0,
                averagePrice = 80.0,
                currentPrice = 71.0,
                sector = "Fiagro",
                dataComDay = 10,
                dataSourceLabel = "Carteira",
                isDemo = false,
                hasMarketPrice = true,
                isCyclical = true
            )
            val quote = com.example.domain.model.LiveMarketQuote(
                ticker = "EGAF11",
                companyName = "Ecoagro",
                price = 71.0,
                changePercent1d = 0.0,
                lastDividendBrl = 0.82,
                dataComDay = 10,
                paymentDay = 17,
                primarySourceLabel = "B3 Oficial",
                consultedSources = listOf("B3 Oficial"),
                marketTimestampLabel = "17/11/2026",
                b3PageUrl = "",
                advfnPageUrl = "",
                fetchedAtMillis = 1_763_000_000_000L
            )
            val firstSync = helper.syncConfirmedDividendsFromQuotes(listOf(pos), mapOf("EGAF11" to quote))
            org.junit.Assert.assertEquals(1, firstSync.createdCount)
            val secondSync = helper.syncConfirmedDividendsFromQuotes(listOf(pos), mapOf("EGAF11" to quote))
            org.junit.Assert.assertEquals(0, secondSync.createdCount)

            // 5. Edição de operação COMPRA recalcula preço médio da posição
            val posId = db.portfolioDao().insertPosition(
                com.example.data.local.PortfolioPositionEntity(
                    portfolioType = com.example.domain.model.PortfolioType.REAL.name,
                    ticker = "ITUB4",
                    name = "Itaú",
                    assetClass = com.example.domain.model.AssetClass.ACOES_BR.name,
                    quantity = 10.0,
                    averagePrice = 30.0,
                    currentPrice = 35.0,
                    hasMarketPrice = true,
                    sector = "Bancos",
                    dataComDay = 30,
                    dataSourceLabel = "Manual"
                )
            )
            val opId = db.portfolioDao().insertOperation(
                com.example.data.local.PortfolioOperationEntity(
                    portfolioType = com.example.domain.model.PortfolioType.REAL.name,
                    ticker = "ITUB4",
                    operationType = com.example.domain.model.OperationType.COMPRA.name,
                    quantity = 10.0,
                    unitPrice = 30.0,
                    totalValue = 300.0,
                    dateIso = "10/11/2026",
                    notes = "Compra inicial"
                )
            )
            helper.updateOperationWithRecalc(
                operationId = opId,
                ticker = "ITUB4",
                operationType = com.example.domain.model.OperationType.COMPRA,
                quantity = 20.0,
                unitPrice = 32.50,
                dateIso = "10/11/2026",
                notes = "Compra editada"
            )
            val updatedPos = db.portfolioDao().getPositionById(posId)
            org.junit.Assert.assertEquals(20.0, updatedPos?.quantity ?: 0.0, 0.001)
            org.junit.Assert.assertEquals(32.50, updatedPos?.averagePrice ?: 0.0, 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun `DividendCalendarScreen displays Historico de Proventos KPIs, 3-series chart, and allows editing dividend-only ticker`() {
        var renamedPair: Pair<String, String>? = null
        val divOnlyEvent = com.example.data.local.DividendEventEntity(
            id = 77L,
            assetTicker = "ONLYDIV11",
            eventType = com.example.domain.model.DividendEventType.DIVIDENDO.name,
            status = com.example.domain.model.DividendStatus.RECEIVED.name,
            exDate = "05/11/2026",
            paymentDate = "15/11/2026",
            unitAmount = 1.20,
            quantityReference = 10.0,
            totalAmount = 12.00,
            source = "Manual"
        )
        val state = com.example.ui.viewmodel.InvestDashboardState(
            dividendEvents = listOf(divOnlyEvent)
        )

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.DividendCalendarScreen(
                    state = state,
                    onRenameDividendTicker = { oldT, newT -> renamedPair = oldT to newT }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dividend_history_header").assertIsDisplayed()
        composeRule.onNodeWithTag("dividend_calendar_lazy_column")
            .performScrollToNode(hasTestTag("edit_dividend_only_ticker_ONLYDIV11"))
        composeRule.onNodeWithTag("edit_dividend_only_ticker_ONLYDIV11").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rename_dividend_ticker_input").performTextReplacement("KNSC11")
        composeRule.onNodeWithTag("rename_dividend_ticker_confirm").performClick()
        composeRule.waitForIdle()

        org.junit.Assert.assertEquals("ONLYDIV11" to "KNSC11", renamedPair)
    }

    @Test
    fun `PortfolioScreen opens EditOperationDialog when clicking edit on an operation`() {
        val op = com.example.data.local.PortfolioOperationEntity(
            id = 55L,
            portfolioType = com.example.domain.model.PortfolioType.REAL.name,
            ticker = "VALE3",
            operationType = com.example.domain.model.OperationType.COMPRA.name,
            quantity = 10.0,
            unitPrice = 60.0,
            totalValue = 600.0,
            dateIso = "10/11/2026",
            notes = "Compra teste"
        )
        val state = com.example.ui.viewmodel.InvestDashboardState(
            operations = listOf(op)
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
                    onAddManualPosition = { _, _, _, _, _, _, _, _, _, _ -> },
                    onRecordOperation = { _, _, _, _, _, _ -> },
                    onDeletePosition = {},
                    onUpdateCash = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("portfolio_lazy_column")
            .performScrollToNode(hasTestTag("edit_operation_button_55"))
        composeRule.onNodeWithTag("edit_operation_button_55").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_operation_ticker_input").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_operation_save_button").assertIsDisplayed()
    }

    @Test
    fun `PortfolioScreen redesigned Dark Premium asset card routes ticker and all 6 financial indicators independently`() {
        val egaf = com.example.domain.model.PortfolioItemView(
            id = 88L,
            portfolioType = com.example.domain.model.PortfolioType.REAL,
            ticker = "EGAF11",
            name = "Ecoagro I Fiagro",
            assetClass = com.example.domain.model.AssetClass.FIAGRO,
            quantity = 25.0,
            averagePrice = 80.38,
            currentPrice = 70.99,
            sector = "Fiagro",
            dataComDay = 5,
            dataSourceLabel = "Manual",
            isDemo = false,
            hasMarketPrice = true,
            isCyclical = true
        )
        val divEvent = com.example.data.local.DividendEventEntity(
            id = 12L,
            assetTicker = "EGAF11",
            eventType = com.example.domain.model.DividendEventType.DIVIDENDO.name,
            status = com.example.domain.model.DividendStatus.RECEIVED.name,
            exDate = "05/11/2026",
            paymentDate = "15/11/2026",
            unitAmount = 0.82,
            quantityReference = 25.0,
            totalAmount = 20.50,
            source = "Manual"
        )
        val state = com.example.ui.viewmodel.InvestDashboardState(
            realPositions = listOf(egaf),
            dividendEvents = listOf(divEvent),
            realHhi = com.example.domain.model.HhiRiskReport.calculate(listOf(egaf), 0.0)
        )

        val openedTabs = mutableListOf<Pair<String, com.example.domain.model.AssetDetailTab>>()

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
                    onAddManualPosition = { _, _, _, _, _, _, _, _, _, _ -> },
                    onRecordOperation = { _, _, _, _, _, _ -> },
                    onDeletePosition = {},
                    onUpdateCash = { _, _ -> },
                    onOpenAssetDetail = { tkr, tab -> openedTabs.add(tkr to tab) }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("portfolio_lazy_column")
            .performScrollToNode(hasTestTag("premium_asset_card_EGAF11"))
        composeRule.onNodeWithTag("position_group_badge_EGAF11").assertIsDisplayed()

        composeRule.onNodeWithTag("asset_card_ticker_EGAF11").performClick()
        composeRule.onNodeWithTag("asset_metric_price_EGAF11").performClick()
        composeRule.onNodeWithTag("asset_metric_dividends_EGAF11").performClick()
        composeRule.onNodeWithTag("asset_metric_div_date_EGAF11").performClick()
        composeRule.onNodeWithTag("asset_metric_quantity_EGAF11").performClick()
        composeRule.onNodeWithTag("asset_metric_avg_price_EGAF11").performClick()
        composeRule.onNodeWithTag("asset_metric_current_value_EGAF11").performClick()

        org.junit.Assert.assertEquals(
            listOf(
                "EGAF11" to com.example.domain.model.AssetDetailTab.ORDERS,
                "EGAF11" to com.example.domain.model.AssetDetailTab.QUOTES,
                "EGAF11" to com.example.domain.model.AssetDetailTab.DIVIDENDS,
                "EGAF11" to com.example.domain.model.AssetDetailTab.DIVIDEND_CALENDAR,
                "EGAF11" to com.example.domain.model.AssetDetailTab.POSITION_MOVEMENTS,
                "EGAF11" to com.example.domain.model.AssetDetailTab.AVERAGE_PRICE,
                "EGAF11" to com.example.domain.model.AssetDetailTab.EQUITY_EVOLUTION
            ),
            openedTabs
        )
    }

    @Test
    fun `AssetDetailScreen switches across all 7 tabs and separates Average Price from Current Price`() {
        val egaf = com.example.domain.model.PortfolioItemView(
            id = 88L,
            portfolioType = com.example.domain.model.PortfolioType.REAL,
            ticker = "EGAF11",
            name = "Ecoagro I Fiagro",
            assetClass = com.example.domain.model.AssetClass.FIAGRO,
            quantity = 25.0,
            averagePrice = 80.38,
            currentPrice = 70.99,
            sector = "Fiagro",
            dataComDay = 5,
            dataSourceLabel = "Manual",
            isDemo = false,
            hasMarketPrice = true,
            isCyclical = true
        )
        val op = com.example.data.local.PortfolioOperationEntity(
            id = 91L,
            portfolioType = com.example.domain.model.PortfolioType.REAL.name,
            ticker = "EGAF11",
            operationType = com.example.domain.model.OperationType.COMPRA.name,
            quantity = 25.0,
            unitPrice = 80.38,
            totalValue = 2009.50,
            dateIso = "10/10/2026",
            notes = "Taxa B3 R$ 0,50"
        )
        val state = com.example.ui.viewmodel.InvestDashboardState(
            realPositions = listOf(egaf),
            operations = listOf(op),
            realHhi = com.example.domain.model.HhiRiskReport.calculate(listOf(egaf), 0.0)
        )

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.AssetDetailScreen(
                    ticker = "EGAF11",
                    initialTab = com.example.domain.model.AssetDetailTab.ORDERS,
                    state = state
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("asset_detail_screen_EGAF11").assertIsDisplayed()
        composeRule.onNodeWithText("Histórico Completo de Ordens").assertIsDisplayed()

        // Aba Preço Médio separa claramente PM e Cotação Atual
        composeRule.onNodeWithTag("asset_detail_tab_AVERAGE_PRICE").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Composição Detalhada do Preço Médio").assertIsDisplayed()

        // Aba Patrimônio
        composeRule.onNodeWithTag("asset_detail_tab_EQUITY_EVOLUTION").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Evolução Patrimonial • EGAF11").assertIsDisplayed()
    }

    @Test
    fun `PortfolioScreen hides G1-G4 badge for unclassified asset and shows ticker clearly on top`() {
        val unclassifiedStock = com.example.domain.model.PortfolioItemView(
            id = 95L,
            portfolioType = com.example.domain.model.PortfolioType.REAL,
            ticker = "BBAS3",
            name = "Banco do Brasil",
            assetClass = com.example.domain.model.AssetClass.ACOES_BR,
            quantity = 50.0,
            averagePrice = 27.65,
            currentPrice = 28.44,
            sector = "Bancos",
            dataComDay = null,
            dataSourceLabel = "Manual",
            isDemo = false,
            hasMarketPrice = true,
            isCyclical = false
        )
        val state = com.example.ui.viewmodel.InvestDashboardState(
            realPositions = listOf(unclassifiedStock),
            realHhi = com.example.domain.model.HhiRiskReport.calculate(listOf(unclassifiedStock), 0.0)
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
                    onAddManualPosition = { _, _, _, _, _, _, _, _, _, _ -> },
                    onRecordOperation = { _, _, _, _, _, _ -> },
                    onDeletePosition = {},
                    onUpdateCash = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("portfolio_lazy_column")
            .performScrollToNode(hasTestTag("premium_asset_card_BBAS3"))
        composeRule.onNodeWithTag("asset_card_ticker_BBAS3").assertIsDisplayed()
        composeRule.onNodeWithTag("position_group_badge_BBAS3").assertDoesNotExist()
    }

    @Test
    fun `OrderScreenshotRecognizer loadDocumentFromUri reads content URI once and opens BOM PDF and HEIC image`() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val recognizer = com.example.data.repository.OrderScreenshotRecognizer(context)

        // 1) PDF com BOM e URI opaca sem extensão .pdf (semelhante a Samsung My Files / Downloads)
        val pdfUri = android.net.Uri.parse("content://com.example.test.docs/document/9901")
        val pdfBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "%PDF-1.5\nNota de Corretagem SINACOR\n%%EOF".toByteArray(Charsets.US_ASCII)
        org.robolectric.Shadows.shadowOf(context.contentResolver)
            .registerInputStream(pdfUri, java.io.ByteArrayInputStream(pdfBytes))

        val loadedPdf = recognizer.loadDocumentFromUri(pdfUri)
        org.junit.Assert.assertTrue(loadedPdf.isPdf)
        org.junit.Assert.assertEquals("application/pdf", loadedPdf.mimeType)
        org.junit.Assert.assertEquals(pdfBytes.size, loadedPdf.bytes.size)

        // 2) Imagem HEIC via content URI sem extensão
        val heicUri = android.net.Uri.parse("content://com.example.test.docs/media/8802")
        val heicBytes = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(),
            0x00, 0x00, 0x00, 0x00
        )
        org.robolectric.Shadows.shadowOf(context.contentResolver)
            .registerInputStream(heicUri, java.io.ByteArrayInputStream(heicBytes))

        val loadedImg = recognizer.loadDocumentFromUri(heicUri)
        org.junit.Assert.assertEquals(com.example.data.repository.BrokerageDocumentKind.IMAGE, loadedImg.kind)
        org.junit.Assert.assertEquals("image/heic", loadedImg.mimeType)
    }

    @Test
    fun `OrderScreenshotReviewDialog renders AppCheckDebugRecoveryPanel and retry same file on App Check error`() {
        var retriedSameFile = false
        val errorState = com.example.domain.model.OrderScreenshotRecognitionState.Error(
            message = "Firebase App Check token is invalid.",
            lastImageUriString = "content://com.example.test/nota.pdf"
        )

        composeRule.setContent {
            InvestAiTheme {
                com.example.ui.screens.OrderScreenshotReviewDialog(
                    state = errorState,
                    targetPortfolioType = com.example.domain.model.PortfolioType.REAL,
                    onDismiss = {},
                    onPickAnotherImage = {},
                    onConfirmLaunch = { _, _ -> },
                    onRetryLastFile = { retriedSameFile = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("appcheck_debug_recovery_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("appcheck_debug_token_input").assertIsDisplayed()
        composeRule.onNodeWithTag("appcheck_copy_token_button").assertIsDisplayed()
        composeRule.onNodeWithTag("appcheck_validate_token_button").assertIsDisplayed()
        composeRule.onNodeWithTag("retry_same_order_file_button").performClick()
        org.junit.Assert.assertTrue(retriedSameFile)
    }
}




