package ru.adan.silmaril

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import android.widget.Toast
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.get
import ru.adan.silmaril.misc.OutputItem
import ru.adan.silmaril.model.AndroidProfileManager
import ru.adan.silmaril.model.ConnectionState as ModelConnectionState
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.model.targetName
import ru.adan.silmaril.ui.ConnectionState as UiConnectionState
import ru.adan.silmaril.viewmodel.UnifiedMapsViewModel
import ru.adan.silmaril.platform.AndroidContext
import ru.adan.silmaril.platform.Platform
import ru.adan.silmaril.platform.createLogger
import ru.adan.silmaril.ui.*
import ru.adan.silmaril.view.*
import ru.adan.silmaril.service.MudConnectionService

class MainActivity : ComponentActivity() {
    private val logger = createLogger("MainActivity")
    private var profileManager: AndroidProfileManager? = null

    // Foreground service for keeping connections alive
    private var mudConnectionService: MudConnectionService? = null
    private var serviceBound = false

    // Pending state update to send once service is bound
    private var pendingConnectionStates: Map<String, ModelConnectionState>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            logger.info { "Service connected" }
            val localBinder = binder as MudConnectionService.LocalBinder
            mudConnectionService = localBinder.getService()
            serviceBound = true

            // Send any pending state update that arrived before binding completed
            pendingConnectionStates?.let { states ->
                logger.info { "Sending pending connection states to service" }
                mudConnectionService?.updateConnectionStates(states)
                pendingConnectionStates = null
            }

            // Also update with current states from profile manager
            profileManager?.let { pm ->
                mudConnectionService?.updateConnectionStates(pm.connectionStates.value)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logger.info { "Service disconnected" }
            mudConnectionService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Completely disable gesture navigation to prevent conflicts with profile swiping
        // Physical back button will still work via BackHandler
        WindowCompat.setDecorFitsSystemWindows(window, true)

        AndroidContext.initialize(this)
        logger.info { "Silmaril starting on ${Platform.name}" }

        // Start Koin fresh (stopKoin is called in onDestroy)
        startKoin {
            androidContext(this@MainActivity)
            modules(androidModule)
        }

        profileManager = get(AndroidProfileManager::class.java)

        // Bind to the foreground service
        bindToService()

        setContent {
            SilmarilTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SilmarilTheme.colors.background
                ) {
                    MudClientApp(profileManager = profileManager!!)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unbind from service
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }

        // Cleanup and stop Koin entirely so next onCreate() gets fresh singletons
        // This handles Activity recreation after long minimize, config changes, etc.
        profileManager?.cleanup()
        stopKoin()
        logger.info { "Activity destroyed, Koin stopped for clean restart" }
    }

    /**
     * Binds to MudConnectionService to manage foreground notification.
     */
    private fun bindToService() {
        val intent = Intent(this, MudConnectionService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        logger.info { "Binding to service" }
    }

    /**
     * Starts the foreground service if any connections are active.
     * Called when connection states change.
     */
    internal fun startServiceIfNeeded(hasActiveConnections: Boolean) {
        if (hasActiveConnections) {
            val intent = Intent(this, MudConnectionService::class.java).apply {
                action = MudConnectionService.ACTION_START_SERVICE
            }
            startForegroundService(intent)
            logger.info { "Starting foreground service" }
        }
    }

    /**
     * Updates the foreground service with current connection states.
     * If service is not yet bound, queues the update for when binding completes.
     */
    internal fun updateServiceConnectionStates(states: Map<String, ModelConnectionState>) {
        if (mudConnectionService != null) {
            mudConnectionService?.updateConnectionStates(states)
        } else {
            // Service not bound yet - queue the update
            logger.debug { "Service not bound, queueing connection state update" }
            pendingConnectionStates = states
        }
    }
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun MudClientApp(profileManager: AndroidProfileManager) {
    val mapModel = remember { get<MapModel>(MapModel::class.java) }
    val roomDataManager = remember { get<RoomDataManager>(RoomDataManager::class.java) }
    val unifiedMapsViewModel = remember { get<UnifiedMapsViewModel>(UnifiedMapsViewModel::class.java) }
    val settingsManager = remember { profileManager.settingsManager }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mapModel.initMaps(profileManager)
        }
    }

