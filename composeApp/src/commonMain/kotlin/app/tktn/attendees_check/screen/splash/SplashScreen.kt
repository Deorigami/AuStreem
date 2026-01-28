package app.tktn.attendees_check.screen.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import app.tktn.core_feature.base.BaseScreen
import app.tktn.feature_util.rememberScreenConfiguration
import co.touchlab.kermit.Logger
import kotlinx.serialization.Serializable

@Serializable
object SplashScreen : BaseScreen() {

    @Composable
    override fun ComposeContent() {
        val screenConfiguration = rememberScreenConfiguration()
        LaunchedEffect(
            screenConfiguration
        ) {
            Logger.d("SCREEN_ORIENTATION") { screenConfiguration.toString() }
        }
        Column(
            modifier = Modifier.fillMaxSize().background(Color.White)
        ) {

        }
    }
}

