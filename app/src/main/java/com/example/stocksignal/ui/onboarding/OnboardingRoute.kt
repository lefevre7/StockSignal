package com.example.stocksignal.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import com.example.stocksignal.data.settings.HoldingPeriod
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stocksignal.util.ExactAlarmPermission

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedHoldingPeriod by rememberSaveable { mutableStateOf(HoldingPeriod.MONTHS) }
    val highlights = remember { onboardingHighlights() }
    val modelDownloadState by viewModel.modelDownloadState.collectAsStateWithLifecycle()
    val modelAlreadyAvailable = remember { viewModel.isModelAlreadyAvailable() }
    val totalSteps = if (modelAlreadyAvailable) 3 else 4
    val maxStepIndex = totalSteps - 1
    val currentStep = stepIndex.coerceIn(0, maxStepIndex)
    val isLastStep = currentStep == maxStepIndex

    LaunchedEffect(stepIndex, maxStepIndex) {
        if (stepIndex > maxStepIndex) {
            stepIndex = maxStepIndex
        }
    }

    val permissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var permissionGranted by remember {
        mutableStateOf(
            !permissionRequired ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    var exactAlarmAllowed by remember {
        mutableStateOf(ExactAlarmPermission.isAllowed(context))
    }
    val exactAlarmIntent = ExactAlarmPermission.requestIntent()
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        exactAlarmAllowed = ExactAlarmPermission.isAllowed(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Welcome to StockSignal",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (currentStep) {
                0 -> {
                    highlights.forEachIndexed { index, item ->
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.body,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (index != highlights.lastIndex) {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
                1 -> {
                    if (!modelAlreadyAvailable) {
                        Text(
                            text = "AI Model Download",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "StockSignal uses an on-device AI model (Gemma 3 1B) to generate intelligent signal scores. This ensures your data stays private and works offline.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Download size: ~584 MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This download may take a few minutes depending on your connection speed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        when {
                            modelDownloadState.isDownloading -> {
                                LinearProgressIndicator(
                                    progress = { modelDownloadState.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Downloading: ${modelDownloadState.progress}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            modelDownloadState.isComplete -> {
                                Text(
                                    text = "✓ Download complete!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            modelDownloadState.error != null -> {
                                val errorMsg = modelDownloadState.error ?: "Unknown error"
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.retryDownload() }) {
                                    Text("Retry Download")
                                }
                            }
                            else -> {
                                Button(onClick = { viewModel.downloadModel() }) {
                                    Text("Start Download")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { stepIndex++ }) {
                                    Text("Skip for now")
                                }
                            }
                        }
                    } else {
                        // If model exists, adjust step content
                        stepIndex++
                    }
                }
                (if (modelAlreadyAvailable) 1 else 2) -> {
                    Text(
                        text = "Investment Timeframe",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "How long do you typically hold positions? This helps optimize signals and indicators for your trading style.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.selectableGroup()
                    ) {
                        HoldingPeriod.entries.forEach { period ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (selectedHoldingPeriod == period),
                                        onClick = { selectedHoldingPeriod = period },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedHoldingPeriod == period),
                                    onClick = null
                                )
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(
                                        text = period.displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = period.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                (if (modelAlreadyAvailable) 2 else 3) -> {
                    Text(
                        text = "Disclaimers",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Signals are informational, not investment advice. We do not execute trades.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Market data is sourced from Stooq.com and may be delayed, incomplete, or inaccurate. The app does not guarantee the accuracy, completeness, or timeliness of any data. Not affiliated with Stooq.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (permissionRequired && !permissionGranted) {
                            "Enable notifications to receive scheduled signal updates."
                        } else {
                            "Notifications are enabled."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (permissionRequired && !permissionGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) {
                            Text("Enable notifications")
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Exact alarms",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (!exactAlarmAllowed && exactAlarmIntent != null) {
                            "Enable exact alarms to keep notification timing accurate."
                        } else {
                            "Exact alarms are enabled."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!exactAlarmAllowed && exactAlarmIntent != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { exactAlarmLauncher.launch(exactAlarmIntent) }) {
                            Text("Enable alarms")
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            if (currentStep > 0) {
                TextButton(
                    onClick = { stepIndex = (currentStep - 1).coerceAtLeast(0) },
                    enabled = !modelDownloadState.isDownloading
                ) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.height(0.dp))
            }

            if (isLastStep) {
                Button(
                    onClick = {
                        viewModel.completeOnboarding(selectedHoldingPeriod)
                        onFinished()
                    }
                ) {
                    Text("Get started")
                }
            } else {
                val isDownloadStep = !modelAlreadyAvailable && currentStep == 1
                val canProceed = !isDownloadStep || 
                    modelDownloadState.isComplete || 
                    modelDownloadState.error != null
                
                Button(
                    onClick = { stepIndex = (currentStep + 1).coerceAtMost(maxStepIndex) },
                    enabled = canProceed && !modelDownloadState.isDownloading
                ) {
                    Text("Next")
                }
            }
        }
    }
}

private data class OnboardingHighlight(
    val title: String,
    val body: String
)

private fun onboardingHighlights(): List<OnboardingHighlight> {
    return listOf(
        OnboardingHighlight(
            title = "Clear signals",
            body = "Track buy/sell signals with explanations and confidence scores."
        ),
        OnboardingHighlight(
            title = "Watchlist and movers",
            body = "Follow your watchlist and the biggest market movers in one place."
        ),
        OnboardingHighlight(
            title = "Local and private",
            body = "Signals are generated locally and stored on device only."
        )
    )
}
