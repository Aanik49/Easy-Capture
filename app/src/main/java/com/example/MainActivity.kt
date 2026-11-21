package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.capture.QuickControlNotificationManager
import com.example.data.AudioSourceMode
import com.example.data.RecordingPhase
import com.example.data.ThemeMode
import com.example.ui.EasyCaptureViewModel
import com.example.ui.components.BroadcastCountdownOverlay
import com.example.ui.components.EasyCaptureSplashOverlay
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.EasyCaptureTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private enum class StudioTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CAPTURE("capture", R.string.nav_capture, Icons.Rounded.RadioButtonChecked, Icons.Outlined.RadioButtonChecked),
    EDITOR("editor", R.string.nav_editor, Icons.Rounded.MovieCreation, Icons.Outlined.MovieCreation),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private val quickActionFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dispatchQuickAction(intent)
        // Activate top-bar quick controls immediately upon opening the app (if notification permission is already granted)
        QuickControlNotificationManager.onAppOpened(applicationContext, true)
        setContent {
            EasyCaptureApp(
                incomingQuickActionFlow = quickActionFlow.asStateFlow(),
                onQuickActionConsumed = { quickActionFlow.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchQuickAction(intent)
    }

    private fun dispatchQuickAction(intent: Intent?) {
        val action = intent?.getStringExtra(QuickControlNotificationManager.EXTRA_QUICK_ACTION)
        if (!action.isNullOrBlank()) {
            quickActionFlow.value = action
            intent.removeExtra(QuickControlNotificationManager.EXTRA_QUICK_ACTION)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyCaptureApp(
    incomingQuickActionFlow: kotlinx.coroutines.flow.StateFlow<String?>,
    onQuickActionConsumed: () -> Unit,
    viewModel: EasyCaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val quickAction by incomingQuickActionFlow.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(StudioTab.CAPTURE) }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var pendingRecordAfterMicPermission by remember { mutableStateOf(false) }
    var pendingStandaloneScreenshot by remember { mutableStateOf(false) }
    var requestedLaunchNotif by rememberSaveable { mutableStateOf(false) }

    var hasMicPermission by remember { mutableStateOf(checkMicPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }

    // Non-blocking active pointer coordinates for Show Screen Touches during recording
    val activeTouchPoints = remember { mutableStateMapOf<Long, Offset>() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasMicPermission = checkMicPermission(context)
        hasOverlayPermission = checkOverlayPermission(context)
        hasNotificationPermission = checkNotificationPermission(context)
        QuickControlNotificationManager.onAppOpened(context.applicationContext, settings.quickControlBarEnabled)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted && settings.quickControlBarEnabled) {
            QuickControlNotificationManager.onAppOpened(context.applicationContext, true)
        }
    }

    // Activate top bar immediately once opened, and on Android 13+ request notification permission right after splash clears
    LaunchedEffect(showSplash, hasNotificationPermission, settings.quickControlBarEnabled) {
        if (!settings.quickControlBarEnabled) {
            QuickControlNotificationManager.setEnabledPreference(context.applicationContext, false)
        } else if (hasNotificationPermission) {
            QuickControlNotificationManager.onAppOpened(context.applicationContext, true)
        } else if (!showSplash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requestedLaunchNotif) {
            requestedLaunchNotif = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val forScreenshot = pendingStandaloneScreenshot
        pendingStandaloneScreenshot = false
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            if (forScreenshot) {
                viewModel.captureStandaloneScreenshotWithConsent(result.resultCode, data)
            } else {
                viewModel.startRecordingWithConsent(result.resultCode, data)
            }
        }
    }

    val launchScreenCaptureConsent = {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (pendingRecordAfterMicPermission) {
            pendingRecordAfterMicPermission = false
            launchScreenCaptureConsent()
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = checkOverlayPermission(context)
    }

    val openOverlaySettings = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    val openDeveloperOptions = {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadVideoForEditing(uri)
        }
    }

    val requestStartRecording = {
        pendingStandaloneScreenshot = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needsMic = settings.audioSource != AudioSourceMode.MUTE
        if (needsMic && !hasMicPermission) {
            pendingRecordAfterMicPermission = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            launchScreenCaptureConsent()
        }
    }

    // Handle quick actions tapped from the persistent top-bar notification
    LaunchedEffect(quickAction) {
        val act = quickAction ?: return@LaunchedEffect
        showSplash = false
        when (act) {
            QuickControlNotificationManager.QUICK_ACTION_RECORD -> {
                currentTab = StudioTab.CAPTURE
                if (captureState.phase == RecordingPhase.IDLE) {
                    requestStartRecording()
                }
            }
            QuickControlNotificationManager.QUICK_ACTION_SCREENSHOT -> {
                currentTab = StudioTab.CAPTURE
                if (captureState.phase == RecordingPhase.RECORDING || captureState.phase == RecordingPhase.PAUSED) {
                    viewModel.triggerInAppScreenshot()
                } else {
                    pendingStandaloneScreenshot = true
                    launchScreenCaptureConsent()
                }
            }
            QuickControlNotificationManager.QUICK_ACTION_TOOLS -> {
                currentTab = StudioTab.EDITOR
            }
            QuickControlNotificationManager.QUICK_ACTION_HOME -> {
                currentTab = StudioTab.CAPTURE
            }
        }
        onQuickActionConsumed()
    }

    BackHandler(enabled = captureState.phase == RecordingPhase.COUNTDOWN) {
        viewModel.cancelCountdown()
    }

    BackHandler(enabled = captureState.phase != RecordingPhase.COUNTDOWN && currentTab != StudioTab.CAPTURE) {
        currentTab = StudioTab.CAPTURE
    }

    val showTouchFeedback = settings.showScreenTouches &&
        (captureState.phase == RecordingPhase.RECORDING || captureState.phase == RecordingPhase.PAUSED)

    EasyCaptureTheme(themeMode = settings.themeMode) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(showTouchFeedback) {
                    if (!showTouchFeedback) {
                        activeTouchPoints.clear()
                        return@pointerInput
                    }
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    activeTouchPoints[change.id.value] = change.position
                                } else {
                                    activeTouchPoints.remove(change.id.value)
                                }
                            }
                        }
                    }
                }
        ) {
            val useNavigationRail = maxWidth >= 600.dp

            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.height(24.dp))
                            StudioTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = {
                            TopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (captureState.phase == RecordingPhase.RECORDING) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.secondary
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            val next = if (settings.themeMode == ThemeMode.DARK) {
                                                ThemeMode.LIGHT
                                            } else {
                                                ThemeMode.DARK
                                            }
                                            viewModel.setThemeMode(next)
                                        },
                                        modifier = Modifier.testTag("quick_theme_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (settings.themeMode == ThemeMode.DARK) {
                                                Icons.Rounded.LightMode
                                            } else {
                                                Icons.Rounded.DarkMode
                                            },
                                            contentDescription = "Toggle Dark or Light Mode",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        },
                        bottomBar = {
                            if (!useNavigationRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    windowInsets = WindowInsets.navigationBars
                                ) {
                                    StudioTab.entries.forEach { tab ->
                                        val selected = currentTab == tab
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = stringResource(tab.labelRes)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = stringResource(tab.labelRes),
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            modifier = Modifier.testTag("nav_${tab.route}")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        when (currentTab) {
                            StudioTab.CAPTURE -> CaptureScreen(
                                settings = settings,
                                captureState = captureState,
                                hasOverlayPermission = hasOverlayPermission,
                                hasMicPermission = hasMicPermission,
                                onStartRecordingClick = requestStartRecording,
                                onStopRecordingClick = viewModel::stopRecording,
                                onTogglePauseClick = viewModel::togglePauseRecording,
                                onSnapScreenshotClick = viewModel::triggerInAppScreenshot,
                                onRequestOverlayPermission = openOverlaySettings,
                                onSelectAreaMode = viewModel::setRecordingArea,
                                onSelectAudioMode = viewModel::setAudioSource,
                                onSelectResolution = viewModel::setResolution,
                                onSelectFormat = viewModel::setExportFormat,
                                onSelectFps = viewModel::setFrameRate,
                                onToggleFloatingButton = viewModel::setFloatingButtonEnabled,
                                onDismissBanner = viewModel::dismissCaptureBanner,
                                modifier = Modifier.padding(innerPadding)
                            )

                            StudioTab.EDITOR -> EditorScreen(
                                uiState = editorState,
                                onPickVideoClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                onUpdateTrim = viewModel::updateTrimRange,
                                onSelectCropPreset = viewModel::selectEditorCropPreset,
                                onUpdateSpeed = viewModel::updateEditorSpeed,
                                onRotate90 = viewModel::rotateEditorVideo90,
                                onToggleMute = viewModel::toggleEditorMute,
                                onSelectResolution = viewModel::updateEditorExportResolution,
                                onSelectFormat = viewModel::updateEditorExportFormat,
                                onSelectFps = viewModel::updateEditorExportFps,
                                onExportClick = viewModel::exportEditedVideo,
                                onDismissStatus = viewModel::dismissExportStatus,
                                modifier = Modifier.padding(innerPadding)
                            )

                            StudioTab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                hasMicPermission = hasMicPermission,
                                hasOverlayPermission = hasOverlayPermission,
                                hasNotificationPermission = hasNotificationPermission,
                                onSelectThemeMode = viewModel::setThemeMode,
                                onSelectResolution = viewModel::setResolution,
                                onSelectFormat = viewModel::setExportFormat,
                                onSelectFps = viewModel::setFrameRate,
                                onSelectBitrateMbps = viewModel::setBitrateMbps,
                                onSelectCountdown = viewModel::setCountdownSeconds,
                                onToggleFloatingEnabled = viewModel::setFloatingButtonEnabled,
                                onUpdateFloatingOpacity = viewModel::setFloatingButtonOpacity,
                                onToggleHapticEnabled = viewModel::setHapticFeedbackEnabled,
                                onToggleShowScreenTouches = viewModel::setShowScreenTouches,
                                onToggleQuickControlBar = viewModel::setQuickControlBarEnabled,
                                onOpenDeveloperOptions = { openDeveloperOptions() },
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onRequestOverlayPermission = openOverlaySettings,
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }

                // Professional Broadcast Countdown Overlay with immediate Start Now button
                AnimatedVisibility(
                    visible = captureState.phase == RecordingPhase.COUNTDOWN,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BroadcastCountdownOverlay(
                        remainingSeconds = captureState.countdownRemaining,
                        totalSeconds = settings.countdownSeconds,
                        settings = settings,
                        onStartNow = viewModel::startRecordingImmediately,
                        onCancel = viewModel::cancelCountdown
                    )
                }

                // Simple, fast studio splash overlay on cold app launch
                AnimatedVisibility(
                    visible = showSplash,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    EasyCaptureSplashOverlay(
                        onFinished = { showSplash = false }
                    )
                }

                // Live visual screen-touch indicator during recording when enabled in Settings
                if (showTouchFeedback && activeTouchPoints.isNotEmpty()) {
                    val touchColor = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        activeTouchPoints.values.forEach { pt ->
                            drawCircle(
                                color = touchColor.copy(alpha = 0.24f),
                                radius = 24.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.85f),
                                radius = 13.dp.toPx(),
                                center = pt,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                            drawCircle(
                                color = touchColor,
                                radius = 6.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun checkMicPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
