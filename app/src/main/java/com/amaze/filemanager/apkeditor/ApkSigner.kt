/*
 * Copyright (C) 2014-2024 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.apkeditor

import android.content.Context
import com.android.apksig.ApkVerifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import com.android.apksig.ApkSigner as ApkSigSigner

/** Signs and verifies APKs through the Android apksig library, without invoking a CLI. */
class ApkSigner(private val context: Context) {
    private val logger = LoggerFactory.getLogger(ApkSigner::class.java)

    companion object {
        const val KEYSTORE_PASSWORD = "android"
        const val KEY_ALIAS = "androiddebugkey"
        const val KEY_PASSWORD = "android"
        private const val TEST_KEYSTORE_NAME = "apk-editor-test.p12"
        private val testKeyLock = Any()
    }

    fun signApk(
        inputApkPath: String,
        outputApkPath: String,
    ): ApkOperationResult =
        try {
            val keystore = getOrCreateTestKeystore()
            signApkWithKeystore(inputApkPath, outputApkPath, keystore.path, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)
        } catch (e: Exception) {
            logger.error("Failed to load APK editor test key", e)
            ApkOperationResult.Error("Failed to load APK editor test key: ${e.message}", e)
        }

    fun signApkWithKeystore(
        inputApkPath: String,
        outputApkPath: String,
        keystorePath: String,
        keystorePassword: String,
        keyAlias: String,
        keyPassword: String,
    ): ApkOperationResult {
        return try {
            val input = File(inputApkPath)
            val output = File(outputApkPath)
            val keystore = File(keystorePath)
            require(input.isFile) { "Input APK not found: $inputApkPath" }
            require(keystore.isFile) { "Keystore not found: $keystorePath" }
            require(input.canonicalFile != output.canonicalFile) { "Input and output APKs must differ" }

            val store = loadKeystore(keystore, keystorePassword.toCharArray())
            val key =
                store.getKey(keyAlias, keyPassword.toCharArray()) as? PrivateKey
                    ?: error("No private key found for alias $keyAlias")
            val certificates =
                store.getCertificateChain(keyAlias)?.map { it as X509Certificate }
                    ?: error("No certificate chain found for alias $keyAlias")
            val signer = ApkSigSigner.SignerConfig.Builder(keyAlias, key, certificates).build()

            output.parentFile?.mkdirs()
            ApkSigSigner.Builder(listOf(signer))
                .setInputApk(input)
                .setOutputApk(output)
                .build()
                .sign()
            ApkOperationResult.Success("APK signed successfully", outputApkPath)
        } catch (e: Exception) {
            logger.error("Failed to sign APK", e)
            ApkOperationResult.Error("Failed to sign APK: ${e.message}", e)
        }
    }

    fun verifyApk(apkPath: String): ApkVerificationResult {
        return try {
            val apk = File(apkPath)
            require(apk.isFile) { "APK not found: $apkPath" }
            val result = ApkVerifier.Builder(apk).build().verify()
            val schemes =
                buildList {
                    if (result.isVerifiedUsingV1Scheme) add("v1 (JAR signing)")
                    if (result.isVerifiedUsingV2Scheme) add("v2 (APK Signature Scheme v2)")
                    if (result.isVerifiedUsingV3Scheme) add("v3 (APK Signature Scheme v3)")
                    if (result.isVerifiedUsingV31Scheme) add("v3.1 (APK Signature Scheme v3.1)")
                }
            val details = (result.allErrors + result.warnings).joinToString("\n")
            ApkVerificationResult.Success(result.isVerified, schemes, details)
        } catch (e: Exception) {
            logger.error("APK verification failed", e)
            ApkVerificationResult.Error("Verification failed: ${e.message}")
        }
    }

    private fun loadKeystore(
        file: File,
        password: CharArray,
    ): KeyStore {
        var lastError: Exception? = null
        for (type in listOf("PKCS12", "JKS", "BKS")) {
            try {
                return KeyStore.getInstance(type).apply {
                    file.inputStream().use { load(it, password) }
                }
            } catch (e: Exception) {
                if (lastError == null) lastError = e
            }
        }
        throw IllegalArgumentException("Could not open keystore; check its type and password", lastError)
    }

    private fun getOrCreateTestKeystore(): File =
        synchronized(testKeyLock) {
            val file = File(context.filesDir, TEST_KEYSTORE_NAME)
            if (!file.exists()) {
                val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val name = X500Name("CN=APK Editor Test Key")
                val now = System.currentTimeMillis()
                val certificate =
                    JcaX509CertificateConverter().getCertificate(
                        JcaX509v3CertificateBuilder(
                            name,
                            BigInteger(128, SecureRandom()).add(BigInteger.ONE),
                            Date(now - 60_000),
                            Date(now + 3650L * 24 * 60 * 60 * 1000),
                            name,
                            keyPair.public,
                        ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)),
                    )
                val password = KEYSTORE_PASSWORD.toCharArray()
                val store =
                    KeyStore.getInstance("PKCS12").apply {
                        load(null, null)
                        setKeyEntry(KEY_ALIAS, keyPair.private, password, arrayOf(certificate))
                    }
                val temporary = File.createTempFile("apk-editor-test", ".p12", file.parentFile)
                try {
                    temporary.outputStream().use { store.store(it, password) }
                    check(temporary.renameTo(file)) { "Could not save APK editor test key" }
                } finally {
                    temporary.delete()
                }
            }
            file
        }
}

sealed class ApkVerificationResult {
    data class Success(
        val isSigned: Boolean,
        val signatureSchemes: List<String>,
        val details: String,
    ) : ApkVerificationResult()

    data class Error(val message: String) : ApkVerificationResult()
}
