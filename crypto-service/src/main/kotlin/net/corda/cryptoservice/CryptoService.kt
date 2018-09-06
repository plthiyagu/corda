package net.corda.cryptoservice

import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PublicKey

interface CryptoService {

    /**
     * Generate and store a new [KeyPair].
     * Note that schemeNumberID is Corda specific. Cross-check with the network operator for supported schemeNumberID
     * and their corresponding signature schemes. The main reason for using schemeNumberID and not algorithm OIDs is
     * because some schemes might not be standardised and thus an official OID might for this scheme not exist yet.
     *
     * @return the [PublicKey] of the generated [KeyPair].
     */
    fun generateKeyPair(alias: String, schemeNumberID: Int): PublicKey

    /** Check if this [CryptoService] has a private key record for the input alias. */
    fun containsKey(alias: String): Boolean

    /**
     * @return [PublicKey] of the input alias or null if it doesn't exist.
     */
    fun getPublicKey(alias: String): PublicKey?

    /**
     * Sign a [ByteArray] using the private key identified by the input alias.
     * @return the signature bytes.
     */
    fun sign(alias: String, data: ByteArray): ByteArray

    /**
     * @return [ContentSigner] for the key identified by the input alias.
     */
    fun getSigner(alias: String): ContentSigner
}
