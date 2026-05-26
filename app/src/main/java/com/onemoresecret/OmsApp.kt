package com.onemoresecret

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.onemoresecret.navigation.*

@Composable
fun OmsApp() {
    val navController = rememberNavController()

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
