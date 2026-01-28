package app.tktn.attendees_check.di

import app.tktn.core_service.di.NetworkClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
	includes = [
		NetworkClient::class,
	]
)
@ComponentScan("app.tktn")
class RootAppModule {
}