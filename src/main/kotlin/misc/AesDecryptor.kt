package misc

import java.io.*
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


// Adan map files are encoded using a non-standard library kenkendk/SharpAESCrypt.
// Instead of using Key + IV for a standard AES encryption, it uses a password and parts of the encrypted content
// to generate Key1 + IV1, from which it derives Key2 + IV2.
// And finally it uses IV2 and Key2 to actually encrypt the contents using AES encryption.
// Because AES produces chunks of 16 bytes as output, SharpAESCrypt uses its own padding system to know when the
// decrypted content ends and garbage bytes begin.
// A part of the SharpAESCrypt decryption algorithm is reimplemented here, and it's enough to decrypt Adan maps.
// It skips various validity checks and version checks, assumes version=2.
fun decryptFile(filePath : String) : String{
    val password = "A5Ub5T7j5cYg40v"

    try {
        RandomAccessFile(File(filePath), "r").use { file ->
            val magicHeader = "AES".toByteArray(Charsets.UTF_8)
            val tmp = ByteArray(magicHeader.size + 2)

            file.read(tmp)
            val version = tmp[magicHeader.size].toInt()

            val extensions = mutableListOf<Pair<String, ByteArray>>()
            if (version >= 2) {
                do {
                    val tmpLength = ByteArray(2)
                    file.readFully(tmpLength)

                    val extensionLength = ((tmpLength[0].toInt() and 0xFF) shl 8) or (tmpLength[1].toInt() and 0xFF)

                    if (extensionLength != 0) {
                        val data = ByteArray(extensionLength)
                        file.readFully(data)

                        val separatorIndex = data.indexOf(0) // Find the null terminator (0)
                        if (separatorIndex < 0) {
                            throw IllegalArgumentException("Invalid extension data")
                        }

                        val key = data.sliceArray(0 until separatorIndex).toString(Charsets.UTF_8)
                        val value = data.sliceArray(separatorIndex + 1 until data.size)

                        // Assuming mExtensions is a MutableList of key-value pairs
                        extensions.add(Pair(key, value))
                    }

                } while (extensionLength > 0)
            }


            // Read IV from file
            val iv1Bytes = ByteArray(16)
            file.read(iv1Bytes)
            //println("Kotlin IV Read: ${iv1Bytes.joinToString(" ") { String.format("%02X", it) }}")

            // Encode the password
            val passwordBytes = password.toByteArray(Charsets.UTF_16LE)
            //println("Encoded Password: ${passwordBytes.joinToString(" ") { String.format("%02X", it) }}")

            // Generate AES Key 1 with IV 1
            val aesKey1 = generateAESKey1(passwordBytes, iv1Bytes)
            //println("Initial Key (from IV): ${iv1Bytes.joinToString(" ") { String.format("%02X", it) }}")
            //println("Derived AES Key 1: ${aesKey1.joinToString(" ") { String.format("%02X", it) }}")

            // Read HMAC data blocks
            val hmacBytes = ByteArray(48) // IV_SIZE (16) + KEY_SIZE (32) block
            file.read(hmacBytes)
            //println("Bytes for HMAC 1: ${hmacBytes.joinToString(" ") { String.format("%02X", it) }}")

            // Generate IV 2 and Key 2 using Key 1 and IV 1
            val (iv2Bytes, aesKey2) = generateIv2AndKey2Bytes(hmacBytes, aesKey1, iv1Bytes)
            //println("IV 2: ${iv2Bytes.joinToString(" ") { String.format("%02X", it) }}")
            //println("Key 2: ${aesKey2.joinToString(" ") { String.format("%02X", it) }}")

            // Compute HMAC1
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(aesKey1, "AES"))
            val hmac1 = mac.doFinal(hmacBytes)
            //println("Kotlin HMAC 1: ${hmac1.joinToString(" ") { String.format("%02X", it) }}")

            // Read following HMAC2
            val hmac2 = ByteArray(hmac1.size)
            file.read(hmac2)
            //println("Kotlin HMAC 2: ${hmac2.joinToString(" ") { String.format("%02X", it) }}")

            // Compare HMACs
            for (i in hmac1.indices) {
                if (hmac1[i] != hmac2[i]) throw Exception("Wrong Password")
            }

            var payloadLength : Int = -1
            // Verify payload length
            if (file.length() > file.filePointer) {
                payloadLength = file.length().toInt() - file.filePointer.toInt() - (32 + 1)
                //println("Calculated Payload Length: $payloadLength")

                if (payloadLength % 16 != 0) {
                    throw IllegalArgumentException("Invalid File Length: Payload is not a multiple of block size")
                }
            }

            // Read payload
            val encryptedPayload = ByteArray(payloadLength)
            file.read(encryptedPayload)

            // Read hidden bytes after the payload, they contain padding
            val hiddenBytes = ByteArray(33)
            file.read(hiddenBytes)
            //println("Hidden bytes: ${hiddenBytes.joinToString(" ") { String.format("%02X", it) }}")
            val padding = hiddenBytes[0]

            // Decrypt payload
            var decryptedData = decryptPayload(encryptedPayload, aesKey2, iv2Bytes)

            // Trim the first 3 bytes (some magic bytes, not useful to us)
            decryptedData = decryptedData.copyOfRange(3, decryptedData.size)

            val tailBytesToRemove = if (padding.toInt() == 0) 0 else 16 - padding
            // val tailBytes = decryptedData.copyOfRange(decryptedData.size-tailBytesToRemove, decryptedData.size)
            // println("Taken bytes: ${tailBytes.joinToString(" ") { String.format("%02X", it) }}")

            // Trim tail bytes with garbage
            decryptedData = decryptedData.copyOfRange(0, decryptedData.size-tailBytesToRemove)

            val decryptedString = String(decryptedData)
            // println(decryptedString)
            // println("Decrypted $filePath")

            return decryptedString
        }
    } catch (e: Exception) {
        println("An error occurred in AesDecryptor.kt: ${e.message}")
    }

    return ""
}

fun generateIv2AndKey2Bytes(data: ByteArray, aesKey1: ByteArray, iv1: ByteArray): Pair<ByteArray, ByteArray> {
    // Constants for the size of IV and Key
    val IV_SIZE = 16
    val KEY_SIZE = 32

    // Initialize the cipher for decryption
    val cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey1, "AES"), IvParameterSpec(iv1))
    }

    // Use ByteArrayInputStream to wrap the encrypted data
    ByteArrayInputStream(data).use { byteArrayInputStream ->
        // Use CipherInputStream to handle decryption
        CipherInputStream(byteArrayInputStream, cipher).use { cipherInputStream ->
            // Read the IV and Key directly from the decrypted stream
            val iv2 = ByteArray(IV_SIZE)
            cipherInputStream.read(iv2)

            val aesKey2 = ByteArray(KEY_SIZE)
            cipherInputStream.read(aesKey2)

            return Pair(iv2, aesKey2)
        }
    }
}


fun generateAESKey1(password: ByteArray, iv1: ByteArray): ByteArray {
    val mHash = MessageDigest.getInstance("SHA-256")
    val key = ByteArray(32)
    System.arraycopy(iv1, 0, key, 0, 16)

    for (i in 0 until 8192) {
        mHash.update(key)
        mHash.update(password)
        val hash = mHash.digest()
        System.arraycopy(hash, 0, key, 0, 32)
    }
    return key
}

fun decryptPayload(encryptedPayload: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    }
    return cipher.doFinal(encryptedPayload)
}