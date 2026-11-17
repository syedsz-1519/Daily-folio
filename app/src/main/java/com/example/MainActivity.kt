package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.EntitlementRepository
import com.example.data.firebase.JournalRepository
import com.example.data.firebase.SocialSnapshotRepository
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.signOutUser
import com.example.data.firebase.toDiagnostics
import com.example.data.model.JournalEntry
import com.example.data.model.SharedPostDraft
import com.example.data.model.UiState
import com.example.data.model.UserEntitlement
import com.example.data.monetization.AdMobBannerSlot
import com.example.data.monetization.AdMobController
import com.example.data.security.VaultPreferencesRepository
import com.example.data.social.ShareIntentParser
import com.example.data.social.YouTubeRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.JournalEditorSheet
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.PremiumUpgradeSheet
import com.example.ui.screens.SettingsAndAuthLabScreen
import com.example.ui.screens.SocialPulseScreen
import com.example.ui.screens.VaultLockScreen
import com.example.ui.theme.FolioNoteTheme
import com.example.ui.viewmodel.JournalViewModel
import com.example.ui.viewmodel.MonetizationViewModel
import com.example.ui.viewmodel.SocialViewModel
import com.example.ui.viewmodel.VaultViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data object JournalRoute

@Serializable
data object SocialPulseRoute

@Serializable
data object SettingsRoute

class MainActivity : FragmentActivity() {

    private val incomingShareDraft = MutableStateFlow<SharedPostDraft?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdMobController.ensureInitialized(this)
        ShareIntentParser.parse(intent)?.let { incomingShareDraft.value = it }

