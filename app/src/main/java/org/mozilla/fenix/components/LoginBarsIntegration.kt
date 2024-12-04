/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import mozilla.components.feature.prompts.concept.ToggleablePrompt
import mozilla.components.feature.prompts.login.LoginSelectBar
import mozilla.components.feature.prompts.login.SuggestStrongPasswordBar
import org.mozilla.fenix.browser.LoginSelectBarBehavior
import org.mozilla.fenix.utils.Settings

/**
 * Helper for ensuring that
 * - login bars are always on top of the bottom toolbar
 * - callers are notified when login bars are shown/hidden.
 */
class LoginBarsIntegration(
    loginsBar: LoginSelectBar,
    passwordBar: SuggestStrongPasswordBar,
    private val settings: Settings,
    private val onLoginsBarShown: () -> Unit,
    private val onLoginsBarHidden: () -> Unit,
) {
    init {
        loginsBar.toggleablePromptListener = loginsBar.createToggleListener()
        passwordBar.toggleablePromptListener = passwordBar.createToggleListener()
    }

    var isVisible: Boolean = false
        private set

    private fun <T : View> T.createToggleListener() = object : ToggleablePrompt.Listener {
        override fun onShown() {
            behavior = LoginSelectBarBehavior<T>(
                context = context,
                toolbarPosition = settings.toolbarPosition,
            )
            isVisible = true
            onLoginsBarShown()
        }

        override fun onHidden() {
            // Remove the custom behavior to prevent layout evaluations while login bars are hidden
            behavior = null
            isVisible = false
            onLoginsBarHidden()
        }
    }

    private var View.behavior: CoordinatorLayout.Behavior<*>?
        get() = (layoutParams as? CoordinatorLayout.LayoutParams)?.behavior
        set(value) {
            (layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = value
        }
}