    // Observe connection states and update foreground service
    val connectionStates by profileManager.connectionStates.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(connectionStates) {
        // Forward connection states to the foreground service
        val activity = context as? MainActivity
        activity?.let { act ->
            // Check if any connections are active or connecting
            val hasActiveConnections = connectionStates.values.any {
                it == ModelConnectionState.CONNECTED || it == ModelConnectionState.CONNECTING
            }

            // Start service if needed
            act.startServiceIfNeeded(hasActiveConnections)

            // Update service with current states
            act.updateServiceConnectionStates(connectionStates)
        }
    }

    val profilesState by profileManager.profilesState.collectAsState()
    val profiles = profilesState.profiles
    val currentProfileName by profileManager.currentProfileName.collectAsState()
    val selectedTabIndex by profileManager.selectedTabIndex.collectAsState()
    val allSavedProfiles by settingsManager.profiles.collectAsState()

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<String?>(null) }

    // Drawer states
    var leftDrawerOpen by remember { mutableStateOf(false) }
    var rightDrawerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var outputTextSize by remember { mutableStateOf(12f) }

    // "Press twice to exit" logic
    var lastBackPressTime by remember { mutableStateOf(0L) }
    val backPressThreshold = 2000L // 2 seconds

    // Handle back button/gesture: ALWAYS intercept
    BackHandler(enabled = true) {
        when {
            // If drawers are open, close them
            leftDrawerOpen || rightDrawerOpen -> {
                leftDrawerOpen = false
                rightDrawerOpen = false
            }
            // If no drawers, use "press twice to exit" pattern
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < backPressThreshold) {
                    // Second press within threshold - exit app
                    (context as? MainActivity)?.finish()
                } else {
                    // First press - show toast
                    lastBackPressTime = currentTime
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Pager state
    val profileList = profiles.keys.toList()
    val pagerState = rememberPagerState(
        initialPage = if (profileList.isEmpty()) 0 else selectedTabIndex.coerceIn(0, profileList.size - 1),
        pageCount = { profileList.size }
    )

    // Track focus state per profile (which profile's input is focused)
    val focusStates = remember { mutableStateMapOf<String, Boolean>() }

    // Track previous profile NAME (not index) to detect switches even when reordering
    var previousProfileName by remember { mutableStateOf(profileList.getOrNull(pagerState.currentPage)) }

    // Track previous profile list to detect reordering
    var previousProfileList by remember { mutableStateOf(profileList) }

    // Flag to track if we need to restore focus after reorder + pager animation
    var pendingFocusRestore by remember { mutableStateOf<String?>(null) }

    // Create FocusRequesters for each profile (keyed by sorted names so reordering doesn't recreate them)
    val focusRequesters = remember(profileList.sorted()) {
        profileList.associateWith { FocusRequester() }
    }

    // Command history per profile (max 20 unique messages per profile)
    // Stored at MainActivity level so it persists across page switches
    val commandHistories = remember { mutableStateMapOf<String, SnapshotStateList<String>>() }

    // Sync pager with profile manager and handle input focus
    LaunchedEffect(pagerState.currentPage, profileList.size) {
        if (profileList.isNotEmpty() && pagerState.currentPage != selectedTabIndex) {
            profileManager.switchWindow(pagerState.currentPage)
        }
    }

    // Detect when profile list gets reordered and set pending focus restore
    LaunchedEffect(profileList) {
        val profileListReordered = profileList.sorted() == previousProfileList.sorted() && profileList != previousProfileList

        if (profileListReordered) {
            // List was reordered - check if we need to restore focus later
            val targetProfileName = profileList.getOrNull(selectedTabIndex)
            val wasTargetFocused = focusStates[targetProfileName] ?: false

            if (wasTargetFocused && targetProfileName != null) {
                pendingFocusRestore = targetProfileName
            }

            previousProfileList = profileList
            previousProfileName = targetProfileName
        } else {
            previousProfileList = profileList
        }
    }

    // Track keyboard visibility state
    val isKeyboardVisible = WindowInsets.isImeVisible

    // Track focus requests: profile name to (should focus, should show keyboard)
    val focusRequests = remember { mutableStateMapOf<String, Pair<Boolean, Boolean>>() }

    // Auto-focus input when switching profiles if previous profile's input was focused
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (profileList.isNotEmpty() && !pagerState.isScrollInProgress) {
            // Check if we have a pending focus restore from a reorder
            val pendingRestore = pendingFocusRestore
            if (pendingRestore != null) {
                // Reorder case - transfer focus only if keyboard is currently visible
                val shouldTransferFocus = isKeyboardVisible
                if (shouldTransferFocus) {
                    focusRequests[pendingRestore] = Pair(true, true)
                }
                pendingFocusRestore = null
            } else {
                // Normal profile switch (swipe, click, etc.) - transfer focus if needed
                val currentProfileName = profileList.getOrNull(pagerState.currentPage)

                // Page switch has settled - check if we switched to a different profile
                if (currentProfileName != null && currentProfileName != previousProfileName) {
                    // Check if the previous profile's input was focused
                    val wasOldPageFocused = focusStates[previousProfileName] ?: false

                    if (wasOldPageFocused) {
                        // Simple logic: if input was focused AND keyboard is currently closed,
                        // don't transfer focus (user closed keyboard with back button)
                        if (isKeyboardVisible) {
                            // Keyboard is open - transfer focus normally
                            focusRequests[currentProfileName] = Pair(true, true)
                        } else {
                            // Keyboard is closed but input was focused - user pressed back
                            // DON'T transfer focus to prevent keyboard from opening
                            // User can manually tap input if they want to type
                        }
                    }

                    previousProfileName = currentProfileName
                }
            }
        }
    }

    LaunchedEffect(selectedTabIndex, profileList.size) {
        if (profileList.isNotEmpty() && pagerState.currentPage != selectedTabIndex) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }
    }

    // DIALOGS
    if (showCreateDialog) {
        CreateProfileDialog(
            existingProfiles = allSavedProfiles,
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                profileManager.addProfile(name)
                // Wait a frame for the profile to be added before switching
                scope.launch {
                    kotlinx.coroutines.yield()
                    profileManager.switchWindow(name)
                }
                showCreateDialog = false
                rightDrawerOpen = false
            }
        )
    }

    if (showOpenDialog) {
        OpenProfileDialog(
            allProfiles = allSavedProfiles,
            openProfiles = profiles.keys,
            onDismiss = { showOpenDialog = false },
            onOpen = { name ->
                profileManager.addProfile(name)
                // Wait a frame for the profile to be added before switching
                scope.launch {
                    kotlinx.coroutines.yield()
                    profileManager.switchWindow(name)
                }
                showOpenDialog = false
                rightDrawerOpen = false
            }
        )
    }

    profileToDelete?.let { name ->
        DeleteConfirmationDialog(
            profileName = name,
            onDismiss = { profileToDelete = null },
            onConfirm = {
                profileManager.deleteProfile(name)
            }
        )
    }

    // Root Box with overlays - exclude entire screen from system gestures
    Box(modifier = Modifier
        .fillMaxSize()
        .systemGestureExclusion()  // Prevent system back gesture entirely
    ) {
        // Main content
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(), // Add safe zone for status bar and notch
                color = Color(0xFF1a1a1a),
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Hamburger menu
                    IconButton(
                        onClick = { leftDrawerOpen = !leftDrawerOpen },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Map drawer",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Center: Profile name
                    Text(
                        text = currentProfileName,
                        color = Color.White,
                        fontSize = 16.sp
                    )

                    // Right: Profiles button
                    IconButton(
                        onClick = { rightDrawerOpen = !rightDrawerOpen },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBox,
                            contentDescription = "Profiles drawer",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // HorizontalPager for profiles
            if (profileList.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .systemGestureExclusion(),  // Prevent system back gesture from interfering
                    // Disable user scrolling if drawers are open
                    userScrollEnabled = !leftDrawerOpen && !rightDrawerOpen
                ) { pageIndex ->
                    val profileName = profileList[pageIndex]
                    val profile = profiles[profileName]
                    val focusRequester = focusRequesters[profileName] ?: FocusRequester()

                    // Get or create command history for this profile
                    val commandHistory = commandHistories.getOrPut(profileName) {
                        mutableStateListOf()
                    }

                    if (profile != null) {
                        val focusRequest = focusRequests[profileName]
                        ProfileContentView(
                            profile = profile,
                            unifiedMapsViewModel = unifiedMapsViewModel,
                            outputTextSize = outputTextSize,
                            focusRequester = focusRequester,
                            commandHistory = commandHistory,
                            shouldRequestFocus = focusRequest?.first ?: false,
                            shouldShowKeyboard = focusRequest?.second ?: true,
                            onFocusChanged = { isFocused ->
                                focusStates[profileName] = isFocused
                            },
                            onFocusHandled = {
                                // Clear the focus request after it's been handled
                                focusRequests.remove(profileName)
                            }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No profiles loaded", color = Color.White)
                }
            }
        }

        // Left drawer overlay
        if (leftDrawerOpen) {
            // Scrim (dark overlay) - click to close
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .zIndex(10f)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            leftDrawerOpen = false
                        }
                    }
            )

            // Drawer content
            val client = profileManager.currentClient.collectAsState().value
            val mainViewModel = profileManager.currentMainViewModel.collectAsState().value
            val mapViewModel = profileManager.currentMapViewModel.collectAsState().value
            val mapInfoByRoom by unifiedMapsViewModel.mapUpdatesForRooms.collectAsState()

            val modelConnectionState = client?.connectionState?.collectAsState()?.value ?: ModelConnectionState.DISCONNECTED
            val connectionState = when (modelConnectionState) {
                ModelConnectionState.CONNECTED -> UiConnectionState.Connected
                ModelConnectionState.CONNECTING -> UiConnectionState.Connecting
                ModelConnectionState.DISCONNECTED -> UiConnectionState.Disconnected
                ModelConnectionState.FAILED -> UiConnectionState.Disconnected
            }

            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.75f)
                    .zIndex(11f),
                color = Color(0xFF1a1a1a)
            ) {
                NavigationDrawer(
                    scaffoldState = rememberScaffoldState(),
                    scope = scope,
                    connectionState = connectionState,
                    serverName = client?.host ?: "adan.ru",
                    onConnect = { client?.connect() },
                    onDisconnect = { client?.forceDisconnect() },
                    onClearOutput = { },
                    client = client,
                    mapViewModel = mapViewModel,
                    mapModel = mapModel,
                    roomDataManager = roomDataManager,
                    mapInfoByRoom = mapInfoByRoom,
                    mainViewModel = mainViewModel,
                    outputTextSize = outputTextSize,
                    onOutputTextSizeChange = { outputTextSize = it }
                )
            }
        }

        // Right drawer overlay
        if (rightDrawerOpen) {
            // Scrim - click to close
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .zIndex(10f)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            rightDrawerOpen = false
                        }
                    }
            )

            // Drawer content (slides from right)
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.75f)
                    .align(Alignment.CenterEnd)
                    .zIndex(11f),
                color = Color(0xFF1a1a1a)
            ) {
                ProfileDrawer(
                    profiles = profiles,
                    currentProfileName = currentProfileName,
                    allSavedProfiles = allSavedProfiles,
                    onCreateProfile = { showCreateDialog = true },
                    onOpenProfile = { showOpenDialog = true },
                    onSwitchProfile = { name ->
                        profileManager.switchWindow(name)
                        rightDrawerOpen = false
                    },
                    onCloseProfile = { name -> profileManager.removeProfile(name) },
                    onDeleteProfile = { name -> profileToDelete = name },
                    onReorderProfiles = { newOrder ->
                        profileManager.reorderProfiles(newOrder)
                        // Focus is automatically handled by the LaunchedEffect that tracks profile name changes
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileContentView(
    profile: ru.adan.silmaril.model.AndroidProfile,
    unifiedMapsViewModel: UnifiedMapsViewModel,
    outputTextSize: Float,
    focusRequester: FocusRequester,
    commandHistory: SnapshotStateList<String>,
    shouldRequestFocus: Boolean,
    shouldShowKeyboard: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onFocusHandled: () -> Unit
) {
    val client = profile.client
    val mainViewModel = profile.mainViewModel
    val groupModel = profile.groupModel
    val mobsModel = profile.mobsModel
    val keyboardController = LocalSoftwareKeyboardController.current

    // Handle focus request from parent
    LaunchedEffect(shouldRequestFocus, shouldShowKeyboard) {
        if (shouldRequestFocus) {
            kotlinx.coroutines.delay(50) // Wait for layout
            focusRequester.requestFocus()

            onFocusHandled() // Clear the request
        }
    }

    val mapInfoByRoom by unifiedMapsViewModel.mapUpdatesForRooms.collectAsState()
    val modelConnectionState by client.connectionState.collectAsState()
    val connectionState = when (modelConnectionState) {
        ModelConnectionState.CONNECTED -> UiConnectionState.Connected
        ModelConnectionState.CONNECTING -> UiConnectionState.Connecting
        ModelConnectionState.DISCONNECTED -> UiConnectionState.Disconnected
        ModelConnectionState.FAILED -> UiConnectionState.Disconnected
    }
    val isEchoOn by mainViewModel.isEnteringPassword.collectAsState()
    val groupStatus by groupModel.groupMates.collectAsState()
    val mobs by mobsModel.mobs.collectAsState()

    val outputLines = remember { mutableStateListOf<OutputItem>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // Command history passed from parent - persists across profile switches
    var showHistoryMenu by remember { mutableStateOf(false) }

    fun addToHistory(command: String) {
        if (command.isNotBlank()) {
            // Remove if already exists (to move to front)
            commandHistory.remove(command)
            // Add to front
            commandHistory.add(0, command)
            // Keep only last 20
            if (commandHistory.size > 20) {
                commandHistory.removeAt(commandHistory.size - 1)
            }
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            totalItems == 0 || lastVisibleItem >= totalItems - 2
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.isScrollInProgress to listState.layoutInfo
        }.collect { (isScrolling, layoutInfo) ->
            if (isScrolling) {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = layoutInfo.totalItemsCount
                val atBottom = totalItems == 0 || lastVisibleItem >= totalItems - 2

                if (!atBottom && autoScrollEnabled) {
                    autoScrollEnabled = false
                }
            }
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom && !autoScrollEnabled) {
            autoScrollEnabled = true
        }
    }

    LaunchedEffect(mainViewModel) {
        mainViewModel.messages.collect { colorfulMessage ->
            val item = OutputItem.new(colorfulMessage)
            outputLines.add(item)
            // Keep last 4000 messages
            if (outputLines.size > 4000) {
                val toDrop = outputLines.size - 4000
                outputLines.removeRange(0, toDrop)
            }
        }
    }

    val lastId by remember { derivedStateOf { outputLines.lastOrNull()?.id } }

    LaunchedEffect(lastId, autoScrollEnabled) {
        if (lastId != null && autoScrollEnabled && outputLines.isNotEmpty()) {
            kotlinx.coroutines.yield()
            listState.scrollToItem(outputLines.size - 1)
        }
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var previousImeBottom by remember { mutableStateOf(0) }

    LaunchedEffect(imeBottom) {
        if (imeBottom > previousImeBottom && outputLines.isNotEmpty()) {
            autoScrollEnabled = true
            listState.scrollToItem(outputLines.size - 1)
        }
        previousImeBottom = imeBottom
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .navigationBarsPadding() // Add safe zone for system navigation bar
                .imePadding()
        ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            if (groupStatus.isNotEmpty() || mobs.isNotEmpty()) {
                GroupMobsWidgets(
                    groupStatus = groupStatus,
                    mobs = mobs,
                    modifier = Modifier.padding(bottom = 4.dp),
                    onGroupCommand = { creatureIndex, commandType ->
                        // Get current group members
                        val currentGroup = groupModel.groupMates.value
                        if (creatureIndex in currentGroup.indices) {
                            val targetCreature = currentGroup[creatureIndex]
                            val targetName = currentGroup.targetName(targetCreature)
                            val fullCommand = "$commandType $targetName"
                            mainViewModel.treatUserInput(fullCommand)
                            scope.launch {
                                autoScrollEnabled = true
                                if (outputLines.isNotEmpty()) {
                                    listState.scrollToItem(outputLines.size - 1)
                                }
                            }
                        }
                    },
                    onMobCommand = { creatureIndex, commandType ->
                        // Get current mobs
                        val currentMobs = mobsModel.mobs.value
                        if (creatureIndex in currentMobs.indices) {
                            val targetCreature = currentMobs[creatureIndex]
                            val targetName = currentMobs.targetName(targetCreature)
                            val fullCommand = "$commandType $targetName"
                            mainViewModel.treatUserInput(fullCommand)
                            scope.launch {
                                autoScrollEnabled = true
                                if (outputLines.isNotEmpty()) {
                                    listState.scrollToItem(outputLines.size - 1)
                                }
                            }
                        }
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
                .padding(4.dp)
        ) {
            if (outputLines.isEmpty()) {
                Text(
                    text = "No output yet. Connect to start.",
                    color = SilmarilTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = outputLines.size,
                        key = { idx -> outputLines[idx].id }
                    ) { idx ->
                        val item = outputLines[idx]
                        OutputLineView(item, outputTextSize)
                    }
                }

                if (!isAtBottom) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                autoScrollEnabled = true
                                listState.scrollToItem(outputLines.size - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(36.dp),
                        backgroundColor = Color(0xFF5a5a5a)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            tint = Color(0xFFe8e8e8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = Color(0xFF3d3d3d),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            onFocusChanged(focusState.isFocused)
                        },
                    singleLine = true,
                    visualTransformation = if (isEchoOn) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (connectionState == UiConnectionState.Connected) {
                                addToHistory(inputText)
                                mainViewModel.treatUserInput(inputText)
                                inputText = ""
                                scope.launch {
                                    autoScrollEnabled = true
                                    if (outputLines.isNotEmpty()) {
                                        listState.scrollToItem(outputLines.size - 1)
                                    }
                                }
                            }
                        }
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFe8e8e8),
                        fontSize = 14.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                    enabled = connectionState == UiConnectionState.Connected,
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = if (isEchoOn) "Enter password..." else "Enter command...",
                                color = Color(0xFF888888),
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            // History button with popup menu
            Box {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                var buttonYPosition by remember { mutableStateOf(0f) }

                // Detect long press
                LaunchedEffect(isPressed) {
                    if (isPressed && commandHistory.isNotEmpty()) {
                        kotlinx.coroutines.delay(500) // Long press threshold
                        if (isPressed) {
                            showHistoryMenu = true
                        }
                    }
                }

                Button(
                    onClick = {
                        // Short click - resend last command
                        if (!showHistoryMenu && commandHistory.isNotEmpty()) {
                            val lastCommand = commandHistory[0]
                            addToHistory(lastCommand)
                            mainViewModel.treatUserInput(lastCommand)
                            scope.launch {
                                autoScrollEnabled = true
                                if (outputLines.isNotEmpty()) {
                                    listState.scrollToItem(outputLines.size - 1)
                                }
                            }
                        }
                    },
                    enabled = connectionState == UiConnectionState.Connected && commandHistory.isNotEmpty(),
                    modifier = Modifier
                        .height(40.dp)
                        .onGloballyPositioned { coordinates ->
                            buttonYPosition = coordinates.positionInWindow().y
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF5a5a5a),
                        disabledBackgroundColor = Color(0xFF3a3a3a)
                    ),
                    interactionSource = interactionSource
                ) {
                    val buttonText = if (commandHistory.isEmpty()) {
                        ""
                    } else {
                        val lastCmd = commandHistory[0]
                        if (lastCmd.length <= 4) lastCmd else "${lastCmd.take(4)}..."
                    }
                    Text(buttonText, color = Color(0xFFe8e8e8), fontSize = 14.sp)
                }

                // Popup menu appearing above the button
                if (showHistoryMenu) {
                    Popup(
                        alignment = Alignment.BottomCenter,
                        offset = IntOffset(0, -40), // Position above button (button height)
                        onDismissRequest = { showHistoryMenu = false },
                        properties = PopupProperties(focusable = false) // Keep keyboard open
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(150.dp) // Fixed narrow width
                                .heightIn(max = 240.dp),
                            color = Color(0xFF2a2a2a),
                            elevation = 8.dp,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                items(commandHistory.size) { index ->
                                    // Reverse order: oldest at top, newest at bottom
                                    val command = commandHistory[commandHistory.size - 1 - index]
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pointerInput(Unit) {
                                                    detectTapGestures {
                                                        addToHistory(command)
                                                        mainViewModel.treatUserInput(command)
                                                        showHistoryMenu = false
                                                        scope.launch {
                                                            autoScrollEnabled = true
                                                            if (outputLines.isNotEmpty()) {
                                                                listState.scrollToItem(outputLines.size - 1)
                                                            }
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = command,
                                                color = Color(0xFFe8e8e8),
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        // Separator between items (except after last)
                                        if (index < commandHistory.size - 1) {
                                            Divider(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                color = Color(0xFF3d3d3d),
                                                thickness = 1.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    addToHistory(inputText)
                    mainViewModel.treatUserInput(inputText)
                    inputText = ""
                    scope.launch {
                        autoScrollEnabled = true
                        if (outputLines.isNotEmpty()) {
                            listState.scrollToItem(outputLines.size - 1)
                        }
                    }
                },
                enabled = connectionState == UiConnectionState.Connected,
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF5a5a5a),
                    disabledBackgroundColor = Color(0xFF3a3a3a)
                )
            ) {
                Text("Send", color = Color(0xFFe8e8e8))
            }
        }
        }

        // Movement control widget overlay
        // Always positioned as if there are 4 creatures (max) to prevent movement when creatures appear/disappear
        val groupWidgetHeight = (4 * 30).dp + 8.dp // 128dp (4 creatures at 30dp each + 8dp padding)

        MovementControlWidget(
            onCommand = { command ->
                mainViewModel.treatUserInput(command)
                scope.launch {
                    autoScrollEnabled = true
                    if (outputLines.isNotEmpty()) {
                        listState.scrollToItem(outputLines.size - 1)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 8.dp, y = 4.dp + groupWidgetHeight + 4.dp)
                .zIndex(0.5f)
        )
    }
}