        setContent {
            FolioNoteTheme {
                val pendingDraft by incomingShareDraft.asStateFlow().collectAsStateWithLifecycle()
                FolioNoteApp(
                    incomingShareDraft = pendingDraft,
                    onConsumeShareDraft = { incomingShareDraft.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShareIntentParser.parse(intent)?.let { incomingShareDraft.value = it }
    }
}

@Composable
fun FolioNoteApp(
    incomingShareDraft: SharedPostDraft?,
    onConsumeShareDraft: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedFolioWorkspace(
                currentUserId = user.uid,
                incomingShareDraft = incomingShareDraft,
                onConsumeShareDraft = onConsumeShareDraft,
                onSignOut = {
                    signOutUser(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedFolioWorkspace(
    currentUserId: String,
    incomingShareDraft: SharedPostDraft?,
    onConsumeShareDraft: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val vaultViewModel: VaultViewModel = viewModel(
        key = "vault_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                VaultViewModel(VaultPreferencesRepository(app))
            }
        }
    )

    val journalViewModel: JournalViewModel = viewModel(
        key = "journal_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                JournalViewModel(JournalRepository(db), currentUserId)
            }
        }
    )

    val socialViewModel: SocialViewModel = viewModel(
        key = "social_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                SocialViewModel(
                    snapshotRepository = SocialSnapshotRepository(db),
                    youtubeRepository = YouTubeRepository(),
                    preferencesRepository = VaultPreferencesRepository(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val monetizationViewModel: MonetizationViewModel = viewModel(
        key = "billing_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                MonetizationViewModel(
                    appContext = app,
                    entitlementRepository = EntitlementRepository(db),
                    vaultPreferencesRepository = VaultPreferencesRepository(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    // Auto-lock on background if configured
    DisposableEffect(lifecycleOwner, vaultViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                vaultViewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val vaultSettings by vaultViewModel.vaultSettings.collectAsStateWithLifecycle()
    val isLocked by vaultViewModel.isLocked.collectAsStateWithLifecycle()
    val pinError by vaultViewModel.pinError.collectAsStateWithLifecycle()

    if (isLocked && vaultSettings.hasPinConfigured) {
        VaultLockScreen(
            biometricEnabled = vaultSettings.biometricEnabled,
            pinError = pinError,
            onVerifyPin = { pin -> vaultViewModel.unlockWithPin(pin) },
            onBiometricSuccess = { vaultViewModel.unlockViaBiometric() },
            onClearError = { vaultViewModel.clearPinError() }
        )
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val entriesState by journalViewModel.filteredEntriesState.collectAsStateWithLifecycle()
    val searchQuery by journalViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedMood by journalViewModel.selectedMoodFilter.collectAsStateWithLifecycle()
    val isSaving by journalViewModel.isSaving.collectAsStateWithLifecycle()
    val journalFeedback by journalViewModel.feedbackMessage.collectAsStateWithLifecycle()
    val pendingSharedDraft by journalViewModel.pendingSharedDraft.collectAsStateWithLifecycle()

    val usageReport by socialViewModel.usageReport.collectAsStateWithLifecycle()
    val isLoadingUsage by socialViewModel.isLoadingUsage.collectAsStateWithLifecycle()
    val youtubeQuery by socialViewModel.youtubeQuery.collectAsStateWithLifecycle()
    val youtubeState by socialViewModel.youtubeState.collectAsStateWithLifecycle()
    val isLoadingYouTube by socialViewModel.isLoadingYouTube.collectAsStateWithLifecycle()
    val syncedSnapshotsState by socialViewModel.syncedSnapshots.collectAsStateWithLifecycle()
    val socialStatus by socialViewModel.statusMessage.collectAsStateWithLifecycle()

    val entitlementState by monetizationViewModel.entitlementState.collectAsStateWithLifecycle()
    val billingState by monetizationViewModel.billingState.collectAsStateWithLifecycle()
    val adMobStatus by AdMobController.adStatus.collectAsStateWithLifecycle()

    val entitlement = (entitlementState as? UiState.Success<UserEntitlement>)?.data
    val isAdFree = entitlement?.adFree ?: vaultSettings.cachedAdFree

    var showEditorSheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var showUpgradeSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(incomingShareDraft) {
        if (incomingShareDraft != null) {
            journalViewModel.setPendingSharedDraft(incomingShareDraft)
            editingEntry = null
            showEditorSheet = true
            onConsumeShareDraft()
        }
    }

    LaunchedEffect(journalFeedback) {
        val msg = journalFeedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        journalViewModel.clearFeedback()
    }

    LaunchedEffect(socialStatus) {
        val msg = socialStatus ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        socialViewModel.clearStatusMessage()
    }

    val isJournalTab = currentDestination?.hasRoute<JournalRoute>() == true
    val isSocialTab = currentDestination?.hasRoute<SocialPulseRoute>() == true
    val isSettingsTab = currentDestination?.hasRoute<SettingsRoute>() == true

    val navigateToTab: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (isJournalTab) {
                    FloatingActionButton(
                        onClick = {
                            editingEntry = null
                            socialViewModel.refreshUsageStats(context, syncToCloud = false)
                            showEditorSheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("new_entry_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New journal entry"
                        )
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        AdMobBannerSlot(isAdFree = isAdFree)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            NavigationBarItem(
                                selected = isJournalTab,
                                onClick = { navigateToTab(JournalRoute) },
                                icon = {
                                    Icon(
                                        imageVector = if (isJournalTab) Icons.Filled.Book else Icons.Outlined.Book,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(id = R.string.nav_journal)) },
                                modifier = Modifier.testTag("nav_tab_journal")
                            )
                            NavigationBarItem(
                                selected = isSocialTab,
                                onClick = { navigateToTab(SocialPulseRoute) },
                                icon = {
                                    Icon(
                                        imageVector = if (isSocialTab) Icons.Filled.QueryStats else Icons.Outlined.QueryStats,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(id = R.string.nav_social)) },
                                modifier = Modifier.testTag("nav_tab_social")
                            )
                            NavigationBarItem(
                                selected = isSettingsTab,
                                onClick = { navigateToTab(SettingsRoute) },
                                icon = {
                                    Icon(
                                        imageVector = if (isSettingsTab) Icons.Filled.Lock else Icons.Outlined.Lock,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(id = R.string.nav_settings)) },
                                modifier = Modifier.testTag("nav_tab_settings")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationRailItem(
                            selected = isJournalTab,
                            onClick = { navigateToTab(JournalRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (isJournalTab) Icons.Filled.Book else Icons.Outlined.Book,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_journal)) }
                        )
                        NavigationRailItem(
                            selected = isSocialTab,
                            onClick = { navigateToTab(SocialPulseRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (isSocialTab) Icons.Filled.QueryStats else Icons.Outlined.QueryStats,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_social)) }
                        )
                        NavigationRailItem(
                            selected = isSettingsTab,
                            onClick = { navigateToTab(SettingsRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (isSettingsTab) Icons.Filled.Lock else Icons.Outlined.Lock,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_settings)) }
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (useRail && !isAdFree) {
                        AdMobBannerSlot(isAdFree = false)
                    }
                    NavHost(
                        navController = navController,
                        startDestination = JournalRoute,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        composable<JournalRoute> {
                            JournalScreen(
                                entriesState = entriesState,
                                searchQuery = searchQuery,
                                selectedMood = selectedMood,
                                hasPinConfigured = vaultSettings.hasPinConfigured,
                                onSearchQueryChange = journalViewModel::setSearchQuery,
                                onMoodFilterSelect = journalViewModel::setMoodFilter,
                                onOpenNewEntry = {
                                    editingEntry = null
                                    showEditorSheet = true
                                },
                                onEditEntry = { entry ->
                                    editingEntry = entry
                                    showEditorSheet = true
                                },
                                onTogglePinned = journalViewModel::togglePinned,
                                onDeleteEntry = journalViewModel::deleteEntry,
                                onLockVault = vaultViewModel::lockNow
                            )
                        }

                        composable<SocialPulseRoute> {
                            SocialPulseScreen(
                                usageReport = usageReport,
                                isLoadingUsage = isLoadingUsage,
                                youtubeQuery = youtubeQuery,
                                youtubeState = youtubeState,
                                isLoadingYouTube = isLoadingYouTube,
                                syncedSnapshotsState = syncedSnapshotsState,
                                onRefreshUsage = { sync ->
                                    socialViewModel.refreshUsageStats(context, syncToCloud = sync)
                                },
                                onYoutubeQueryChange = socialViewModel::updateYoutubeQuery,
                                onLookupYouTube = { socialViewModel.lookupYouTube() },
                                onSaveYoutubeSnapshot = socialViewModel::saveYouTubeSnapshotToCloud,
                                onJournalWithSocialData = { draft ->
                                    if (draft != null) {
                                        journalViewModel.setPendingSharedDraft(draft)
                                    }
                                    editingEntry = null
                                    showEditorSheet = true
                                }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsAndAuthLabScreen(
                                authDiagnostics = Firebase.auth.currentUser.toDiagnostics(),
                                vaultSettings = vaultSettings,
                                entitlementState = entitlementState,
                                billingState = billingState,
                                adMobStatus = adMobStatus,
                                onSignOutClick = onSignOut,
                                onConfigurePin = { pin, enableBio ->
                                    vaultViewModel.configurePin(pin, enableBio) {}
                                },
                                onToggleVaultLock = vaultViewModel::setVaultLockEnabled,
                                onToggleBiometric = vaultViewModel::setBiometricEnabled,
                                onToggleLockOnBackground = vaultViewModel::setLockOnBackground,
                                onRemovePin = vaultViewModel::removePin,
                                onLockVaultNow = vaultViewModel::lockNow,
                                onTestInterstitialAd = {
                                    AdMobController.showInterstitialIfEligible(
                                        activity = activity,
                                        isAdFree = isAdFree,
                                        force = true
                                    )
                                },
                                onOpenUpgradeSheet = { showUpgradeSheet = true },
                                onRestorePurchases = monetizationViewModel::restorePurchases
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorSheet) {
        val (todaySummary, todayMins) = socialViewModel.buildTodaySummaryForJournal()
        JournalEditorSheet(
            editingEntry = editingEntry,
            pendingSharedDraft = pendingSharedDraft,
            todaySocialSummary = todaySummary,
            todaySocialMinutes = todayMins,
            isSaving = isSaving,
            onDismiss = {
                showEditorSheet = false
                editingEntry = null
                journalViewModel.setPendingSharedDraft(null)
            },
            onSave = { title, content, mood, tagsRaw, pinned, attachSocial ->
                val summaryToSave = if (attachSocial) todaySummary else null
                val minsToSave = if (attachSocial) todayMins else 0L
                val existing = editingEntry
                if (existing == null) {
                    journalViewModel.saveNewEntry(
                        title = title,
                        content = content,
                        mood = mood,
                        tagsRaw = tagsRaw,
                        pinned = pinned,
                        sharedDraft = pendingSharedDraft,
                        socialSnapshotSummary = summaryToSave,
                        totalSocialMinutes = minsToSave
                    ) { success ->
                        if (success) {
                            showEditorSheet = false
                            AdMobController.showInterstitialIfEligible(
                                activity = activity,
                                isAdFree = isAdFree,
                                force = false
                            )
                        }
                    }
                } else {
                    journalViewModel.updateExistingEntry(
                        entryId = existing.id,
                        title = title,
                        content = content,
                        mood = mood,
                        tagsRaw = tagsRaw,
                        pinned = pinned,
                        socialSnapshotSummary = summaryToSave ?: existing.socialSnapshotSummary,
                        totalSocialMinutes = if (attachSocial) minsToSave else existing.totalSocialMinutes
                    ) { success ->
                        if (success) {
                            showEditorSheet = false
                            editingEntry = null
                        }
                    }
                }
            }
        )
    }

    if (showUpgradeSheet) {
        PremiumUpgradeSheet(
            isAdFree = isAdFree,
            billingState = billingState,
            onDismiss = { showUpgradeSheet = false },
            onRefreshBilling = monetizationViewModel::refreshBilling,
            onPurchaseOffer = { act, offer ->
                monetizationViewModel.launchPurchase(act, offer)
            }
        )
    }
}
