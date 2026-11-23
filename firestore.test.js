const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastTs() {
  return new Date(Date.now() - 2000);
}

test("Unauthenticated user cannot read or list consolidated reports", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("consolidated_reports").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("agent_insights").get()
  );
});

test("Authenticated owner can create, read, and list their consolidated reports", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("consolidated_reports")
    .doc("report_1");

  const now = pastTs();
  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      tickerFilter: "ITUB4",
      consensusConclusion: "Comprar",
      averageConfidence: 84,
      coveredAgentsCount: 3,
      analystSummary: "DY e P/VP atrativos.",
      gestorSummary: "HHI sob controle.",
      auditorSummary: "Fontes conferidas.",
      combinedRisks: "Risco macro.",
      totalEquityBrl: 50000,
      hhiScore: 1450,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("consolidated_reports").get()
  );
});

test("Cross-user access to consolidated reports and agent insights is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTs();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("agent_insights")
      .doc("ins_1")
      .set({
        userId: ALICE_UID,
        agentRole: "ANALISTA",
        ticker: "HGLG11",
        conclusion: "Manter",
        confidence: 78,
        thesis: "Galpões logísticos resilientes.",
        usedPersonalData: false,
        createdAt: now,
        updatedAt: now,
      })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("agent_insights").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("agent_insights")
      .doc("ins_2")
      .set({
        userId: ALICE_UID,
        agentRole: "AUDITOR",
        ticker: "HGLG11",
        conclusion: "Manter",
        confidence: 80,
        thesis: "Tentativa de escrita cruzada.",
        usedPersonalData: false,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Shadow update with ghost field and invalid enum are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTs();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("consolidated_reports")
      .doc("ghost_1")
      .set({
        userId: ALICE_UID,
        tickerFilter: "GERAL",
        consensusConclusion: "Comprar",
        averageConfidence: 90,
        coveredAgentsCount: 3,
        totalEquityBrl: 10000,
        hhiScore: 1200,
        isAdminOverride: true,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("agent_insights")
      .doc("bad_role")
      .set({
        userId: ALICE_UID,
        agentRole: "ADMIN",
        ticker: "ITUB4",
        conclusion: "Comprar",
        confidence: 80,
        thesis: "Papel inválido.",
        usedPersonalData: false,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Authenticated owner can create, update, get, list, and delete cloud assets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const assetRef = aliceDb.collection("users").doc(ALICE_UID).collection("assets").doc("PETR4");
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    assetRef.set({
      userId: ALICE_UID,
      ticker: "PETR4",
      name: "Petrobras PN",
      assetClass: "ACOES_BR",
      sector: "Petróleo e Gás",
      quantity: 120.0,
      averagePrice: 35.4,
      currentPrice: 38.9,
      dy12m: 14.2,
      pvp: 1.25,
      pl: 4.8,
      roe: 26.5,
      dailyLiquidityMillions: 1850.0,
      dataComDay: 12,
      cyclical: true,
      demoData: false,
      sourceLabel: "Carteira Real B3",
      dataBaseDate: "2026-11-23",
      riskSummary: "Sensibilidade ao Brent e política de dividendos.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Mirror Kotlin partial update on upsertCloudAsset (preserves userId, ticker, createdAt)
  await assertSucceeds(
    assetRef.update({
      currentPrice: 39.5,
      dy12m: 13.8,
      updatedAt: pastTs(),
    })
  );

  // Mirror Kotlin observeCloudAssets query & getCloudAsset
  await assertSucceeds(assetRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("assets")
      .orderBy("updatedAt", "desc")
      .limit(100)
      .get()
  );

  // Immutable createdAt and ticker mutations must fail
  await assertFails(
    assetRef.update({
      createdAt: pastTs(),
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    assetRef.update({
      ticker: "VALE3",
      updatedAt: pastTs(),
    })
  );

  // Future timestamp tampering must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("assets").doc("VALE3").set({
      userId: ALICE_UID,
      ticker: "VALE3",
      name: "Vale ON",
      assetClass: "ACOES_BR",
      sector: "Mineração",
      quantity: 50,
      averagePrice: 60,
      currentPrice: 62,
      dy12m: 9.5,
      pvp: 1.3,
      pl: 6.2,
      roe: 19.0,
      dailyLiquidityMillions: 1500,
      dataComDay: 0,
      cyclical: false,
      demoData: false,
      sourceLabel: "B3",
      dataBaseDate: "2026-11-23",
      riskSummary: "",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: pastTs(),
    })
  );

  await assertSucceeds(assetRef.delete());
});

test("Unauthenticated and cross-user access to assets and asset_audits is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("assets").doc("HGLG11").set({
      userId: ALICE_UID,
      ticker: "HGLG11",
      name: "CSHG Logística",
      assetClass: "FII",
      sector: "Logística",
      quantity: 15,
      averagePrice: 158.0,
      currentPrice: 162.5,
      dy12m: 8.6,
      pvp: 1.02,
      pl: 0,
      roe: 0,
      dailyLiquidityMillions: 12.4,
      dataComDay: 30,
      cyclical: true,
      demoData: false,
      sourceLabel: "B3",
      dataBaseDate: "2026-11-23",
      riskSummary: "Vacância logística controlada.",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("asset_audits").doc("audit_HGLG11_1").set({
      userId: ALICE_UID,
      assetId: "HGLG11",
      ticker: "HGLG11",
      assetName: "CSHG Logística",
      riskLevel: "BAIXO",
      auditScore: 86,
      verdict: "FAVORAVEL",
      executiveSummary: "Fundo logístico de alta liquidez com P/VP próximo ao valor patrimonial.",
      risksJson: '[{"titulo":"Juros longos","severidade":"MEDIA","detalhe":"Pressão na NTN-B","mitigacao":"Diversificar indexadores"}]',
      opportunitiesJson: '[{"titulo":"Renda recorrente","potencial":"ALTO","detalhe":"DY consistente","catalisador":"Novos contratos atípicos"}]',
      inconsistencyAlertsJson: "[]",
      auditRecommendation: "Acompanhar relatório gerencial mensal na B3.",
      modelName: "gemini-3.8-flash",
      sourceSnapshotLabel: "B3 • 2026-11-23",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Mirror Kotlin observeAssetAudits query and ticker-filtered agent_insights query
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("asset_audits")
      .orderBy("createdAt", "desc")
      .limit(25)
      .get()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("agent_insights")
      .where("ticker", "==", "HGLG11")
      .limit(6)
      .get()
  );

  // Asset audits are immutable (update rejected)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("asset_audits")
      .doc("audit_HGLG11_1")
      .update({ verdict: "EVITAR", updatedAt: pastTs() })
  );

  // Invalid riskLevel enum or @DocumentId `id` field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("asset_audits").doc("audit_bad").set({
      id: "audit_bad",
      userId: ALICE_UID,
      assetId: "HGLG11",
      ticker: "HGLG11",
      assetName: "CSHG Logística",
      riskLevel: "EXTREMO",
      auditScore: 50,
      verdict: "NEUTRO",
      executiveSummary: "Resumo",
      risksJson: "[]",
      opportunitiesJson: "[]",
      inconsistencyAlertsJson: "[]",
      auditRecommendation: "Verificar",
      modelName: "gemini-3.8-flash",
      sourceSnapshotLabel: "B3",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unauthenticated and cross-user checks
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("assets").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("asset_audits").get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("assets").doc("HGLG11").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("asset_audits").get());
});

