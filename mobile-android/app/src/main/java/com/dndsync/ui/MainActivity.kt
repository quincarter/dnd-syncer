package com.dndsync.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.dndsync.DndSyncApplication
import com.dndsync.model.DndMode
import com.dndsync.service.DndNotificationListenerService
import com.dndsync.service.DndSyncForegroundService
import com.dndsync.ui.theme.DndSyncerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start background foreground service
        DndSyncForegroundService.startService(this)

        setContent {
            DndSyncerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var hasNotifListenerPermission by remember { mutableStateOf(checkNotificationListenerPermission(context)) }
    var hasDndPermission by remember { mutableStateOf(checkDndPermission(context)) }
    var hasBatteryExemption by remember { mutableStateOf(checkBatteryOptimization(context)) }

    var pairingPinInput by remember { mutableStateOf("") }
    var manualIpInput by remember {
        mutableStateOf(DndSyncApplication.instance.prefs.getString("last_host", "") ?: "")
    }
    var showManualConnectDialog by remember { mutableStateOf(false) }

    val allPermissionsGranted = hasNotifListenerPermission && hasDndPermission
    var permissionsExpanded by remember { mutableStateOf(!allPermissionsGranted) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Automatically re-check permissions when returning to the app from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotifListenerPermission = checkNotificationListenerPermission(context)
                hasDndPermission = checkDndPermission(context)
                hasBatteryExemption = checkBatteryOptimization(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val connectionState by (DndSyncForegroundService.instance?.connectionState
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(DndSyncForegroundService.ConnectionState.Disconnected) }
    ).collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // App Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF6366F1), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "DND Syncer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Android Companion",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Permissions Section Header with Accordion / Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { permissionsExpanded = !permissionsExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allPermissionsGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PERMISSIONS GRANTED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        letterSpacing = 1.sp
                    )
                } else {
                    Text(
                        text = "SETUP REQUIRED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF818CF8),
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (permissionsExpanded) "Hide" else "Manage / Revoke",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (permissionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (permissionsExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = permissionsExpanded) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))

                // 1. Notification Listener Permission Card
                PermissionCard(
                    title = "Notification Access",
                    description = if (hasNotifListenerPermission) "Granted · Tap to manage or revoke in Android Settings." else "Required to mirror notifications to your desktop.",
                    isGranted = hasNotifListenerPermission,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(fallback)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. DND Policy Access Card
                PermissionCard(
                    title = "Do Not Disturb Access",
                    description = if (hasDndPermission) "Granted · Tap to manage or revoke in Android Settings." else "Required to read and change focus mode / DND.",
                    isGranted = hasDndPermission,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(fallback)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Battery Optimization
                PermissionCard(
                    title = "Ignore Battery Optimizations",
                    description = if (hasBatteryExemption) "Granted · App is exempt from Android background sleep." else "Allows syncing seamlessly in the background.",
                    isGranted = hasBatteryExemption,
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = if (!hasBatteryExemption) {
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                } else {
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(fallback)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Sync Status Card
        val (statusDotColor, statusTitle, statusSubtitle) = when (val state = connectionState) {
            is DndSyncForegroundService.ConnectionState.Paired -> Triple(
                Color(0xFF10B981),
                "Paired & Synced",
                "Connected with ${state.desktopName}. Focus and notifications are actively synchronized."
            )
            is DndSyncForegroundService.ConnectionState.Connected -> Triple(
                Color(0xFF3B82F6),
                "Connected to Desktop",
                "WebSocket connection active. Enter the 6-digit PIN from desktop to complete pairing."
            )
            is DndSyncForegroundService.ConnectionState.Connecting -> Triple(
                Color(0xFFF59E0B),
                "Connecting to Desktop...",
                "Attempting connection to ${state.target}..."
            )
            is DndSyncForegroundService.ConnectionState.Searching -> Triple(
                Color(0xFFF59E0B),
                "Searching for Desktop on Wi-Fi",
                "Discovering via UDP broadcast. If searching takes long, tap 'Connect to Desktop IP Manually' below."
            )
            is DndSyncForegroundService.ConnectionState.Error -> Triple(
                Color(0xFFEF4444),
                "Connection Failed",
                "${state.message}. Check your desktop IP and ensure DND Syncer desktop app is running."
            )
            is DndSyncForegroundService.ConnectionState.Disconnected -> Triple(
                Color(0xFF64748B),
                "Disconnected from Desktop",
                "Open the desktop app on the same Wi-Fi, or enter your desktop IP address below to connect."
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(statusDotColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                )

                if (connectionState !is DndSyncForegroundService.ConnectionState.Paired) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Enter PIN to pair
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = pairingPinInput,
                            onValueChange = { if (it.length <= 6) pairingPinInput = it.filter { c -> c.isDigit() } },
                            label = { Text("6-Digit PIN from Desktop") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (pairingPinInput.isNotBlank()) {
                                    val intent = Intent(context, DndSyncForegroundService::class.java).apply {
                                        action = DndSyncForegroundService.ACTION_PAIR_PIN
                                        putExtra("pin", pairingPinInput.trim())
                                    }
                                    context.startService(intent)
                                    android.widget.Toast.makeText(context, "Sending pairing PIN...", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Text("Pair")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Quick Controls
        Text(
            text = "QUICK ACTIONS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF818CF8),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Toggle DND button
        Button(
            onClick = {
                val listener = DndNotificationListenerService.instance
                if (listener != null) {
                    val isDnd = listener.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                    listener.setDnd(!isDnd, DndMode.PRIORITY_ONLY)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
        ) {
            Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFF818CF8))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Toggle Do Not Disturb Mode", color = Color.White)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Manual IP Connect Button
        OutlinedButton(
            onClick = { showManualConnectDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.Computer, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Connect to Desktop IP Manually")
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // Manual Connect Dialog
    if (showManualConnectDialog) {
        AlertDialog(
            onDismissRequest = { showManualConnectDialog = false },
            title = { Text("Connect by Desktop IP") },
            text = {
                Column {
                    Text(
                        "Enter the local IP address of your Mac shown in the desktop pairing modal (e.g. 192.168.1.50):",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        placeholder = { Text("192.168.1.50") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualIpInput.isNotBlank()) {
                            val intent = Intent(context, DndSyncForegroundService::class.java).apply {
                                action = DndSyncForegroundService.ACTION_CONNECT_MANUAL
                                putExtra("host", manualIpInput.trim())
                            }
                            context.startService(intent)
                            android.widget.Toast.makeText(context, "Connecting to ${manualIpInput.trim()}...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        showManualConnectDialog = false
                    }
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualConnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8)
                    )
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Granted",
                        color = Color(0xFF10B981),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Manage in Settings",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("Grant", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun checkNotificationListenerPermission(context: Context): Boolean {
    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    return enabledListeners.contains(context.packageName)
}

private fun checkDndPermission(context: Context): Boolean {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    // In Android, granting NotificationListenerService implicitly grants DND policy access
    return notificationManager.isNotificationPolicyAccessGranted || checkNotificationListenerPermission(context)
}

private fun checkBatteryOptimization(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}
