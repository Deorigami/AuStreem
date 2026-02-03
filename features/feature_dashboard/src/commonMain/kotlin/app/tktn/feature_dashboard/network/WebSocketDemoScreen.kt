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
									if (isStreaming) {
										app.tktn.core_service.audio.PlatformAudioHook.stopService {
											audioService.stopStreaming()
										}
									} else {
										app.tktn.core_service.audio.PlatformAudioHook.startService {
											audioService.startStreaming()
										}
									}
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
							
							androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
							
							Text("Audio Output Control", style = MaterialTheme.typography.titleMedium)
							Spacer(modifier = Modifier.height(8.dp))
							
							val devices = remember { audioService.getAvailableDevices() }
							var activeMode by remember { mutableStateOf<String?>(null) } // "speaker" or "mic"
							var showDeviceList by remember { mutableStateOf(false) }
							var showSpeakerList by remember { mutableStateOf(false) }
							var isMonitoring by remember { mutableStateOf(false) }

							if (isReceiving) {
								// Active Stream State
								Card(
									colors = CardDefaults.cardColors(
										containerColor = if (activeMode == "mic") MaterialTheme.colorScheme.primaryContainer 
														else MaterialTheme.colorScheme.secondaryContainer
									),
									modifier = Modifier.fillMaxWidth()
								) {
									Column(modifier = Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
										Text(
											if (activeMode == "mic") "🎤 Broadcasting to Virtual Mic" else "🔊 Playing on Speakers",
											style = MaterialTheme.typography.titleMedium,
											fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
										)
										
										val currentDevice = remember(activeMode) { 
											if (activeMode == "mic") devices.find { it.contains("Cable", true) || it.contains("Virtual", true) }?.substringBefore("|") ?: "Unknown"
											else "System Default"
										}
										Text("Output: $currentDevice", style = MaterialTheme.typography.bodySmall)

										Spacer(modifier = Modifier.height(16.dp))
										
										Button(
											onClick = { 
												audioService.stopReceiving() 
												activeMode = null
											},
											colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
											modifier = Modifier.fillMaxWidth()
										) {
											Text("⏹ Stop Audio Stream")
										}
										
										if (activeMode == "mic") {
											Spacer(modifier = Modifier.height(16.dp))
											Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
												Text("Hear myself (Monitor)", style = MaterialTheme.typography.bodySmall)
												Spacer(modifier = Modifier.width(8.dp))
												androidx.compose.material3.Switch(
													checked = isMonitoring,
													onCheckedChange = { 
														isMonitoring = it
														audioService.setMonitoring(it)
													}
												)
											}
											Spacer(modifier = Modifier.height(8.dp))
											Text(
												"💡 In Windows/Discord, set Input Device to 'CABLE Output' or 'Virtual Mic'",
												style = MaterialTheme.typography.bodySmall,
												modifier = Modifier.padding(4.dp)
											)
										}
									}
								}
							} else {
								// Idle State - Choose Mode
								Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
									// Speaker Button
									Button(
										onClick = {
											showSpeakerList = !showSpeakerList
											showDeviceList = false // Close the other list
										},
										modifier = Modifier.weight(1f),
										colors = ButtonDefaults.buttonColors(
											containerColor = if (showSpeakerList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
											contentColor = if (showSpeakerList) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
										)
									) {
										Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
											Text("🔊 Speaker")
											Text("(Select Output)", style = MaterialTheme.typography.labelSmall)
										}
									}
									
									// Virtual Mic Button
									Button(
										onClick = {
											// Auto-detect Cable
											val cable = devices.find { it.contains("Cable", true) || it.contains("Virtual", true) || it.contains("AudioRelay", true) }
											
											if (cable != null) {
												audioService.setTargetDevice(cable)
												val host = connectedHost?.substringBefore(':') ?: clientHost
												audioService.startReceiving(host, DISCOVERY_PORT)
												activeMode = "mic"
											} else {
												// Fallback to manual selection
												showDeviceList = !showDeviceList
												showSpeakerList = false
											}
										},
										modifier = Modifier.weight(1f),
										colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
									) {
										Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
											Text("🎤 Virtual Mic")
											Text("(VB-Cable)", style = MaterialTheme.typography.labelSmall)
										}
									}
								}
								
								if (showSpeakerList) {
									Spacer(modifier = Modifier.height(8.dp))
									Text("Select Speaker Output:", style = MaterialTheme.typography.labelSmall)
									LazyColumn(modifier = Modifier.heightIn(max = 200.dp).fillMaxWidth()) {
										item {
											Button(
												onClick = {
													audioService.setTargetDevice(null)
													val host = connectedHost?.substringBefore(':') ?: clientHost
													audioService.startReceiving(host, DISCOVERY_PORT)
													activeMode = "speaker"
													showSpeakerList = false
												},
												modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
												colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
											) { Text("System Default Device") }
										}
										items(devices) { device ->
											Button(
												onClick = {
													audioService.setTargetDevice(device)
													val host = connectedHost?.substringBefore(':') ?: clientHost
													audioService.startReceiving(host, DISCOVERY_PORT)
													activeMode = "speaker"
													showSpeakerList = false
												},
												modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
											) { Text(device.take(40)) }
										}
									}
								}
								
								if (showDeviceList) {
									Spacer(modifier = Modifier.height(8.dp))
									Text("⚠️ Clean VB-CABLE not found. Select manually:", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
									LazyColumn(modifier = Modifier.heightIn(max = 200.dp).fillMaxWidth()) {
										items(devices) { device ->
											Button(
												onClick = {
													audioService.setTargetDevice(device)
													val host = connectedHost?.substringBefore(':') ?: clientHost
													audioService.startReceiving(host, DISCOVERY_PORT)
													activeMode = "mic"
													showDeviceList = false
												},
												modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
											) { Text(device.take(40)) }
										}
									}
								}
							}

							Spacer(modifier = Modifier.height(24.dp))
							Button(
								onClick = { 
									audioService.stopReceiving()
									scope.launch { client.disconnect() } 
								},
								modifier = Modifier.fillMaxWidth(),
								colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline)
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
