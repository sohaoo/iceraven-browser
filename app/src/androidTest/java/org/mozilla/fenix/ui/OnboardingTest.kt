package org.mozilla.fenix.ui

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mozilla.fenix.customannotations.SmokeTest
import org.mozilla.fenix.helpers.AppAndSystemHelper.runWithLauncherIntent
import org.mozilla.fenix.helpers.HomeActivityIntentTestRule
import org.mozilla.fenix.helpers.TestSetup
import org.mozilla.fenix.ui.robots.homeScreen

@Ignore("Disabled:https://bugzilla.mozilla.org/show_bug.cgi?id=1894664")
class OnboardingTest : TestSetup() {

    @get:Rule
    val activityTestRule =
        AndroidComposeTestRule(
            HomeActivityIntentTestRule.withDefaultSettingsOverrides(launchActivity = false),
        ) { it.activity }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/2122321
    @Test
    fun verifyFirstOnboardingCardItemsTest() {
        runWithLauncherIntent(activityTestRule) {
            homeScreen {
                verifyFirstOnboardingCard(activityTestRule)
            }
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/2122334
    @SmokeTest
    @Test
    fun verifyFirstOnboardingCardItemsFunctionalityTest() {
        runWithLauncherIntent(activityTestRule) {
            homeScreen {
                clickDefaultCardNotNowOnboardingButton(activityTestRule)
                verifySecondOnboardingCard(activityTestRule)
                swipeSecondOnboardingCardToRight()
            }.clickSetAsDefaultBrowserOnboardingButton(activityTestRule) {
                verifyAndroidDefaultAppsMenuAppears()
            }.goBackToOnboardingScreen {
                verifySecondOnboardingCard(activityTestRule)
            }
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/2122343
    @Test
    fun verifySecondOnboardingCardItemsTest() {
        runWithLauncherIntent(activityTestRule) {
            homeScreen {
                clickDefaultCardNotNowOnboardingButton(activityTestRule)
                verifySecondOnboardingCard(activityTestRule)
            }
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/2122344
    @SmokeTest
    @Test
    fun verifyThirdOnboardingCardSignInFunctionalityTest() {
        runWithLauncherIntent(activityTestRule) {
            homeScreen {
                clickDefaultCardNotNowOnboardingButton(activityTestRule)
                verifySecondOnboardingCard(activityTestRule)
                clickAddSearchWidgetNotNowOnboardingButton(activityTestRule)
                verifyThirdOnboardingCard(activityTestRule)
            }.clickSignInOnboardingButton(activityTestRule) {
                verifyTurnOnSyncMenu()
            }
        }
    }
}
