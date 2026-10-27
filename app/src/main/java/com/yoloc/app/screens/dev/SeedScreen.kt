package com.yoloc.app.screens.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoloc.app.R
import com.yoloc.app.components.AppText
import com.yoloc.app.components.ButtonSize
import com.yoloc.app.components.ButtonVariant
import com.yoloc.app.components.IconName
import com.yoloc.app.components.ScreenContainer
import com.yoloc.app.components.Tone
import com.yoloc.app.components.YButton
import com.yoloc.app.screens.dev.DevSeedViewModel.Companion.FAKE_COUNT
import com.yoloc.app.screens.dev.DevSeedViewModel.Companion.FAKE_COUNT_LARGE

/**
 * RN: app/dev/seed.tsx. Dev only: fills History with fake scans through the real save path.
 * Reached from Settings in debug builds only.
 */
@Composable
fun SeedScreen(vm: DevSeedViewModel = viewModel(factory = DevSeedViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    ScreenContainer {
        if (!vm.enabled) {
            AppText(stringResource(R.string.placeholder), tone = Tone.Muted)
            return@ScreenContainer
        }
        AppText(stringResource(R.string.dev_seed_body))
        AppText(s.count?.let { stringResource(R.string.dev_seed_count, it) } ?: " ")
        YButton(
            pluralStringResource(R.plurals.dev_seed_seed, FAKE_COUNT, FAKE_COUNT),
            { vm.seed(FAKE_COUNT) },
            icon = IconName.Plus,
            size = ButtonSize.Lg,
            fullWidth = true,
            loading = s.busy,
            enabled = !s.busy,
        )
        YButton(
            pluralStringResource(R.plurals.dev_seed_seed, FAKE_COUNT_LARGE, FAKE_COUNT_LARGE),
            { vm.seed(FAKE_COUNT_LARGE) },
            variant = ButtonVariant.Secondary,
            icon = IconName.Layers,
            fullWidth = true,
            enabled = !s.busy,
        )
        YButton(
            stringResource(R.string.dev_seed_remove),
            vm::remove,
            variant = ButtonVariant.Secondary,
            icon = IconName.Trash2,
            fullWidth = true,
            enabled = !s.busy && (s.count ?: 0) > 0,
        )
        val message = when (val m = s.message) {
            is DevSeedViewModel.Message.Added -> pluralStringResource(R.plurals.dev_seed_added, m.n, m.n)
            is DevSeedViewModel.Message.Removed -> pluralStringResource(R.plurals.dev_seed_removed, m.n, m.n)
            DevSeedViewModel.Message.Failed -> stringResource(R.string.dev_seed_failed)
            null -> null
        }
        if (message != null) AppText(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
