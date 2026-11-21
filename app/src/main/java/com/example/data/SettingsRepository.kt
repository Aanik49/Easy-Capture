package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.easyCaptureDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "easy_capture_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val RESOLUTION = stringPreferencesKey("resolution")
        val EXPORT_FORMAT = stringPreferencesKey("export_format")
        val FRAME_RATE = intPreferencesKey("frame_rate")
        val BITRATE_MBPS = intPreferencesKey("bitrate_mbps")
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val AREA_MODE = stringPreferencesKey("area_mode")
        val AREA_LEFT = floatPreferencesKey("area_left")
        val AREA_TOP = floatPreferencesKey("area_top")
        val AREA_RIGHT = floatPreferencesKey("area_right")
        val AREA_BOTTOM = floatPreferencesKey("area_bottom")
        val COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        val FLOATING_ENABLED = booleanPreferencesKey("floating_enabled")
        val FLOATING_OPACITY = floatPreferencesKey("floating_opacity")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val SHOW_SCREEN_TOUCHES = booleanPreferencesKey("show_screen_touches")
        val QUICK_CONTROL_BAR = booleanPreferencesKey("quick_control_bar")
    }

    val settingsFlow: Flow<CaptureSettings> = context.easyCaptureDataStore.data.map { prefs ->
        CaptureSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            resolution = prefs[Keys.RESOLUTION]?.let { runCatching { VideoResolution.valueOf(it) }.getOrNull() }
                ?: VideoResolution.FHD_1080P,
            exportFormat = prefs[Keys.EXPORT_FORMAT]?.let { runCatching { VideoExportFormat.valueOf(it) }.getOrNull() }
                ?: VideoExportFormat.MP4_H264,
            frameRate = prefs[Keys.FRAME_RATE] ?: 60,
            bitrateMbps = prefs[Keys.BITRATE_MBPS] ?: 16,
            audioSource = prefs[Keys.AUDIO_SOURCE]?.let { runCatching { AudioSourceMode.valueOf(it) }.getOrNull() }
                ?: AudioSourceMode.MICROPHONE,
            areaMode = prefs[Keys.AREA_MODE]?.let { runCatching { RecordingAreaMode.valueOf(it) }.getOrNull() }
                ?: RecordingAreaMode.FULL_SCREEN,
            customArea = NormalizedRect(
                left = prefs[Keys.AREA_LEFT] ?: 0f,
                top = prefs[Keys.AREA_TOP] ?: 0f,
                right = prefs[Keys.AREA_RIGHT] ?: 1f,
                bottom = prefs[Keys.AREA_BOTTOM] ?: 1f
            ).sanitized(),
            countdownSeconds = prefs[Keys.COUNTDOWN_SECONDS] ?: 3,
            floatingButtonEnabled = prefs[Keys.FLOATING_ENABLED] ?: true,
            floatingButtonOpacity = (prefs[Keys.FLOATING_OPACITY] ?: 0.82f).coerceIn(0.35f, 1f),
            hapticFeedbackEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            showScreenTouches = prefs[Keys.SHOW_SCREEN_TOUCHES] ?: false,
            quickControlBarEnabled = prefs[Keys.QUICK_CONTROL_BAR] ?: true
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.easyCaptureDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateResolution(resolution: VideoResolution) {
        context.easyCaptureDataStore.edit { it[Keys.RESOLUTION] = resolution.name }
    }

    suspend fun updateExportFormat(format: VideoExportFormat) {
        context.easyCaptureDataStore.edit { it[Keys.EXPORT_FORMAT] = format.name }
    }

    suspend fun updateFrameRate(fps: Int) {
        context.easyCaptureDataStore.edit { it[Keys.FRAME_RATE] = fps }
    }

    suspend fun updateBitrateMbps(mbps: Int) {
        context.easyCaptureDataStore.edit { it[Keys.BITRATE_MBPS] = mbps }
    }

    suspend fun updateAudioSource(source: AudioSourceMode) {
        context.easyCaptureDataStore.edit { it[Keys.AUDIO_SOURCE] = source.name }
    }

    suspend fun updateArea(mode: RecordingAreaMode, rect: NormalizedRect) {
        val clean = rect.sanitized()
        context.easyCaptureDataStore.edit {
            it[Keys.AREA_MODE] = mode.name
            it[Keys.AREA_LEFT] = clean.left
            it[Keys.AREA_TOP] = clean.top
            it[Keys.AREA_RIGHT] = clean.right
            it[Keys.AREA_BOTTOM] = clean.bottom
        }
    }

    suspend fun updateCountdown(seconds: Int) {
        context.easyCaptureDataStore.edit { it[Keys.COUNTDOWN_SECONDS] = seconds }
    }

    suspend fun updateFloatingEnabled(enabled: Boolean) {
        context.easyCaptureDataStore.edit { it[Keys.FLOATING_ENABLED] = enabled }
    }

    suspend fun updateFloatingOpacity(opacity: Float) {
        context.easyCaptureDataStore.edit { it[Keys.FLOATING_OPACITY] = opacity.coerceIn(0.35f, 1f) }
    }

    suspend fun updateHapticEnabled(enabled: Boolean) {
        context.easyCaptureDataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun updateShowScreenTouches(enabled: Boolean) {
        context.easyCaptureDataStore.edit { it[Keys.SHOW_SCREEN_TOUCHES] = enabled }
    }

    suspend fun updateQuickControlBarEnabled(enabled: Boolean) {
        context.easyCaptureDataStore.edit { it[Keys.QUICK_CONTROL_BAR] = enabled }
    }
}
