package com.vesteai.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vesteai.R

private data class WelcomePage(val titleRes: Int, val bodyRes: Int)

private val welcomePages = listOf(
    WelcomePage(R.string.onboarding_welcome_title_1, R.string.onboarding_welcome_body_1),
    WelcomePage(R.string.onboarding_welcome_title_2, R.string.onboarding_welcome_body_2),
    WelcomePage(R.string.onboarding_welcome_title_3, R.string.onboarding_welcome_body_3),
)

@Composable
fun WelcomeCarouselScreen(onFinished: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val current = welcomePages[page]
    val isLast = page == welcomePages.lastIndex

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(current.titleRes),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(current.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            welcomePages.indices.forEach { index ->
                val color = if (index == page) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(8.dp)
                        .background(color, CircleShape),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onFinished) { Text(stringResource(R.string.onboarding_skip)) }
            Button(onClick = { if (isLast) onFinished() else page++ }) {
                Text(stringResource(if (isLast) R.string.onboarding_get_started else R.string.onboarding_next))
            }
        }
    }
}
