package com.example.spatialchess

import com.example.spatialchess.ui.ChessApp
import com.example.spatialchess.ui.ChessTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

/**
 * Spatial Chess entry point.
 *
 * The whole product lives in one Form.Volumetric [DefaultWindowContainer] (declared in the
 * manifest) so the board, the trays and the panels share one Shared-Space window and no Stage is
 * ever opened (PRD F01).
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            ChessTheme {
                ChessApp()
            }
        }
    }
