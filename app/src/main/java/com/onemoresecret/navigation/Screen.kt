package com.onemoresecret.navigation

import kotlinx.serialization.Serializable

@Serializable
data object QrRoute

@Serializable
data class MessageRoute(val uriString: String? = null, val text: String? = null)

@Serializable
data object KeyImportRoute

@Serializable
data object NewPrivateKeyRoute

@Serializable
data object KeyManagementRoute

@Serializable
data object PasswordGeneratorRoute

@Serializable
data object PermissionsRoute

@Serializable
data class EncryptTextRoute(val text: String? = null)

@Serializable
data object TimeOtpRoute

@Serializable
data object TotpManualEntryRoute

@Serializable
data object PinSetupRoute

@Serializable
data class FileEncryptionRoute(val uriString: String? = null)

@Serializable
data object CryptoCurrencyAddressRoute

@Serializable
data class CrashReportRoute(val errorMessage: String? = null)

@Serializable
data object TotpImportRoute
