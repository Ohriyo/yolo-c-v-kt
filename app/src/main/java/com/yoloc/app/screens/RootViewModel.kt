package com.yoloc.app.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.ThemeSetting
import com.yoloc.app.store.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * RN: app/_layout.tsx. Opens the database (migrate + seed) and reads settings before anything is
 * shown, then picks onboarding or the tabs. The theme follows the saved setting.
 */
class RootViewModel(private val settings: SettingsStore) : ViewModel() {
    data class State(
        val status: SettingsStore.Status,
        val theme: ThemeSetting,
        val onboardingDone: Boolean,
    )

    val state: StateFlow<State> = settings.state
        .map { State(it.status, it.theme, it.onboardingDone) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.state.value.let { State(it.status, it.theme, it.onboardingDone) })

    init {
        // RN: `if (status === 'idle') void hydrate()`. A recreated activity finds it Ready and skips this.
        if (settings.state.value.status == SettingsStore.Status.Idle) retry()
    }

    /** The startup error's Retry. */
    fun retry() {
        viewModelScope.launch { settings.hydrate() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RootViewModel(container.settingsStore) }
        }
    }
}
