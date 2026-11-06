package com.vesteai.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vesteai.R

@Composable
fun CameraPermissionScreen(
    onGranted: () -> Unit,
    onPickInstead: () -> Unit,
    viewModel: CameraPermissionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val granted by rememberCameraGranted()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(granted) { viewModel.onStatusChecked(granted) }
    LaunchedEffect(state) {
        if (state == CameraPermissionState.Granted) onGranted()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val activity = context.findActivity()
        val rationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        viewModel.onRequestResult(isGranted, rationale)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            CameraPermissionState.Checking -> CircularProgressIndicator()
            CameraPermissionState.Granted -> CircularProgressIndicator()
            CameraPermissionState.Needed -> {
                Text(
                    text = stringResource(R.string.camera_permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.camera_permission_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.camera_permission_allow))
                }
            }
            CameraPermissionState.Denied -> {
                Text(
                    text = stringResource(R.string.camera_permission_denied_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.camera_permission_denied_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.camera_permission_retry))
                }
                OutlinedButton(onClick = onPickInstead, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.camera_permission_pick_instead))
                }
            }
            CameraPermissionState.PermanentlyDenied -> {
                Text(
                    text = stringResource(R.string.camera_permission_permanent_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.camera_permission_permanent_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Button(onClick = { context.startActivity(appSettingsIntent(context)) }) {
                    Text(stringResource(R.string.camera_permission_open_settings))
                }
                OutlinedButton(onClick = onPickInstead, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.camera_permission_pick_instead))
                }
            }
        }
    }
}

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
