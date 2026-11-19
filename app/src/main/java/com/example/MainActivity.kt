package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.configureAppCheck
import com.example.data.repository.FirestoreSyncRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.components.FintechCard
import com.example.ui.navigation.InvestAppRoot
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.InvestAiTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CloudSyncViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SAFE_OFFLINE = "extra_safe_offline_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)

        val previousCrashTrace = CrashReporter.consumeLastCrash(this)
        if (previousCrashTrace != null) {
            showNativeCrashDiagnosticView(previousCrashTrace)
            return
        }

        val safeOfflineMode = intent?.getBooleanExtra(EXTRA_SAFE_OFFLINE, false) == true

        try {
            if (!safeOfflineMode) {
                runCatching { configureAppCheck(this, intent) }
            }
            runCatching {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                )
            }

            setContent {
                InvestAiTheme {
                    if (safeOfflineMode) {
                        InvestAppRoot(
                            userDisplayName = "Modo Local Seguro",
                            cloudSyncMessage = "Modo Local Seguro ativado sem serviços de nuvem."
                        )
                    } else {
                        InvestAuthGate()
                    }
                }
            }
        } catch (t: Throwable) {
            val formatted = CrashReporter.recordCrash(this, t)
            showNativeCrashDiagnosticView(formatted)
        }
    }

    private fun showNativeCrashDiagnosticView(crashTrace: String) {
        fun dp(value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#070B12"))
            fitsSystemWindows = true
            setPadding(dp(20), dp(24), dp(20), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val titleView = TextView(this).apply {
            text = "Diagnóstico de Falha — INVEST AI"
            setTextColor(Color.parseColor("#F05252"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(Typeface.DEFAULT_BOLD)
        }

        val subtitleView = TextView(this).apply {
            text = "O aplicativo detectou um fechamento inesperado na abertura anterior. Copie o detalhe abaixo ou abra em Modo Local Seguro:"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(8), 0, dp(12))
        }

        val traceScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#141E33"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                bottomMargin = dp(14)
            }
        }

        val traceText = TextView(this).apply {
            text = crashTrace
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        traceScroll.addView(traceText)

        val copyButton = Button(this).apply {
            text = "Copiar Diagnóstico"
            contentDescription = "Copiar texto do erro para a área de transferência"
            minHeight = dp(48)
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("INVEST AI Crash", crashTrace))
                Toast.makeText(this@MainActivity, "Diagnóstico copiado!", Toast.LENGTH_SHORT).show()
            }
        }

        val offlineButton = Button(this).apply {
            text = "Abrir em Modo Local Seguro"
            contentDescription = "Abrir o aplicativo localmente sem serviços externos"
            minHeight = dp(48)
            setOnClickListener {
                val next = Intent(this@MainActivity, MainActivity::class.java).apply {
                    putExtra(EXTRA_SAFE_OFFLINE, true)
                }
                startActivity(next)
                finish()
            }
        }

        val retryButton = Button(this).apply {
            text = "Tentar Abrir Normalmente"
            contentDescription = "Tentar iniciar o aplicativo normalmente"
            minHeight = dp(48)
            setOnClickListener {
                val next = Intent(this@MainActivity, MainActivity::class.java)
                startActivity(next)
                finish()
            }
        }

        root.addView(titleView)
        root.addView(subtitleView)
        root.addView(traceScroll)
        root.addView(copyButton)
        root.addView(offlineButton)
        root.addView(retryButton)
        setContentView(root)
    }
}

@Composable
fun InvestAuthGate(authOverride: FirebaseAuth? = null) {
    val context = LocalContext.current
    val resolvedAuth = remember(authOverride, context) {
        if (authOverride != null) {
            authOverride
        } else {
            runCatching {
                val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
                if (app != null) Firebase.auth else null
            }.onFailure { err ->
                Log.e("MainActivity", "FirebaseAuth indisponível na inicialização", err)
            }.getOrNull()
        }
    }

    if (resolvedAuth == null) {
        FirebaseMissingRecoveryGate()
        return
    }

    val currentUser by resolvedAuth.authStateFlow().collectAsStateWithLifecycle(initialValue = resolvedAuth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen()
    } else {
        key(user.uid) {
            val scope = rememberCoroutineScope()
            val credentialManager = remember(context) {
                runCatching { CredentialManager.create(context) }.getOrNull()
            }

            val cloudSyncViewModel: CloudSyncViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val repoResult = runCatching {
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(databaseId)
                            FirestoreSyncRepository(db)
                        }
                        CloudSyncViewModel(
                            repository = repoResult.getOrNull(),
                            currentUserId = user.uid,
                            initError = repoResult.exceptionOrNull()?.localizedMessage
                        )
                    }
                }
            )
            val cloudReportsUiState by cloudSyncViewModel.cloudReports.collectAsStateWithLifecycle()
            val isCloudSyncing by cloudSyncViewModel.isSyncing.collectAsStateWithLifecycle()
            val cloudSyncMessage by cloudSyncViewModel.syncMessage.collectAsStateWithLifecycle()

            InvestAppRoot(
                userDisplayName = user.displayName ?: user.email ?: "Investidor",
                onSignOut = { signOutUser(credentialManager, scope) },
                cloudReportsUiState = cloudReportsUiState,
                isCloudSyncing = isCloudSyncing,
                cloudSyncMessage = cloudSyncMessage,
                onClearCloudSyncMessage = cloudSyncViewModel::clearSyncMessage,
                onSyncConsolidatedReport = cloudSyncViewModel::syncConsolidatedReportToCloud
            )
        }
    }
}

@Composable
private fun FirebaseMissingRecoveryGate() {
    var continueOffline by rememberSaveable { mutableStateOf(false) }
    if (continueOffline) {
        InvestAppRoot(
            userDisplayName = "Modo Local (sem Firebase)",
            cloudSyncMessage = "Firebase não configurado neste APK (google-services.json ausente no build)."
        )
        return
    }
    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("INVEST AI", style = MaterialTheme.typography.headlineMedium, color = EmeraldPrimary)
                FintechCard(borderColor = AmberGold) {
                    Text(
                        "Configuração Firebase ausente neste APK",
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberGold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Este APK foi compilado no repositório sem a configuração do Firebase (google-services.json). " +
                            "Por isso o Login Google, o Firestore e o Gemini ao vivo estão desativados neste pacote.\n\n" +
                            "Para ativá-los no próximo build do GitHub Actions, adicione o secret GOOGLE_SERVICES_JSON nas configurações do repositório.\n\n" +
                            "Enquanto isso, você pode usar toda a análise local, importação CSV/XLSX e cálculos HHI normalmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Button(
                    onClick = { continueOffline = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("open_local_mode_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = EmeraldOnPrimary
                    )
                ) {
                    Text("Abrir em Modo Local")
                }
            }
        }
    }
}
