/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.trustpanel

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import mozilla.components.support.ktx.android.view.setNavigationBarColorCompat
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.components.menu.compose.MenuDialogBottomSheet
import org.mozilla.fenix.settings.trustpanel.ui.PROTECTION_PANEL_ROUTE
import org.mozilla.fenix.settings.trustpanel.ui.ProtectionPanel
import org.mozilla.fenix.settings.trustpanel.ui.TRACKERS_PANEL_ROUTE
import org.mozilla.fenix.settings.trustpanel.ui.TrackersBlockedPanel
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * A bottom sheet dialog fragment displaying the unified trust panel.
 */
class TrustPanelFragment : BottomSheetDialogFragment() {

    private val args by navArgs<TrustPanelFragmentArgs>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                val safeActivity = activity ?: return@setOnShowListener
                val browsingModeManager = (safeActivity as HomeActivity).browsingModeManager

                val navigationBarColor = if (browsingModeManager.mode.isPrivate) {
                    ContextCompat.getColor(context, R.color.fx_mobile_private_layer_color_3)
                } else {
                    ContextCompat.getColor(context, R.color.fx_mobile_layer_color_3)
                }

                window?.setNavigationBarColorCompat(navigationBarColor)

                val bottomSheet = findViewById<View?>(R.id.design_bottom_sheet)
                bottomSheet?.setBackgroundResource(android.R.color.transparent)

                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.peekHeight = resources.displayMetrics.heightPixels
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            FirefoxTheme {
                MenuDialogBottomSheet(
                    onRequestDismiss = ::dismiss,
                    handlebarContentDescription = "",
                ) {
                    val navHostController = rememberNavController()

                    NavHost(
                        navController = navHostController,
                        startDestination = PROTECTION_PANEL_ROUTE,
                    ) {
                        composable(route = PROTECTION_PANEL_ROUTE) {
                            ProtectionPanel(
                                url = args.url,
                                title = args.title,
                                isSecured = args.isSecured,
                                onTrackerBlockedMenuClick = {
                                    navHostController.navigate(route = TRACKERS_PANEL_ROUTE)
                                },
                                onClearSiteDataMenuClick = {},
                            )
                        }

                        composable(route = TRACKERS_PANEL_ROUTE) {
                            TrackersBlockedPanel(
                                title = args.title,
                                onBackButtonClick = {
                                    navHostController.navigate(route = PROTECTION_PANEL_ROUTE)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
