package com.librivox.mobile.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookHostNavigationPolicyTest {
    @Test
    fun topLevelNavigation_doesNotSaveOutgoingStateWhenReturningHome() {
        assertFalse(
            shouldSaveTopLevelBackStackState(
                targetRoute = Routes.HOME,
                currentRoute = Routes.LIBRARY,
            ),
        )
    }

    @Test
    fun topLevelNavigation_doesNotRestoreSavedStateForHome() {
        assertFalse(
            shouldRestoreTopLevelBackStackState(
                targetRoute = Routes.HOME,
                reselectingCurrentTopRoute = false,
            ),
        )
    }

    @Test
    fun topLevelNavigation_keepsSavedStateForOtherTopLevelTabs() {
        assertTrue(
            shouldSaveTopLevelBackStackState(
                targetRoute = Routes.LIBRARY,
                currentRoute = Routes.DISCOVER,
            ),
        )
        assertTrue(
            shouldRestoreTopLevelBackStackState(
                targetRoute = Routes.LIBRARY,
                reselectingCurrentTopRoute = false,
            ),
        )
    }

    @Test
    fun topLevelNavigation_doesNotSaveBookDetailWhenLeavingForATab() {
        assertFalse(
            shouldSaveTopLevelBackStackState(
                targetRoute = Routes.LIBRARY,
                currentRoute = Routes.bookDetail("wolnelektury-prus-anielka"),
            ),
        )
    }
}
