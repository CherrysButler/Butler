package com.cherry.butler.feature.lorebook

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cherry.butler.ui.navigation.Routes

internal fun NavGraphBuilder.lorebookLibraryDestinations(navController: NavHostController) {
    composable(Routes.LOREBOOKS) {
        LorebooksLibraryScreen(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(Routes.lorebookEditor(it)) },
            onEditImport = { draftId -> navController.navigate(Routes.importLorebook(draftId)) },
        )
    }
    composable(
        route = Routes.IMPORT_LOREBOOK,
        arguments = listOf(navArgument(Routes.ARG_LOREBOOK_DRAFT_ID) { type = NavType.StringType }),
    ) { entry ->
        LorebookEditorScreen(
            bookId = null,
            newBookScope = null,
            onBack = { navController.popBackStack() },
            onSaved = { _, _ -> navController.popBackStack() },
            importDraftId = requireNotNull(entry.arguments?.getString(Routes.ARG_LOREBOOK_DRAFT_ID)),
        )
    }
}
