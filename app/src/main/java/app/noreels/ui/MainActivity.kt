package app.noreels.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.noreels.NoReelsApplication
import app.noreels.ui.theme.NoReelsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer { HomeViewModel((application as NoReelsApplication).container, applicationContext) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoReelsTheme {
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(state, viewModel::setSurfaceEnabled)
            }
        }
    }
}
