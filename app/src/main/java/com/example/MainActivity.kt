package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.UiOneShotEvent
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ModelsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.TranscriptScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.VaultScribeTheme

object VaultRoutes {
    const val STUDIO = "studio"
    const val TRANSCRIPT = "transcript"
    const val MODELS = "models"
    const val VAULT = "vault"
}

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val vaultTabs = listOf(
    NavTab(VaultRoutes.STUDIO, R.string.tab_studio, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    NavTab(VaultRoutes.TRANSCRIPT, R.string.tab_transcript, Icons.Filled.Subtitles, Icons.Outlined.Subtitles),
    NavTab(VaultRoutes.MODELS, R.string.tab_models, Icons.Filled.Memory, Icons.Outlined.Memory),
    NavTab(VaultRoutes.VAULT, R.string.tab_vault, Icons.Filled.Lock, Icons.Outlined.Lock),
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingAudioIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
            val authSnapshot by viewModel.authSnapshot.collectAsStateWithLifecycle()
            val authError by viewModel.authError.collectAsStateWithLifecycle()
            val isAuthSubmitting by viewModel.isAuthSubmitting.collectAsStateWithLifecycle()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded(isChangingConfigurations)
                        Lifecycle.Event.ON_RESUME -> viewModel.clearExternalPickerSuppression()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            VaultScribeTheme(
                themeMode = settings.themeMode,
                useDynamicColor = settings.useDynamicColor,
            ) {
                Crossfade(targetState = isUnlocked, label = "vault_auth_gate") { unlocked ->
                    if (!unlocked) {
                        LoginScreen(
                            authSnapshot = authSnapshot,
                            errorMessage = authError,
                            isSubmitting = isAuthSubmitting,
                            onRegister = viewModel::registerAccount,
                            onLogin = viewModel::login,
                            onResetAccount = viewModel::resetAccountAndWipeVault,
                            onClearError = viewModel::clearAuthError,
                        )
                    } else {
                        VaultScribeApp(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingAudioIntent(intent)
    }

    private fun handleIncomingAudioIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        uri?.let { viewModel.onAudioFileSelected(it) }
    }
}

@Composable
fun VaultScribeApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val authSnapshot by viewModel.authSnapshot.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val modelProgress by viewModel.modelProgress.collectAsStateWithLifecycle()
    val historyJobs by viewModel.historyJobs.collectAsStateWithLifecycle()
    val loadedAudio by viewModel.loadedAudio.collectAsStateWithLifecycle()
    val pipelinePhase by viewModel.pipelinePhase.collectAsStateWithLifecycle()
    val activeJob by viewModel.activeJob.collectAsStateWithLifecycle()
    val selectedExportFormat by viewModel.selectedExportFormat.collectAsStateWithLifecycle()
    val isDragOver by viewModel.isDragOver.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: VaultRoutes.STUDIO

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiOneShotEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                UiOneShotEvent.NavigateToTranscript -> navigateToTab(VaultRoutes.TRANSCRIPT)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp && keyEvent.isCtrlPressed) {
                    when (keyEvent.key) {
                        Key.One -> { navigateToTab(VaultRoutes.STUDIO); true }
                        Key.Two -> { navigateToTab(VaultRoutes.TRANSCRIPT); true }
                        Key.Three -> { navigateToTab(VaultRoutes.MODELS); true }
                        Key.Four -> { navigateToTab(VaultRoutes.VAULT); true }
                        Key.L -> { viewModel.lockVault(); true }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        vaultTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label,
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${tab.route}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        vaultTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label,
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("rail_${tab.route}"),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = VaultRoutes.STUDIO,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                ) {
                    composable(VaultRoutes.STUDIO) {
                        StudioScreen(
                            settings = settings,
                            models = models,
                            loadedAudio = loadedAudio,
                            pipelinePhase = pipelinePhase,
                            playbackState = playbackState,
                            isDragOver = isDragOver,
                            isSystemAsrAvailable = viewModel.isSystemAsrAvailable,
                            onSetDragOver = viewModel::setDragOver,
                            onAudioUriReceived = viewModel::onAudioFileSelected,
                            onClearAudio = viewModel::clearLoadedAudio,
                            onSelectLanguage = viewModel::selectLanguage,
                            onSetCustomBcp47 = viewModel::setCustomBcp47Tag,
                            onSelectEngineMode = viewModel::selectEngineMode,
                            onStartTranscription = viewModel::startTranscription,
                            onCancelTranscription = viewModel::cancelTranscription,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSeekTo = viewModel::seekTo,
                            onOpenModelsTab = { navigateToTab(VaultRoutes.MODELS) },
                            onOpenTranscriptTab = { navigateToTab(VaultRoutes.TRANSCRIPT) },
                            onPrepareExternalPicker = viewModel::suppressAutoLockForExternalPicker,
                        )
                    }
                    composable(VaultRoutes.TRANSCRIPT) {
                        TranscriptScreen(
                            activeJob = activeJob,
                            selectedExportFormat = selectedExportFormat,
                            playbackState = playbackState,
                            buildExportContent = viewModel::buildExportContent,
                            suggestedFileName = viewModel::suggestedExportFileName,
                            onSelectExportFormat = viewModel::selectExportFormat,
                            onSaveExportUri = viewModel::saveExportToUri,
                            onCreateShareUri = viewModel::createShareFileUri,
                            onUpdateCueText = viewModel::updateCueText,
                            onShiftCueTiming = viewModel::shiftCueTiming,
                            onDeleteCue = viewModel::deleteCue,
                            onSeekAudio = viewModel::seekTo,
                            onShowMessage = viewModel::showMessage,
                            onGoToStudio = { navigateToTab(VaultRoutes.STUDIO) },
                            onPrepareExternalPicker = viewModel::suppressAutoLockForExternalPicker,
                        )
                    }
                    composable(VaultRoutes.MODELS) {
                        ModelsScreen(
                            models = models,
                            modelProgress = modelProgress,
                            isSystemAsrAvailable = viewModel.isSystemAsrAvailable,
                            onDownloadModel = viewModel::downloadModel,
                            onDeleteModel = viewModel::deleteModel,
                            onImportLocalZip = viewModel::importLocalModelZip,
                            onPrepareExternalPicker = viewModel::suppressAutoLockForExternalPicker,
                        )
                    }
                    composable(VaultRoutes.VAULT) {
                        VaultScreen(
                            historyJobs = historyJobs,
                            settings = settings,
                            authSnapshot = authSnapshot,
                            onOpenJob = viewModel::openHistoryJob,
                            onDeleteJob = viewModel::deleteHistoryJob,
                            onWipeAll = viewModel::wipeAllHistory,
                            onLockVaultNow = viewModel::lockVault,
                            onAutoLockChange = viewModel::setAutoLockOnBackground,
                            onUpdateCredentials = viewModel::updateCredentials,
                            onThemeModeChange = viewModel::setThemeMode,
                            onDynamicColorChange = viewModel::setUseDynamicColor,
                            onIncludeTimestampsTxtChange = viewModel::setIncludeTimestampsInTxt,
                            onMaxCueCharsChange = viewModel::setMaxCueChars,
                        )
                    }
                }
            }
        }
    }
}
