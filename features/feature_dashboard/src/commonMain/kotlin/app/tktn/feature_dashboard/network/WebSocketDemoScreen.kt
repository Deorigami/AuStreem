package app.tktn.feature_dashboard.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tktn.core_feature.base.BaseScreen
import app.tktn.core_service.network.DiscoveryService
import app.tktn.core_service.network.WebSocketClient
import app.tktn.core_service.network.WebSocketServer
import app.tktn.core_service.network.getLocalIpAddress
import app.tktn.core_service.network.DISCOVERY_PORT
import app.tktn.core_service.network.UDP_PORT
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object WebSocketDemoScreen : BaseScreen() {
	@Composable
	override fun ComposeContent() {
		val client = koinInject<WebSocketClient>()
		val server = koinInject<WebSocketServer>()
		val discovery = koinInject<DiscoveryService>()
		val scope = rememberCoroutineScope()

		var serverName by remember { mutableStateOf("Device ${(100..999).random()}") }
		var clientHost by remember { mutableStateOf("10.0.2.2") }
		var messageToSend by remember { mutableStateOf("") }

		val serverMessages by server.receivedMessages.collectAsState("")
		val clientMessages by client.messages.collectAsState("")
		val discoveredServers by discovery.discoveredServers.collectAsState()
		val isServerRunning by server.isRunning.collectAsState()

		val isClientConnected by client.isConnected.collectAsState()
		val connectedHost by client.connectedHost.collectAsState()

		val allMessages = remember { mutableStateListOf<String>() }
		val snackbarHostState = remember { SnackbarHostState() }

		LaunchedEffect(Unit) {
			discovery.startListening()
		}

		LaunchedEffect(serverMessages) {
			if (serverMessages.isNotEmpty()) allMessages.add("Server: $serverMessages")
		}
		LaunchedEffect(clientMessages) {
			if (clientMessages.isNotEmpty()) allMessages.add("Client: $clientMessages")
		}

		val filteredServers = discoveredServers.filter {
			val hostPort = "${it.host}:${it.port}"
			hostPort != connectedHost
		}

		Scaffold(
			snackbarHost = { SnackbarHost(snackbarHostState) }
		) { padding ->
			Column(
				modifier = Modifier.fillMaxSize().padding(padding)
					.verticalScroll(rememberScrollState())
					.padding(16.dp)
			) {
				Text(
					"WebSocket Demo",
					style = MaterialTheme.typography.headlineMedium
				)
				
				val myIp = remember { getLocalIpAddress() }
				if (myIp != null) {
					Text(
						"Your IP: $myIp",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.primary
					)
				}

				Spacer(modifier = Modifier.height(16.dp))

				// Discovered Servers
				if (!isServerRunning && filteredServers.isNotEmpty()) {
					Text(
						"Discovered Servers:",
						style = MaterialTheme.typography.titleMedium
					)
					Card(
						modifier = Modifier.fillMaxWidth()
							.padding(vertical = 8.dp),
						colors = CardDefaults.cardColors(
							containerColor = MaterialTheme.colorScheme.secondaryContainer
						)
					) {
						Column(modifier = Modifier.padding(16.dp)) {
							filteredServers.forEach { discovered ->
								Row(
									modifier = Modifier.fillMaxWidth()
										.padding(vertical = 4.dp),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
								) {
									Column {
										Text(
											discovered.name,
											style = MaterialTheme.typography.bodyLarge,
											fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
										)
										Text(
											"${discovered.host}:${discovered.port}",
											style = MaterialTheme.typography.bodySmall
										)
									}
									Button(
										enabled = !isClientConnected,
										onClick = {
											clientHost =
												discovered.host
											scope.launch {
												try {
													client.connect(
														clientHost,
														DISCOVERY_PORT
													)
												} catch (e: Exception) {
													snackbarHostState.showSnackbar(
														"Connection failed: ${e.message}"
													)
												}
											}
										}
									) {
										Text("Connect")
									}
								}
							}
						}
					}
				} else if (!isServerRunning && !isClientConnected) {
					// Networking Guide
					Card(
						modifier = Modifier.fillMaxWidth()
							.padding(vertical = 8.dp),
						colors = CardDefaults.cardColors(
							containerColor = MaterialTheme.colorScheme.surfaceVariant
						)
					) {
						Column(modifier = Modifier.padding(12.dp)) {
							Text(
								"🌐 Real Device Discovery",
								style = MaterialTheme.typography.labelLarge,
								color = MaterialTheme.colorScheme.primary
							)
							Text(
								"1. Ensure both devices are on the SAME Wi-Fi network.\n" +
								"2. Disable Windows Firewall or allow incoming port $UDP_PORT (UDP) and $DISCOVERY_PORT (TCP).\n" +
								"3. If discovery fails, enter the host's IP manually below.",
								style = MaterialTheme.typography.bodySmall
							)
						}
					}
				}

				// Server Controls
				Card(
					modifier = Modifier.fillMaxWidth()
						.padding(vertical = 8.dp)
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(
							"Server Settings",
							style = MaterialTheme.typography.titleMedium
						)

						if (!isServerRunning) {
							Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
								OutlinedTextField(
									value = serverName,
									onValueChange = { serverName = it },
									label = { Text("Server Name") },
									modifier = Modifier.weight(1f)
								)
								Spacer(modifier = Modifier.width(8.dp))
								Button(
									enabled = !isClientConnected,
									onClick = {
									server.start(
										discovery.selfId,
										serverName
									)
									discovery.startBroadcasting(
										serverName
									)
								}) {
									Text("Start")
								}
							}
							Text(
								"Server will run on fixed port $DISCOVERY_PORT",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.outline
							)
						} else {
							Column(modifier = Modifier.fillMaxWidth()) {
								Text(
									"Status: Playing as \"$serverName\"",
									color = MaterialTheme.colorScheme.primary
								)
								Text(
									"Port: $DISCOVERY_PORT",
									style = MaterialTheme.typography.bodySmall
								)
								Spacer(modifier = Modifier.height(8.dp))
								Button(
									onClick = {
										server.stop()
										discovery.stopBroadcasting()
									},
									colors = ButtonDefaults.buttonColors(
										containerColor = MaterialTheme.colorScheme.error
									),
									modifier = Modifier.fillMaxWidth()
								) {
									Text("Stop Server")
								}
							}
						}
					}
				}

				// Client Controls
				if (!isServerRunning) {
					Card(
						modifier = Modifier.fillMaxWidth()
							.padding(vertical = 8.dp)
					) {
						Column(modifier = Modifier.padding(16.dp)) {
							Text(
								"Manual Connection",
								style = MaterialTheme.typography.titleMedium
							)

							if (!isClientConnected) {
								OutlinedTextField(
									value = clientHost,
									onValueChange = {
										clientHost = it
									},
									label = { Text("Host (IP Address)") },
									modifier = Modifier.fillMaxWidth()
								)
								Spacer(modifier = Modifier.height(8.dp))
								Button(
									modifier = Modifier.fillMaxWidth(),
									onClick = {
										scope.launch {
											try {
												client.connect(
													clientHost,
													DISCOVERY_PORT
												)
											} catch (e: Exception) {
												snackbarHostState.showSnackbar(
													"Connection failed: ${e.message}"
												)
											}
										}
									}
								) {
									Text("Connect")
								}
							} else {
								Column(modifier = Modifier.fillMaxWidth()) {
									Text(
										"Status: Connected to $connectedHost",
										color = MaterialTheme.colorScheme.primary
									)
									Spacer(modifier = Modifier.height(8.dp))
									Button(
										onClick = { scope.launch { client.disconnect() } },
										colors = ButtonDefaults.buttonColors(
											containerColor = MaterialTheme.colorScheme.error
										),
										modifier = Modifier.fillMaxWidth()
									) {
										Text("Disconnect")
									}
								}
							}
						}
					}
				}

				// Messaging
				Row(
					modifier = Modifier.fillMaxWidth()
						.padding(vertical = 8.dp)
				) {
					OutlinedTextField(
						value = messageToSend,
						onValueChange = { messageToSend = it },
						label = { Text("Message") },
						modifier = Modifier.weight(1f)
					)
					Spacer(modifier = Modifier.width(8.dp))
					Button(onClick = {
						scope.launch {
							client.sendMessage(messageToSend)
							messageToSend = ""
						}
					}) {
						Text("Send")
					}
				}

				// Message List
				Text(
					"Messages:",
					style = MaterialTheme.typography.titleSmall
				)
				LazyColumn(
					modifier = Modifier.weight(1f).fillMaxWidth()
				) {
					items(allMessages) { msg ->
						Text(
							msg,
							modifier = Modifier.padding(vertical = 2.dp)
						)
					}
				}
			}
		}
	}
}
