package com.yoloc.app.screens.dev

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yoloc.app.BuildConfig
import com.yoloc.app.features.dev.FAKE_SCAN_TAG
import com.yoloc.app.features.dev.SampleImage
import com.yoloc.app.features.dev.removeFakeScans
import com.yoloc.app.features.dev.seedFakeScans
import com.yoloc.app.screens.app
import com.yoloc.app.screens.container
import com.yoloc.app.store.ScanLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * RN: app/dev/seed.tsx. Fills History with fake scans through the real save path, and removes
 * them. Debug builds only: the removal deletes for good every scan whose notes start with the fake
 * tag, so a release build must never reach it ([enabled] guards it even if a route slipped in).
 */
class DevSeedViewModel(
    private val seedTask: suspend (count: Int) -> Int,
    private val removeTask: suspend () -> Int,
    private val countTask: suspend () -> Int,
    private val library: ScanLibrary,
    val enabled: Boolean = BuildConfig.DEBUG,
) : ViewModel() {
    sealed interface Message {
        data class Added(val n: Int) : Message

        data class Removed(val n: Int) : Message

        data object Failed : Message
    }

    data class State(
        /** Fake scans in the database; null until counted. */
        val count: Int? = null,
        val busy: Boolean = false,
        val message: Message? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        if (enabled) refreshCount()
    }

    private fun refreshCount() {
        viewModelScope.launch {
            try {
                val n = countTask()
                _state.update { it.copy(count = n) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not count fake scans", e)
            }
        }
    }

    fun seed(n: Int) = perform { Message.Added(seedTask(n)) }

    fun remove() = perform { Message.Removed(removeTask()) }

    private fun perform(task: suspend () -> Message) {
        if (!enabled || _state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val message = try {
                    task()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Fake scan task failed", e)
                    Message.Failed
                }
                _state.update { it.copy(busy = false, message = message) }
                refreshCount()
            } finally {
                // Leaving mid-seed keeps the scans already saved; the lists must still show them.
                library.bump()
            }
        }
    }

    companion object {
        private const val TAG = "devSeed"
        const val FAKE_COUNT = 20

        /** Enough to check that History stays smooth with a season of scans. */
        const val FAKE_COUNT_LARGE = 500

        /** Small bundled photo (365 x 547, debug assets) used as every fake scan's image. */
        private const val SAMPLE_ASSET = "sample/sample_04.jpg"

        val Factory = viewModelFactory {
            initializer {
                val application = app
                val c = container
                DevSeedViewModel(
                    seedTask = { n ->
                        // RN: Asset.loadAsync gives a local file the same way.
                        val file = File(application.cacheDir, "dev/sample_04.jpg")
                        withContext(Dispatchers.IO) {
                            if (!file.exists()) {
                                // Copy next to it, then rename, so an interrupted copy never leaves a broken sample.
                                file.parentFile?.mkdirs()
                                val temp = File(file.parentFile, "${file.name}.tmp")
                                application.assets.open(SAMPLE_ASSET).use { input -> temp.outputStream().use { input.copyTo(it) } }
                                check(temp.renameTo(file)) { "Could not write ${file.name}" }
                            }
                        }
                        seedFakeScans(
                            c.scanRepository, c.plotRepository, c.classRepository, c.settingsRepository, c.files,
                            SampleImage(file, 365, 547), c.modelConfig, n,
                        )
                    },
                    removeTask = { removeFakeScans(c.devRepository, c.files) },
                    countTask = { c.devRepository.countScansWithNotePrefix(FAKE_SCAN_TAG) },
                    library = c.scanLibrary,
                )
            }
        }
    }
}
