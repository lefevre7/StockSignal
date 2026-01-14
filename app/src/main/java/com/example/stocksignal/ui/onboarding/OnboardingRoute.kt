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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedHoldingPeriod by rememberSaveable { mutableStateOf(HoldingPeriod.MONTHS) }
    val highlights = remember { onboardingHighlights() }
    val totalSteps = 3
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
                2 -> {
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
                }
            }
        }

        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            if (currentStep > 0) {
                TextButton(onClick = { stepIndex = (currentStep - 1).coerceAtLeast(0) }) {
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
                Button(onClick = { stepIndex = (currentStep + 1).coerceAtMost(maxStepIndex) }) {
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
