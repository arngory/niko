package io.niko.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
                NikoTheme {
                    HomeScreen(main.homeState.value, main.homeView.value, main.homeActions)
                }
            }
        }

    // back from another screen, or the app back to the front: profiles, rules or pings may have changed meanwhile
    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).refreshHome()
    }
}
