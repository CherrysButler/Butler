package com.cherry.butler.feature.lorebook

import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.cherry.butler.ui.navigation.Routes

internal fun NavGraphBuilder.lorebookLibraryDestinations(navController: NavHostController) {
    composable(Routes.LOREBOOKS) {
        LorebooksLibraryScreen(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(Routes.lorebookEditor(it)) },
            onEditImport = { navController.navigate(Routes.IMPORT_LOREBOOK) },
        )
    }
    composable(Routes.IMPORT_LOREBOOK) { entry ->
        val libraryEntry = remember(entry) {
            navController.previousBackStackEntry?.takeIf { it.destination.route == Routes.LOREBOOKS }
        }
        val libraryModel = libraryEntry?.let { hiltViewModel<LorebooksViewModel>(it) }
        LorebookEditorScreen(
            bookId = null,
            newBookScope = null,
            onBack = { navController.popBackStack() },
            onSaved = { _, _ -> navController.popBackStack() },
            takeImportedDraft = { libraryModel?.takeImportedDraft() },
        )
    }
}
