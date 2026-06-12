package com.onemoresecret.navigation

import kotlinx.serialization.Serializable

@Serializable
data object QrRoute

@Serializable
data class MessageRoute(val uriString: String? = null, val text: String? = null, val popBackStack: Boolean = false)

@Serializable
data class KeyImportRoute(val popBackStack: Boolean = false)

@Serializable
data object NewPrivateKeyRoute

@Serializable
data object KeyManagementRoute

@Serializable
data object PasswordGeneratorRoute

@Serializable
data object PermissionsRoute

@Serializable
data class EncryptTextRoute(val text: String? = null, val popBackStack: Boolean = false)

@Serializable
data object TimeOtpRoute

@Serializable
data object TotpManualEntryRoute

@Serializable
data object PinSetupRoute

@Serializable
data class FileEncryptionRoute(val uriString: String? = null, val popBackStack: Boolean = false)

@Serializable
data object CryptoCurrencyAddressRoute

@Serializable
data class CrashReportRoute(val errorMessage: String? = null)

@Serializable
data class TotpImportRoute(val popBackStack: Boolean = false)
