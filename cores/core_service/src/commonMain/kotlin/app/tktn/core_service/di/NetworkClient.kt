package app.tktn.core_service.di

import io.ktor.client.HttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("app.tktn.core_service")
class NetworkClient {
	@Single
	fun getClient() : HttpClient = HttpClient {

	}
}