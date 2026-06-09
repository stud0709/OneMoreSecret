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
            composable<MessageRoute> {
                val previousBackStackEntry = navController.previousBackStackEntry
                val applicationId = previousBackStackEntry?.savedStateHandle?.get<Int>(QRScreen.ARG_APPLICATION_ID)
                val messageData = previousBackStackEntry?.savedStateHandle?.get<ByteArray>(QRScreen.ARG_MESSAGE)
                val uri = previousBackStackEntry?.savedStateHandle?.get<android.net.Uri>(QRScreen.ARG_URI)
                MessageScreen(
                    applicationId = applicationId,
                    messageData = messageData,
                    uri = uri,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<KeyImportRoute> {
                val previousBackStackEntry = navController.previousBackStackEntry
                val messageData = previousBackStackEntry?.savedStateHandle?.get<ByteArray>(QRScreen.ARG_MESSAGE) ?: ByteArray(0)
                KeyImportScreen(
                    message = messageData,
                    onImportCompleted = { navController.popBackStack() }
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
                EncryptTextScreen(initialText = route.text ?: "", onNavigateBack = { navController.popBackStack() })
            }
            composable<TotpImportRoute> {
                val previousBackStackEntry = navController.previousBackStackEntry
                val messageData = previousBackStackEntry?.savedStateHandle?.get<ByteArray>(QRScreen.ARG_MESSAGE) ?: ByteArray(0)
                TotpImportScreen(
                    message = messageData,
                    onImportCompleted = { navController.popBackStack() }
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
            composable<FileEncryptionRoute> {
                val previousBackStackEntry = navController.previousBackStackEntry
                val uri = previousBackStackEntry?.savedStateHandle?.get<android.net.Uri>(QRScreen.ARG_URI)
                if (uri != null) {
                    FileEncryptionScreen(
                        uri = uri,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable<CryptoCurrencyAddressRoute> {
                CryptoCurrencyAddressScreen()
            }
        }
    }
}
