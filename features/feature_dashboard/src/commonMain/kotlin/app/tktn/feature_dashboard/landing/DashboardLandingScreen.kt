package app.tktn.feature_dashboard.landing

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import app.tktn.core_feature.base.BaseScreen
import app.tktn.core_feature.navigation.LocalNavStack
import kotlinx.serialization.Serializable

@Serializable
object DashboardLandingScreen : BaseScreen() {

    @Composable
    override fun ComposeContent() {
        val navStack = LocalNavStack.current
        Column(modifier = Modifier.fillMaxSize()) {
            Text("Dashboard")
            Button(onClick = {  }) {
                Text("Go to WebSocket Demo")
            }
        }
    }
}
