package com.yoloc.app.store

import android.util.Log
import com.yoloc.app.db.repositories.SettingCodec
import com.yoloc.app.db.repositories.SettingCodecs
import com.yoloc.app.db.repositories.SettingsRepository
import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.features.detection.ModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * RN: settingsStore.ts. Settings the UI reads often, cached here; the settings table stays the
 * source of truth.
 *
 * Loads and writes run in [scope] (the app's), not the caller's: a screen that closes mid-write
 * must not cancel the write, and a cancelled caller must not cancel a load others are waiting on.
 */
class SettingsStore(
    private val settings: SettingsRepository,
    config: ModelConfig,
    private val scope: CoroutineScope,
) {
    enum class Status { Idle, Loading, Ready, Error }

    data class State(
        /** Ready once the settings table has been read at startup. */
        val status: Status = Status.Idle,
        val error: String? = null,
        val onboardingDone: Boolean = false,
        val theme: ThemeSetting = ThemeSetting.System,
        val confThreshold: Double,
        val gpsOptIn: Boolean = false,
    )

    private val _state = MutableStateFlow(State(confThreshold = config.confThreshold))
    val state: StateFlow<State> = _state.asStateFlow()

    private val hydrateLock = Any()
    private var hydrating: Deferred<Unit>? = null
    private val writeLock = Mutex()

    /** Opens the database (migrating and seeding it) and loads settings. Safe to retry. */
    suspend fun hydrate() {
        // A second caller awaits the load already in flight instead of starting one.
        val load = synchronized(hydrateLock) {
            hydrating ?: scope.async { load() }.also { started ->
                hydrating = started
                started.invokeOnCompletion { synchronized(hydrateLock) { if (hydrating === started) hydrating = null } }
            }
        }
        load.await()
    }

    private suspend fun load() {
        _state.update { it.copy(status = Status.Loading, error = null) }
        try {
            val s = settings.getSettings()
            _state.update {
                it.copy(
                    status = Status.Ready,
                    onboardingDone = s.onboardingDone,
                    theme = s.theme,
                    confThreshold = s.confThreshold,
                    gpsOptIn = s.gpsOptIn,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(status = Status.Error, error = e.message ?: e.toString()) }
        }
    }

    suspend fun completeOnboarding() = set(SettingCodecs.onboardingDone, { it.onboardingDone }) { it.copy(onboardingDone = true) }

    /** Settings / testing: show onboarding again. */
    suspend fun resetOnboarding() = set(SettingCodecs.onboardingDone, { it.onboardingDone }) { it.copy(onboardingDone = false) }

    suspend fun setTheme(theme: ThemeSetting) = set(SettingCodecs.theme, { it.theme }) { it.copy(theme = theme) }

    /** Saved for the next scan; the detection pipeline reads it from the settings table. */
    suspend fun setConfThreshold(value: Double) =
        set(SettingCodecs.confThreshold, { it.confThreshold }) { it.copy(confThreshold = value) }

    suspend fun setGpsOptIn(optIn: Boolean) = set(SettingCodecs.gpsOptIn, { it.gpsOptIn }) { it.copy(gpsOptIn = optIn) }

    /**
     * Updates the cache at once (the screen responds without waiting on storage), then persists.
     * A failed write is logged and the cached value kept for this session; the table wins on the
     * next launch, so the app never gets stuck.
     *
     * Each write stores the newest cached value under one lock, so quick successive changes (the
     * slider) cannot leave an older value in the table even if their writes run out of order.
     */
    private suspend fun <T> set(codec: SettingCodec<T>, read: (State) -> T, change: (State) -> State) {
        _state.update(change)
        scope.launch {
            try {
                writeLock.withLock { settings.setSetting(codec, read(_state.value)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not save setting ${codec.key}", e)
            }
        }.join()
    }

    private companion object {
        const val TAG = "settingsStore"
    }
}
