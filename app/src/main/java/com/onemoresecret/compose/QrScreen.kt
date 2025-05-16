package com.onemoresecret.compose

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.onemoresecret.R
import kotlinx.coroutines.launch


@Composable
fun QrScreen(navController: NavController?, snackbarHostState: SnackbarHostState?) {
    var cameraPreview: PreviewView? = null
    var resourceProvider = ResourceProvider(LocalContext.current)
    val viewModel =
        QrViewModel(LocalContext.current.applicationContext as Application, resourceProvider)

    /*
    if (requireActivity().supportFragmentManager.backStackEntryCount != 0) {
        Log.w(QRFragment.TAG, "Discarding back stack")
        discardBackStack(this)
    }


    if (!preferences.getBoolean(PermissionsFragment.PROP_PERMISSIONS_REQUESTED, false)) {
        NavHostFragment.findNavController(this@QRFragment)
            .navigate(R.id.action_QRFragment_to_permissionsFragment)
        return
    }
     */

    //requireActivity().addMenuProvider(menuProvider)


    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    cameraPreview = PreviewView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    cameraPreview
                },
                modifier = Modifier.fillMaxSize()
            )

            if (viewModel.wiFiPairing.value) {
                Image(
                    painter = painterResource(id = R.drawable.leak_add),
                    contentDescription = "Bluetooth: Pairing",
                    modifier = Modifier
                        .size(128.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.remaining_codes),
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = viewModel.remainingCodes.value,
                style = MaterialTheme.typography.headlineMedium
            )

            if (viewModel.recentEntries.isNotEmpty()) {
                Text(
                    text = stringResource(id = R.string.recent),
                    modifier = Modifier.padding(top = 8.dp)
                )

                LazyRow(modifier = Modifier.fillMaxWidth()) {
                    items(count = viewModel.recentEntries.size) { index ->
                        RecentEntryButton(
                            drawableId = viewModel.recentEntries[index].drawableId,
                            onMessage = viewModel::onMessage,
                            recentMessage = viewModel.recentEntries[index].message
                        )
                    }
                }
            }
        }

        viewModel.pinEntryParams.value?.let {
            PinEntryScreen(
                onSuccess = it.onSuccess,
                onCancel = it.onCancel,
                onPanic = { viewModel.loadRecentEntries() },
                onDismiss = { viewModel.pinEntryParams.value = null })
        }
    }

    AddLaunchEffects(navController, cameraPreview, LocalLifecycleOwner.current, snackbarHostState)


}

@Composable
fun AddLaunchEffects(
    navController: NavController?,
    cameraPreview: PreviewView?,
    lifecycleOwner: LifecycleOwner,
    snackbarHostState: SnackbarHostState?
) {
    val viewModel: QrViewModel = viewModel()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val intent = LocalActivity.current?.intent
    if (intent != null) {
        LocalActivity.current?.intent = null
        viewModel.processIntent(intent, context)
    }

    LaunchedEffect(viewModel.navigateTo.value) {
        if (viewModel.navigateTo.value == null) return@LaunchedEffect
        navController?.navigate(viewModel.navigateTo.value!!)
    }

    LaunchedEffect(viewModel.snackbarMessage.value) {
        if (viewModel.snackbarMessage.value == null) return@LaunchedEffect
        val msg = viewModel.snackbarMessage.value!!
        viewModel.snackbarMessage.value = null

        coroutineScope.launch { snackbarHostState?.showSnackbar(msg) }
    }

    //enable camera
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startCamera(context, viewModel, cameraPreview!!, lifecycleOwner)
        } else {
            Toast.makeText(context, R.string.insufficient_permissions, Toast.LENGTH_LONG)
                .show()
        }
    }

    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCamera(context, viewModel, cameraPreview!!, lifecycleOwner)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(viewModel.bioPromptParams.value) {
        if (viewModel.bioPromptParams.value == null) return@LaunchedEffect
        val bioPromptParams = viewModel.bioPromptParams.value!!
        viewModel.bioPromptParams.value = null

        BiometricPrompt(
            context as FragmentActivity,
            context.mainExecutor,
            bioPromptParams.callback
        ).authenticate(
            bioPromptParams.promptInfo,
            BiometricPrompt.CryptoObject(bioPromptParams.cipher)
        )
    }
}

@Composable
fun RecentEntryButton(
    drawableId: Int,
    onMessage: (String, Boolean) -> Unit,
    recentMessage: String
) {
    val buttonSize = 100.dp to 50.dp
    val marginStart = 8.dp

    Image(
        painter = painterResource(id = drawableId),
        contentDescription = null,
        modifier = Modifier
            .size(buttonSize.first, buttonSize.second)
            .padding(start = marginStart)
            .clickable { onMessage(recentMessage, false) }
    )
}

@Preview(showBackground = true)
@Composable
fun QrScreenPreview() {
    QrScreen(null, null)
}









