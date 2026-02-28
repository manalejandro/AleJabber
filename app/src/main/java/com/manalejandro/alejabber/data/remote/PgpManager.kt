package com.manalejandro.alejabber.data.remote

import android.content.Context
import android.util.Log
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date
import java.security.SecureRandom

/**
 * Manages OpenPGP keys and message encryption/decryption using BouncyCastle.
 *
 * Public keys for contacts are stored as armored ASCII files in the app's
 * files directory at  pgp/contacts/<jid>.asc
 * The user's own key pair (if any) is stored at  pgp/own.asc (secret ring)
 */
class PgpManager(private val context: Context) {

    private val TAG = "PgpManager"
    private val pgpDir get() = File(context.filesDir, "pgp").also { it.mkdirs() }
    private val contactDir get() = File(pgpDir, "contacts").also { it.mkdirs() }
    private val ownKeyFile get() = File(pgpDir, "own.asc")

    // ── Key storage ───────────────────────────────────────────────────────

    /** Save an armored public key for [jid]. */
    fun saveContactPublicKey(jid: String, armoredKey: String) {
        File(contactDir, safeFileName(jid) + ".asc").writeText(armoredKey)
        Log.i(TAG, "PGP public key saved for $jid")
    }

    /** Load the armored public key for [jid], or null if not stored. */
    fun loadContactPublicKeyArmored(jid: String): String? {
        val f = File(contactDir, safeFileName(jid) + ".asc")
        return if (f.exists()) f.readText() else null
    }

    /** Delete the public key for [jid]. */
    fun deleteContactPublicKey(jid: String) {
        File(contactDir, safeFileName(jid) + ".asc").delete()
    }

    /** Returns the list of JIDs that have a stored public key. */
    fun listContactsWithKeys(): List<String> =
        contactDir.listFiles()
            ?.filter { it.extension == "asc" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

    /** Save the user's own secret key ring (armored). */
    fun saveOwnSecretKeyArmored(armoredKey: String) {
        ownKeyFile.writeText(armoredKey)
        Log.i(TAG, "Own PGP secret key saved")
    }

    /** Load the user's own secret key ring (armored), or null. */
    fun loadOwnSecretKeyArmored(): String? =
        if (ownKeyFile.exists()) ownKeyFile.readText() else null

    fun hasOwnKey(): Boolean = ownKeyFile.exists()

    /** Returns the fingerprint of the user's own primary key as a hex string. */
    fun getOwnKeyFingerprint(): String? {
        val armored = loadOwnSecretKeyArmored() ?: return null
        return try {
            val secretRing = readSecretKeyRing(armored) ?: return null
            val fingerprint = secretRing.secretKey.publicKey.fingerprint
            fingerprint.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading own key fingerprint", e)
            null
        }
    }

    // ── Encrypt ───────────────────────────────────────────────────────────

    /**
     * Encrypts [plaintext] for [jid].
     * Returns the armored ciphertext, or null if no key is stored for [jid].
     */
    fun encryptFor(jid: String, plaintext: String): String? {
        val armoredKey = loadContactPublicKeyArmored(jid) ?: return null
        return try {
            val pubKey = readFirstPublicEncryptionKey(armoredKey) ?: return null
            encrypt(plaintext, pubKey)
        } catch (e: Exception) {
            Log.e(TAG, "PGP encrypt error for $jid", e)
            null
        }
    }

    private fun encrypt(plaintext: String, pubKey: PGPPublicKey): String {
        val out = ByteArrayOutputStream()
        val armoredOut = ArmoredOutputStream(out)

        val encGen = PGPEncryptedDataGenerator(
            BcPGPDataEncryptorBuilder(
                org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags.AES_256
            ).setWithIntegrityPacket(true).setSecureRandom(SecureRandom())
        )
        encGen.addMethod(BcPublicKeyKeyEncryptionMethodGenerator(pubKey))

        val encOut = encGen.open(armoredOut, ByteArray(1 shl 16))
        val literalGen = PGPLiteralDataGenerator()
        val literalOut: java.io.OutputStream = literalGen.open(
            encOut, PGPLiteralData.BINARY, "", Date(), ByteArray(1 shl 16)
        )
        literalOut.write(plaintext.toByteArray(Charsets.UTF_8))
        literalOut.close()
        encOut.close()
        armoredOut.close()
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    // ── Decrypt ───────────────────────────────────────────────────────────

    /**
     * Decrypts an armored PGP message using the user's own private key.
     * [passphrase] is used to unlock the private key.
     * Returns plaintext or null on failure.
     */
    fun decrypt(armoredCiphertext: String, passphrase: CharArray = CharArray(0)): String? {
        val armoredOwnKey = loadOwnSecretKeyArmored() ?: return null
        return try {
            val secretRing = readSecretKeyRing(armoredOwnKey) ?: return null
            val factory    = PGPObjectFactory(
                PGPUtil.getDecoderStream(ByteArrayInputStream(armoredCiphertext.toByteArray())),
                JcaKeyFingerprintCalculator()
            )
            val encDataList = factory.nextObject() as? PGPEncryptedDataList
                ?: (factory.nextObject() as? PGPEncryptedDataList)
                ?: return null

            val encData = encDataList.encryptedDataObjects.asSequence()
                .filterIsInstance<PGPPublicKeyEncryptedData>()
                .firstOrNull() ?: return null

            val secretKey = secretRing.getSecretKey(encData.keyID) ?: return null
            val privateKey = secretKey.extractPrivateKey(
                JcePBESecretKeyDecryptorBuilder()
                    .setProvider("BC")
                    .build(passphrase)
            )
            val plainStream = encData.getDataStream(BcPublicKeyDataDecryptorFactory(privateKey))
            val plainFactory = PGPObjectFactory(plainStream, JcaKeyFingerprintCalculator())
            var obj = plainFactory.nextObject()
            if (obj is PGPCompressedData) {
                obj = PGPObjectFactory(
                    obj.dataStream, JcaKeyFingerprintCalculator()
                ).nextObject()
            }
            val literalData = obj as? PGPLiteralData ?: return null
            literalData.inputStream.readBytes().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "PGP decrypt error", e)
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun readFirstPublicEncryptionKey(armored: String): PGPPublicKey? {
        val stream = ArmoredInputStream(ByteArrayInputStream(armored.toByteArray()))
        val col    = PGPPublicKeyRingCollection(stream, BcKeyFingerprintCalculator())
        for (ring in col.keyRings) {
            for (key in ring.publicKeys) {
                if (key.isEncryptionKey) return key
            }
        }
        return null
    }

    private fun readSecretKeyRing(armored: String): PGPSecretKeyRing? {
        val stream = ArmoredInputStream(ByteArrayInputStream(armored.toByteArray()))
        val col    = PGPSecretKeyRingCollection(stream, BcKeyFingerprintCalculator())
        return col.keyRings.asSequence().firstOrNull()
    }

    private fun safeFileName(jid: String): String =
        jid.replace(Regex("[^a-zA-Z0-9._@-]"), "_")
}




