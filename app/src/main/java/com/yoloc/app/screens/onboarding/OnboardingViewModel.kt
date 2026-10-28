package com.yoloc.app.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.onboarding.ONBOARDING_STEPS
import com.yoloc.app.features.onboarding.OnboardingSlide
import com.yoloc.app.screens.container
import com.yoloc.app.store.SettingsStore
import com.yoloc.app.store.cleanUserName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RN: (onboarding)/welcome.tsx and permissions.tsx. The welcome slides, then the permission
 * primer, which requests nothing. The screen navigates to the primer on Skip or on the last
 * slide's "Get started"; completing onboarding makes the root swap onboarding for the tabs.
 */
class OnboardingViewModel(private val settings: SettingsStore, config: ModelConfig) : ViewModel() {
    data class State(
        val index: Int = 0,
        /** For the "aid" slide: pods found out of 10 at early stage, from the model's recall. */
        val aidFound: Int? = null,
    ) {
        val slide: OnboardingSlide get() = OnboardingSlide.entries[index]
        val isLast: Boolean get() = index == OnboardingSlide.entries.lastIndex

        /** Steps for the progress row: the slides plus the primer. */
        val total: Int get() = ONBOARDING_STEPS
    }

    private val _state = MutableStateFlow(State(aidFound = recallOutOfTen(config.metrics.earlyStageRecall)))
    val state: StateFlow<State> = _state.asStateFlow()

    /** Next slide; on the last one the screen goes to the primer instead. */
    fun next() = _state.update { if (it.isLast) it else it.copy(index = it.index + 1) }

    /** Android back: a slide back. False on the first slide, so back leaves as usual. */
    fun back(): Boolean {
        if (_state.value.index == 0) return false
        _state.update { it.copy(index = it.index - 1) }
        return true
    }

    /** The primer's Continue. A name typed there is saved first, so Home greets with it at once. */
    fun complete(name: String = "") {
        viewModelScope.launch {
            if (cleanUserName(name).isNotEmpty()) settings.setUserName(name)
            settings.completeOnboarding()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { OnboardingViewModel(container.settingsStore, container.modelConfig) }
        }
    }
}

/** RN: Math.round(recall * 10); null when the model config has no recall. */
internal fun recallOutOfTen(recall: Double?): Int? = recall?.let { Math.round(it * 10).toInt() }
