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
import androidx.compose.foundation.background
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
    var manualIpInput by remember { mutableStateOf("") }
    var showManualConnectDialog by remember { mutableStateOf(false) }

    val allPermissionsGranted = hasNotifListenerPermission && hasDndPermission

    // Check permissions periodically on resume
    DisposableEffect(Unit) {
        hasNotifListenerPermission = checkNotificationListenerPermission(context)
        hasDndPermission = checkDndPermission(context)
        hasBatteryExemption = checkBatteryOptimization(context)
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(28.dp))

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

        // Required Permissions Section
        if (!allPermissionsGranted) {
            Text(
                text = "SETUP REQUIRED",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF818CF8),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Notification Listener Permission Card
            PermissionCard(
                title = "Notification Access",
                description = "Required to mirror notifications to your desktop.",
                isGranted = hasNotifListenerPermission,
                onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. DND Policy Access Card
            PermissionCard(
                title = "Do Not Disturb Access",
                description = "Required to read and change focus mode / DND.",
                isGranted = hasDndPermission,
                onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Battery Optimization
            if (!hasBatteryExemption) {
                PermissionCard(
                    title = "Ignore Battery Optimizations",
                    description = "Allows syncing seamlessly in the background.",
                    isGranted = hasBatteryExemption,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Live Sync Status Card
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
                                .background(
                                    if (allPermissionsGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (allPermissionsGranted) "Background Sync Active" else "Pending Permissions",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Automatically discovering Desktop clients on your Wi-Fi network via UDP broadcast.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Enter PIN to pair
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = pairingPinInput,
                        onValueChange = { if (it.length <= 6) pairingPinInput = it },
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
                                    putExtra("pin", pairingPinInput)
                                }
                                context.startService(intent)
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
                        "Enter the local IP address of your desktop computer (e.g. 192.168.1.50):",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true
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
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(24.dp)
                )
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
    return notificationManager.isNotificationPolicyAccessGranted
}

private fun checkBatteryOptimization(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}
