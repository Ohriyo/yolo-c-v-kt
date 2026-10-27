package com.yoloc.app.screens.about

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.BuildConfig
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.screens.container
import com.yoloc.app.screens.onboarding.recallOutOfTen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RN: app/about.tsx. Everything about the model comes from model.config.json and the classes
 * table, so a retrained model updates this screen with no code change. The headline is the
 * weakest number that matters most: early-stage recall.
 */
class AboutViewModel(val config: ModelConfig, classRepository: ClassRepository, val appVersion: String) : ViewModel() {
    data class State(
        /** Model index order, for the class chips; null until loaded. */
        val classes: List<DetectionClass>? = null,
    )

    /** Early-stage pods found out of 10, and missed; null when the config has no recall. */
    val found: Int? = recallOutOfTen(config.metrics.earlyStageRecall)
    val missed: Int? = found?.let { 10 - it }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val rows = classRepository.listClasses()
                _state.update { it.copy(classes = rows) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load classes", e)
            }
        }
    }

    companion object {
        private const val TAG = "about"

        val Factory = viewModelFactory {
            initializer { AboutViewModel(container.modelConfig, container.classRepository, BuildConfig.VERSION_NAME) }
        }
    }
}
