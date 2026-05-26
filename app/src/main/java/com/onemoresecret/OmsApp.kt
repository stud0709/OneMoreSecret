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

    NavHost(
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
            // MessageScreen(...)
        }
        composable<KeyImportRoute> {
            // KeyImportScreen(...)
        }
        composable<NewPrivateKeyRoute> {
            NewPrivateKeyScreen(
                onPopBackStack = { navController.popBackStack() }
            )
        }
        composable<KeyManagementRoute> {
            // KeyManagementScreen(...)
        }
        composable<PermissionsRoute> {
            // PermissionsScreen(...)
        }
        composable<EncryptTextRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EncryptTextRoute>()
            EncryptTextScreen(initialText = route.text ?: "")
        }
        composable<TimeOtpRoute> {
            // TimeOtpScreen(...)
        }
        composable<TotpManualEntryRoute> {
            // TotpManualEntryScreen(...)
        }
        composable<PinSetupRoute> {
            PinSetupScreen(
                onPopBackStack = { navController.popBackStack() }
            )
        }
        composable<FileEncryptionRoute> {
            // FileEncryptionScreen(...)
        }
        composable<CryptoCurrencyAddressRoute> {
            // CryptoCurrencyAddressScreen(...)
        }
    }
}
