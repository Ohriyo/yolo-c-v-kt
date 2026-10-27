package com.yoloc.app.screens.learn

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.db.repositories.ClassRepository
import com.yoloc.app.db.repositories.DetectionClass
import com.yoloc.app.features.learn.LearnTopic
import com.yoloc.app.screens.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RN: app/learn/[topic].tsx. The topic from the route (null for an unknown one: the screen shows
 * an error). The Stages topic lists the classes table, mildest first; the others are strings.
 */
class LearnTopicViewModel(topicKey: String?, classRepository: ClassRepository) : ViewModel() {
    data class State(
        val topic: LearnTopic?,
        /** By severity, mildest first; null until loaded. */
        val classes: List<DetectionClass>? = null,
    )

    private val _state = MutableStateFlow(State(LearnTopic.fromKey(topicKey)))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        if (_state.value.topic == LearnTopic.Stages) {
            viewModelScope.launch {
                try {
                    val rows = classRepository.listClasses().sortedBy { it.severity }
                    _state.update { it.copy(classes = rows) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load classes", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "learn"

        val Factory = viewModelFactory {
            initializer {
                // The type-safe LearnTopic(topic) route (Step 5.3) stores its argument by name.
                LearnTopicViewModel(createSavedStateHandle().get<String>("topic"), container.classRepository)
            }
        }
    }
}
