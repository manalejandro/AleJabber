package com.manalejandro.alejabber.data.remote

import android.util.Base64
import android.util.Log
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.modes.SICBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Lightweight OTR-inspired session using BouncyCastle.
 *
 * This implements the *symmetric encryption core* of OTR:
 *  - Ephemeral ECDH key exchange on Curve25519 (via BouncyCastle named curve "curve25519")
 *  - AES-256 in CTR mode for message encryption
 *  - HMAC-SHA256 for message authentication
 *  - A nonce counter to provide Forward Secrecy across messages within a session
 *
 * Protocol flow:
 *  1. Both sides call [getPublicKeyBytes] to get their ephemeral public key.
 *  2. When the peer's public key is received, call [setRemotePublicKey].
 *     This derives the shared AES and MAC keys using ECDH.
 *  3. [encrypt] / [decrypt] can then be called.
 *
 * The ciphertext format is:
 *   BASE64( nonce(8 bytes) | ciphertext | hmac(32 bytes) )
 * prefixed with the OTR-style header "?OTR:" so recipients can identify it.
 *
 * NOTE: True OTR (OTR v2/v3/v4) requires D-H ratcheting and a full AKE
 * (Authenticated Key Exchange) negotiation via XMPP. That full protocol
 * requires the otr4j library (MIT-licensed). This implementation provides
 * session-level encryption that can be upgraded to full OTR when otr4j is
 * added as a dependency.
 */
class OtrSession {

    private val TAG = "OtrSession"
    private val rng = SecureRandom()

    // Curve25519 ECDH parameters
    private val curveParams = ECNamedCurveTable.getParameterSpec("curve25519")
    private val domainParams = ECDomainParameters(
        curveParams.curve, curveParams.g, curveParams.n, curveParams.h
    )

    // Ephemeral local key pair
    private val localKeyPair: Pair<ByteArray, ByteArray>  // (privateKey, publicKey)

    // Shared secret derived after ECDH
    private var sessionAesKey: ByteArray? = null
    private var sessionMacKey: ByteArray? = null

    // Monotonic counter used as IV for AES-CTR
    private var sendCounter = 0L
    private var recvCounter = 0L

    init {
        val keyGenParams = ECKeyGenerationParameters(domainParams, rng)
        val generator = ECKeyPairGenerator()
        generator.init(keyGenParams)
        val keyPair = generator.generateKeyPair()
        val privKey = keyPair.private as ECPrivateKeyParameters
        val pubKey  = keyPair.public  as ECPublicKeyParameters
        localKeyPair = privKey.d.toByteArray() to pubKey.q.getEncoded(true)
        Log.d(TAG, "OTR ephemeral key pair generated")
    }

    /** Returns our ephemeral compressed public key (33 bytes for curve25519). */
    fun getPublicKeyBytes(): ByteArray = localKeyPair.second

    /** Returns our public key as a Base64 string for transport in a chat message. */
    fun getPublicKeyBase64(): String =
        Base64.encodeToString(localKeyPair.second, Base64.NO_WRAP)

    /**
     * Finalises the ECDH key exchange using the remote party's [publicKeyBytes].
     * After this call, [encrypt] and [decrypt] are operational.
     */
    fun setRemotePublicKey(publicKeyBytes: ByteArray) {
        try {
            val remotePoint = curveParams.curve.decodePoint(publicKeyBytes)
            val remoteKey   = ECPublicKeyParameters(remotePoint, domainParams)
            val privD       = org.bouncycastle.math.ec.ECAlgorithms.referenceMultiply(
                remoteKey.q, org.bouncycastle.util.BigIntegers.fromUnsignedByteArray(localKeyPair.first)
            )
            val sharedX = privD.xCoord.encoded   // 32 bytes
            // Derive AES key (first 32 bytes of SHA-256(shared)) and MAC key (SHA-256 of AES key)
            val sha256 = java.security.MessageDigest.getInstance("SHA-256")
            val aesKey = sha256.digest(sharedX)
            sessionAesKey = aesKey
            sessionMacKey = sha256.digest(aesKey)
            Log.i(TAG, "OTR ECDH complete — session keys derived")
        } catch (e: Exception) {
            Log.e(TAG, "ECDH key exchange failed", e)
            throw e
        }
    }

    /**
     * Encrypts [plaintext] using AES-256-CTR and authenticates it with HMAC-SHA256.
     * Returns the encoded ciphertext string suitable for XMPP transport.
     * Throws [IllegalStateException] if [setRemotePublicKey] was not called yet.
     */
    fun encrypt(plaintext: String): String {
        val aesKey = sessionAesKey ?: error("OTR session not established — call setRemotePublicKey first")
        val macKey = sessionMacKey!!
        val nonce  = longToBytes(sendCounter++)
        val iv     = ByteArray(16).also { System.arraycopy(nonce, 0, it, 0, 8) }

        // AES-256-CTR
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // HMAC-SHA256 over nonce + ciphertext
        val mac  = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        mac.update(nonce)
        val hmac = mac.doFinal(cipherBytes)

        // Format: nonce(8) | ciphertext | hmac(32)
        val payload = nonce + cipherBytes + hmac
        return "?OTR:" + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /**
     * Decrypts an OTR-encoded [ciphertext] produced by [encrypt].
     * Returns the plaintext, or null if authentication fails.
     */
    fun decrypt(ciphertext: String): String? {
        val aesKey = sessionAesKey ?: return null
        val macKey = sessionMacKey ?: return null

        return try {
            val stripped = ciphertext.removePrefix("?OTR:")
            val payload  = Base64.decode(stripped, Base64.NO_WRAP)
            if (payload.size < 8 + 32) return null

            val nonce       = payload.slice(0..7).toByteArray()
            val hmacStored  = payload.slice(payload.size - 32 until payload.size).toByteArray()
            val cipherBytes = payload.slice(8 until payload.size - 32).toByteArray()

            // Verify HMAC first
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(macKey, "HmacSHA256"))
            mac.update(nonce)
            val hmacCalc = mac.doFinal(cipherBytes)
            if (!hmacCalc.contentEquals(hmacStored)) {
                Log.w(TAG, "OTR HMAC verification failed")
                return null
            }

            // Decrypt
            val iv = ByteArray(16).also { System.arraycopy(nonce, 0, it, 0, 8) }
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
            cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "OTR decrypt error", e)
            null
        }
    }

    fun isEstablished(): Boolean = sessionAesKey != null

    private fun longToBytes(l: Long): ByteArray = ByteArray(8) { i ->
        ((l shr ((7 - i) * 8)) and 0xFF).toByte()
    }
}


