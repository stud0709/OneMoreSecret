package com.onemoresecret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.onemoresecret.navigation.CryptoCurrencyAddressRoute
import com.onemoresecret.navigation.EncryptTextRoute
import com.onemoresecret.navigation.FileEncryptionRoute
import com.onemoresecret.navigation.KeyImportRoute
import com.onemoresecret.navigation.KeyManagementRoute
import com.onemoresecret.navigation.MessageRoute
import com.onemoresecret.navigation.NewPrivateKeyRoute
import com.onemoresecret.navigation.PasswordGeneratorRoute
import com.onemoresecret.navigation.PermissionsRoute
import com.onemoresecret.navigation.PinSetupRoute
import com.onemoresecret.navigation.QrRoute
import com.onemoresecret.navigation.TotpImportRoute
import com.onemoresecret.navigation.TotpManualEntryRoute

@Composable
fun OmsApp() {
    val navController = rememberNavController()
    val omsState = remember { OmsState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, omsState, navController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (!omsState.isAutoLockDisarmed) {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute?.contains(MessageRoute::class.qualifiedName!!) == true) {
                        navController.popBackStack<QrRoute>(inclusive = false)
                    }
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                omsState.isAutoLockDisarmed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CompositionLocalProvider(LocalOmsState provides omsState) {
        androidx.navigation.compose.NavHost(
            navController = navController,
            startDestination = QrRoute
        ) {
            composable<QrRoute> {
                QRScreen(navController)
            }
            composable<PasswordGeneratorRoute> {
                PasswordGeneratorScreen()
            }
            composable<MessageRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<MessageRoute>()
                val previousBackStackEntry = navController.previousBackStackEntry
                val applicationId = previousBackStackEntry?.savedStateHandle?.get<Int>(QRScreen.ARG_APPLICATION_ID)
                val messageData = previousBackStackEntry?.savedStateHandle?.get<ByteArray>(QRScreen.ARG_MESSAGE)
                val uri = route.uriString?.let { android.net.Uri.parse(it) } ?: previousBackStackEntry?.savedStateHandle?.get<android.net.Uri>(QRScreen.ARG_URI)
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.activity.compose.BackHandler(enabled = route.popBackStack) {
                    (context as? android.app.Activity)?.finish()
                }
                MessageScreen(
                    applicationId = applicationId,
                    messageData = messageData,
                    uri = uri,
                    onNavigateBack = { if (route.popBackStack) (context as? android.app.Activity)?.finish() else navController.popBackStack() }
                )
            }
            composable<KeyImportRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<KeyImportRoute>()
                val previousBackStackEntry = navController.previousBackStackEntry
                val messageData = previousBackStackEntry?.savedStateHandle?.get<ByteArray>(QRScreen.ARG_MESSAGE) ?: ByteArray(0)
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.activity.compose.BackHandler(enabled = route.popBackStack) {
                    (context as? android.app.Activity)?.finish()
                }
                KeyImportScreen(
                    message = messageData,
                    onImportCompleted = { if (route.popBackStack) (context as? android.app.Activity)?.finish() else navController.popBackStack() }
                )
            }
            composable<NewPrivateKeyRoute> {
                NewPrivateKeyScreen(
                    onPopBackStack = { navController.popBackStack() }
                )
            }
            composable<KeyManagementRoute> {
                KeyManagementScreen(
                    onNavigateToNewPrivateKey = { navController.navigate(NewPrivateKeyRoute) }
                )
            }
            composable<PermissionsRoute> {
                PermissionsScreen(
                    onProceed = { navController.popBackStack() }
                )
            }
            composable<EncryptTextRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<EncryptTextRoute>()
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.activity.compose.BackHandler(enabled = route.popBackStack) {
                    (context as? android.app.Activity)?.finish()
                }
                EncryptTextScreen(initialText = route.text ?: "", onNavigateBack = { if (route.popBackStack) (context as? android.app.Activity)?.finish() else navController.popBackStack() })
            }
            composable<TotpImportRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<TotpImportRoute>()
                val previousBackStackEntry = navController.previousBackStackEntry
                val messageData = previousBackStackEntry?.savedStateHandle?.get<ByteArray>(QRScreen.ARG_MESSAGE) ?: ByteArray(0)
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.activity.compose.BackHandler(enabled = route.popBackStack) {
                    (context as? android.app.Activity)?.finish()
                }
                TotpImportScreen(
                    message = messageData,
                    onImportCompleted = { if (route.popBackStack) (context as? android.app.Activity)?.finish() else navController.popBackStack() }
                )
            }
            composable<TotpManualEntryRoute> {
                TotpManualEntryScreen()
            }
            composable<PinSetupRoute> {
                PinSetupScreen(
                    onPopBackStack = { navController.popBackStack() }
                )
            }
            composable<FileEncryptionRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<FileEncryptionRoute>()
                val uriString = route.uriString
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.activity.compose.BackHandler(enabled = route.popBackStack) {
                    (context as? android.app.Activity)?.finish()
                }
                if (uriString != null) {
                    FileEncryptionScreen(
                        uri = android.net.Uri.parse(uriString),
                        onNavigateBack = { if (route.popBackStack) (context as? android.app.Activity)?.finish() else navController.popBackStack() }
                    )
                }
            }
            composable<CryptoCurrencyAddressRoute> {
                CryptoCurrencyAddressScreen()
            }
        }
    }
}
