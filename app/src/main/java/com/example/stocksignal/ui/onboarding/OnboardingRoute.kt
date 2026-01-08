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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    val steps = remember { onboardingSteps() }
    val isLastStep = stepIndex == steps.lastIndex

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
            Text(
                text = steps[stepIndex].title,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = steps[stepIndex].body,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))
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
                text = "Powered by Stooq.com data. Not affiliated with Stooq.",
                style = MaterialTheme.typography.bodySmall
            )

            if (permissionRequired) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (permissionGranted) {
                        "Notifications are enabled."
                    } else {
                        "Enable notifications to receive scheduled signal updates."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!permissionGranted) {
                    Button(onClick = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text("Enable notifications")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            if (stepIndex > 0) {
                TextButton(onClick = { stepIndex -= 1 }) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.height(0.dp))
            }

            if (isLastStep) {
                Button(
                    onClick = {
                        viewModel.completeOnboarding()
                        onFinished()
                    }
                ) {
                    Text("Get started")
                }
            } else {
                Button(onClick = { stepIndex += 1 }) {
                    Text("Next")
                }
            }
        }
    }
}

private data class OnboardingStep(
    val title: String,
    val body: String
)

private fun onboardingSteps(): List<OnboardingStep> {
    return listOf(
        OnboardingStep(
            title = "Clear signals",
            body = "Track buy/sell signals with explanations and confidence scores."
        ),
        OnboardingStep(
            title = "Watchlist and movers",
            body = "Follow your watchlist and the biggest market movers in one place."
        ),
        OnboardingStep(
            title = "Local and private",
            body = "Signals are generated locally and stored on device only."
        )
    )
}
