package androidx.hilt.navigation.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * hiltViewModel() on the desktop: the view model comes from the Dagger graph, scoped to the
 * same owner (the screen's back-stack entry) as on Android, with that entry's arguments in
 * its SavedStateHandle.
 */
@Composable
inline fun <reified VM : ViewModel> hiltViewModel(
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
    key: String? = null,
): VM = viewModel(viewModelStoreOwner = viewModelStoreOwner, key = key, initializer = {
    com.cherry.butler.desktop.DesktopGraph.viewModel(VM::class.java, com.cherry.butler.desktop.savedState(this))
})
