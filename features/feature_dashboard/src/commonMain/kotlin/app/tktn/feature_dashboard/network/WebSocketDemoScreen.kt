package app.tktn.feature_dashboard.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
		val audioService = koinInject<app.tktn.core_service.audio.AudioStreamingService>()
		val scope = rememberCoroutineScope()

		var serverName by remember { mutableStateOf("Device ${(100..999).random()}") }
		var clientHost by remember { mutableStateOf("10.0.2.2") }
		var messageToSend by remember { mutableStateOf("") }

		val serverMessages by server.receivedMessages.collectAsState("")
		val clientMessages by client.messages.collectAsState("")
		val discoveredServers by discovery.discoveredServers.collectAsState()
		val isServerRunning by server.isRunning.collectAsState()
		val connectedClients by server.connectedClients.collectAsState()

		val isClientConnected by client.isConnected.collectAsState()
		val connectedHost by client.connectedHost.collectAsState()
		
		val isStreaming by audioService.isStreaming.collectAsState()
		val isReceiving by audioService.isReceiving.collectAsState()
		val isAudioConnected by client.isAudioConnected.collectAsState()

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
					"Audio Relay Demo",
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

				// Server Section (Android/Source)
				Card(
					modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
					colors = CardDefaults.cardColors(
						containerColor = if (isServerRunning) MaterialTheme.colorScheme.primaryContainer 
										else MaterialTheme.colorScheme.surface
					)
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text("Server Mode (Microphone Source)", style = MaterialTheme.typography.titleMedium)
						
						if (!isServerRunning) {
							Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
								OutlinedTextField(
									value = serverName,
									onValueChange = { serverName = it },
									label = { Text("Device Name") },
									modifier = Modifier.weight(1f)
								)
								Spacer(modifier = Modifier.width(8.dp))
								Button(onClick = {
									server.start(discovery.selfId, serverName)
									discovery.startBroadcasting(serverName)
								}) {
									Text("Start")
								}
							}
						} else {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
							) {
								Column {
									Text("Playing as: $serverName", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
									Text("Connected Clients: $connectedClients", style = MaterialTheme.typography.bodySmall)
								}
								Button(
									onClick = {
										audioService.stopStreaming()
										server.stop()
										discovery.stopBroadcasting()
									},
									colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
								) {
									Text("Stop Server")
								}
							}
							
							Spacer(modifier = Modifier.height(12.dp))
							
							// Audio Streaming Toggle
							Button(
								onClick = {
									if (isStreaming) audioService.stopStreaming()
									else audioService.startStreaming()
								},
								modifier = Modifier.fillMaxWidth(),
								colors = ButtonDefaults.buttonColors(
									containerColor = if (isStreaming) MaterialTheme.colorScheme.error 
													else MaterialTheme.colorScheme.secondary
								)
							) {
								Text(if (isStreaming) "🛑 Stop Mic Stream" else "🎙️ Start Mic Stream")
							}
						}
					}
				}

				// Client Section (Windows/Sink)
				Card(
					modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
					colors = CardDefaults.cardColors(
						containerColor = if (isClientConnected) MaterialTheme.colorScheme.secondaryContainer 
										else MaterialTheme.colorScheme.surface
					)
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text("Client Mode (Audio Receiver)", style = MaterialTheme.typography.titleMedium)
						
						if (!isClientConnected) {
							if (isServerRunning.not()) {
								Text("Discovered Devices:", style = MaterialTheme.typography.labelSmall)
								filteredServers.forEach { discovered ->
									Button(
										onClick = {
											clientHost = discovered.host
											scope.launch {
												client.connect(clientHost, DISCOVERY_PORT)
											}
										},
										modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
									) {
										Text("Connect to ${discovered.name} (${discovered.host})")
									}
								}
							}
							
							Spacer(modifier = Modifier.height(8.dp))
							OutlinedTextField(
								value = clientHost,
								onValueChange = { clientHost = it },
								label = { Text("Manual IP") },
								modifier = Modifier.fillMaxWidth()
							)
							Button(
								onClick = {
									scope.launch {
										client.connect(clientHost, DISCOVERY_PORT)
									}
								},
								modifier = Modifier.fillMaxWidth()
							) {
								Text("Connect Manually")
							}
						} else {
							Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
								Text("Connected: $connectedHost", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
								Text(
									if (isAudioConnected) "🟢 Audio Link: Active" else "🔴 Audio Link: None",
									style = MaterialTheme.typography.labelSmall,
									color = if (isAudioConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
								)
							}
							Spacer(modifier = Modifier.height(16.dp))
							
							// Audio Routing Mode Selection
							var routingMode by remember { mutableStateOf("speaker") } // "speaker" or "mic"
							val devices = remember { audioService.getAvailableDevices() }
							var selectedDevice by remember { mutableStateOf<String?>(null) }
							var showDeviceDialog by remember { mutableStateOf(false) }

							Text("Routing Mode:", style = MaterialTheme.typography.labelSmall)
							Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
								Button(
									onClick = { 
										routingMode = "speaker" 
										selectedDevice = null 
										audioService.setTargetDevice(null)
									},
									modifier = Modifier.weight(1f),
									colors = ButtonDefaults.buttonColors(
										containerColor = if (routingMode == "speaker") MaterialTheme.colorScheme.primary 
														else MaterialTheme.colorScheme.surfaceVariant,
										contentColor = if (routingMode == "speaker") MaterialTheme.colorScheme.onPrimary 
														else MaterialTheme.colorScheme.onSurfaceVariant
									)
								) {
									Text("🔊 Speaker")
								}
								Spacer(modifier = Modifier.width(8.dp))
								Button(
									onClick = { 
										routingMode = "mic"
										// Auto-select cable if available
										val cable = devices.find { it.contains("Cable", ignoreCase = true) || it.contains("Virtual", ignoreCase = true) }
										if (cable != null) {
											selectedDevice = cable
											audioService.setTargetDevice(cable)
										} else {
											showDeviceDialog = true
										}
									},
									modifier = Modifier.weight(1f),
									colors = ButtonDefaults.buttonColors(
										containerColor = if (routingMode == "mic") MaterialTheme.colorScheme.primary 
														else MaterialTheme.colorScheme.surfaceVariant,
										contentColor = if (routingMode == "mic") MaterialTheme.colorScheme.onPrimary 
														else MaterialTheme.colorScheme.onSurfaceVariant
									)
								) {
									Text("🎤 Virtual Mic")
								}
							}

							if (routingMode == "mic") {
								Card(
									modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
									colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
								) {
									Column(modifier = Modifier.padding(12.dp)) {
										Text("1. Feed the Virtual Cable", style = MaterialTheme.typography.titleSmall)
										Text(
											"The app must 'play' your voice into the Virtual Cable's input to feed the microphone.",
											style = MaterialTheme.typography.bodySmall
										)
										
										Spacer(modifier = Modifier.height(8.dp))
										Text("Selected Input Bridge:", style = MaterialTheme.typography.labelSmall)
										Text(
											selectedDevice ?: "No Virtual Cable Selected", 
											fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
											color = if (selectedDevice == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
										)
										
										Button(
											onClick = { showDeviceDialog = true },
											modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
											colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
										) {
											Text(if (selectedDevice == null) "Select Virtual Cable" else "Change Virtual Cable")
										}

										Spacer(modifier = Modifier.height(12.dp))
										androidx.compose.material3.HorizontalDivider()
										Spacer(modifier = Modifier.height(12.dp))

										Text("2. Windows Configuration", style = MaterialTheme.typography.titleSmall)
										Text(
											"Now, in Windows or Discord, select 'CABLE Output' as your Microphone.",
											style = MaterialTheme.typography.bodySmall
										)
										
										if (selectedDevice == null) {
											Text(
												"⚠️ Note: You need VB-CABLE or similar installed to see this option.",
												style = MaterialTheme.typography.bodySmall,
												color = MaterialTheme.colorScheme.error,
												modifier = Modifier.padding(top = 4.dp)
											)
										}
									}
								}
							} else {
								Card(
									modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
									colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
								) {
									Column(modifier = Modifier.padding(12.dp)) {
										Text("🔊 Speaker Monitoring", style = MaterialTheme.typography.titleSmall)
										Text(
											"Voice will play directly through your chosen PC speaker.",
											style = MaterialTheme.typography.bodySmall
										)
										
										Spacer(modifier = Modifier.height(8.dp))
										Text("Active Speaker:", style = MaterialTheme.typography.labelSmall)
										Text(
											selectedDevice ?: "System Default Output", 
											fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
										)
										
										Button(
											onClick = { showDeviceDialog = true },
											modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
											colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
										) {
											Text("Change Speaker")
										}
									}
								}
							}

							if (showDeviceDialog) {
								androidx.compose.ui.window.Dialog(onDismissRequest = { showDeviceDialog = false }) {
									Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
										Column(modifier = Modifier.padding(16.dp)) {
											Text("Select Virtual/Output Device", style = MaterialTheme.typography.titleMedium)
											Spacer(modifier = Modifier.height(8.dp))
											LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
												items(devices) { device ->
													Button(
														onClick = { 
															selectedDevice = device
															audioService.setTargetDevice(device)
															showDeviceDialog = false 
														},
														modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
													) { Text(device) }
												}
											}
										}
									}
								}
							}

							Spacer(modifier = Modifier.height(16.dp))
							
							// Audio Receiving Toggle
							Button(
								onClick = {
									if (isReceiving) audioService.stopReceiving()
									else {
										val host = connectedHost?.substringBefore(':') ?: clientHost
										audioService.startReceiving(host, DISCOVERY_PORT)
									}
								},
								modifier = Modifier.fillMaxWidth(),
								colors = ButtonDefaults.buttonColors(
									containerColor = if (isReceiving) MaterialTheme.colorScheme.error 
													else MaterialTheme.colorScheme.primary
								)
							) {
								val icon = if (routingMode == "mic") "🎤" else "🔊"
								Text(if (isReceiving) "� Stop Stream" else "$icon Start Receiving")
							}
							
							Spacer(modifier = Modifier.height(8.dp))
							Button(
								onClick = { 
									audioService.stopReceiving()
									scope.launch { client.disconnect() } 
								},
								modifier = Modifier.fillMaxWidth(),
								colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
							) {
								Text("Disconnect")
							}
						}
					}
				}

				// Messaging Section (Chat Demo)
				if (isClientConnected || isServerRunning) {
					Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
						Column(modifier = Modifier.padding(16.dp)) {
							Text("Chat / Control Messages", style = MaterialTheme.typography.labelSmall)
							Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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
							
							Text("History:", style = MaterialTheme.typography.labelSmall)
							allMessages.forEach { msg ->
								Text(msg, style = MaterialTheme.typography.bodySmall)
							}
						}
					}
				}
			}
		}
	}
}
