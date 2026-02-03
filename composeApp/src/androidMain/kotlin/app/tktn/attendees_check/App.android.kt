package app.tktn.attendees_check

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.tktn.attendees_check.di.RootAppModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

class MyApp : Application(){
    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(RootAppModule().module)
			androidContext(this@MyApp)
        }
    }
}

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RootApp() }
    }
}
