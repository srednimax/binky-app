package app.binky.tracker

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.binky.tracker.ui.appViewModelExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one Compose test in Phase 1, against ADR-0012's rule that this UI is verified by hand: it
 * guards a wiring decision rather than a screen, and the screens can churn without touching it.
 *
 * The bug it exists for: with no ViewModel decorator on `NavDisplay`, `viewModel()` inside an entry
 * resolves to the **Activity's** store, so a ViewModel outlives the screen that made it. The bunny
 * editor came back with `saved` already true and popped itself before it could be used — every
 * "Add a bunny" after the first one in a session did nothing, and only killing the app fixed it.
 * Nothing in the JVM or Room suites can see that; it lives entirely in how the shell is assembled.
 */
@RunWith(AndroidJUnit4::class)
class NavigationScopingTest {
    @get:Rule val composeRule = createComposeRule()

    private data object Root : NavKey

    /** Argument-carrying, defaulted, like `BunnyEditor` — so every "add" is an equal key. */
    private data class Detail(
        val id: String? = null,
    ) : NavKey

    private class ProbeViewModel : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }

        companion object {
            val Factory: ViewModelProvider.Factory = viewModelFactory { initializer { ProbeViewModel() } }
        }
    }

    @Test
    fun poppingAnEntryClearsItsViewModelAndAnEqualKeyComesBackFresh() {
        val created = mutableListOf<ProbeViewModel>()
        lateinit var backStack: MutableList<NavKey>

        composeRule.setContent {
            val stack = remember { mutableStateListOf<NavKey>(Root) }
            backStack = stack
            NavDisplay(
                backStack = stack,
                entryDecorators = appEntryDecorators(),
                entryProvider =
                    entryProvider {
                        entry<Root> { Text("root") }
                        entry<Detail> {
                            val viewModel: ProbeViewModel = viewModel(factory = ProbeViewModel.Factory)
                            // DisposableEffect keyed on the instance runs once per *distinct*
                            // ViewModel, so recomposition cannot inflate the count.
                            DisposableEffect(viewModel) {
                                created += viewModel
                                onDispose {}
                            }
                            Text("detail")
                        }
                    },
            )
        }
        composeRule.waitForIdle()

        // Open the detail screen and leave it again — "add a bunny, save, come back".
        composeRule.runOnIdle { backStack.add(Detail()) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { backStack.removeAt(backStack.lastIndex) }
        composeRule.waitForIdle()

        // Open it a second time. The key is *equal* to the first one, exactly as `BunnyEditor()`
        // is equal to every other `BunnyEditor()` — which is what made the real bug reachable.
        composeRule.runOnIdle { backStack.add(Detail()) }
        composeRule.waitForIdle()

        assertEquals("each visit should build its own ViewModel", 2, created.size)
        assertTrue("the first visit's ViewModel should be cleared when its entry pops", created[0].cleared)
        assertNotSame("the second visit must not inherit the first visit's ViewModel", created[0], created[1])
    }

    @Test
    fun appViewModelExtrasCarriesTheApplicationEveryFactoryReads() {
        lateinit var extras: CreationExtras

        composeRule.setContent { extras = appViewModelExtras() }
        composeRule.waitForIdle()

        // A nav entry's ViewModelStoreOwner is bare — no Activity behind it, so no default extras.
        // Every factory in the app reaches AppContainer through this key; without it the cast in
        // the initializer throws the moment the screen opens.
        assertNotNull("APPLICATION_KEY must be supplied to factories inside a nav entry", extras[APPLICATION_KEY])
    }
}
