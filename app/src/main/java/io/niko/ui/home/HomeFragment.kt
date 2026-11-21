package io.niko.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import io.niko.ui.MainActivity
import io.niko.ui.compose.NikoTheme

/**
 * The main screen (D-MAIN, D-NAV), the first the app opens: [HomeScreen] over the activity's state. The activity keeps
 * the state (the service's callbacks and the database come to it) and does what the screen asks.
 */
class HomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val main = requireActivity() as MainActivity
            setContent {
                // the configs' sheet's test is the app's: it outlives this screen and the activity
                val testing by ConfigsTest.testing.collectAsState()
                val results by ConfigsTest.results.collectAsState()
                val stored by ConfigsTest.stored.collectAsState()
                LaunchedEffect(stored) { if (stored > 0) main.refreshHome() }
                NikoTheme {
                    HomeScreen(
                        main.homeState.value, main.homeView.value, main.homeActions,
                        configs = main.configsState.value, testing = testing, results = results,
                        configsActions = main.configsActions,
                    )
                }
            }
        }

    // back from another screen, or the app back to the front: profiles, rules or pings may have changed meanwhile
    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).refreshHome()
    }
}
