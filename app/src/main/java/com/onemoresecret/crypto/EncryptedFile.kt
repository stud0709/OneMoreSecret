package com.onemoresecret.crypto

import com.onemoresecret.OmsDataOutputStream
import com.onemoresecret.crypto.AESUtil.process
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.function.Consumer
import java.util.function.Supplier
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

object EncryptedFile {
    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        IllegalBlockSizeException::class,
        BadPaddingException::class,
        InvalidKeyException::class,
        InvalidAlgorithmParameterException::class,
        IOException::class
    )
    @JvmStatic
    fun create(
        fis: InputStream,
        oFile: File,
        rsaPublicKeyMaterial: ByteArray,
        rsaTransformation: RsaTransformation,
        aesKeyLength: Int,
        aesTransformation: AesTransformation,
        cancellationSupplier: Supplier<Boolean>?,
        progressConsumer: Consumer<Int>?
    ) {
        try {
            FileOutputStream(oFile).use { fos ->
                OmsDataOutputStream(fos).use { dataOutputStream ->
                    val aesEncryptionParameters = MessageComposer.prepareRsaAesEnvelope(
                        dataOutputStream,
                        MessageComposer.APPLICATION_ENCRYPTED_FILE,
                        rsaPublicKeyMaterial,
                        rsaTransformation,
                        aesKeyLength,
                        aesTransformation
                    )
                    process(
                        Cipher.ENCRYPT_MODE,
                        fis,
                        dataOutputStream,
                        aesEncryptionParameters.aesKeyMaterial,
                        aesEncryptionParameters.iv,
                        aesTransformation,
                        cancellationSupplier,
                        progressConsumer
                    )
                    aesEncryptionParameters.aesKeyMaterial.fill(0)
                }
            }
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }
}
