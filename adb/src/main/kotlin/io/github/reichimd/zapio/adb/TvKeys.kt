package io.github.reichimd.zapio.adb

import dadb.AdbKeyPair
import java.io.File

/**
 * The RSA key pair this app identifies itself with towards the TV.
 *
 * The TV asks once whether to trust the key; after "Always allow" it remembers it.
 * The files must live in app-private storage, never in shared storage.
 */
class TvKeys private constructor(internal val keyPair: AdbKeyPair) {

    companion object {
        /** Loads the key pair from [privateKey] and [publicKey], creating it first if needed. */
        fun loadOrCreate(privateKey: File, publicKey: File): TvKeys {
            if (!privateKey.exists() || !publicKey.exists()) {
                privateKey.parentFile?.mkdirs()
                AdbKeyPair.generate(privateKey, publicKey)
            }
            return load(privateKey, publicKey)
        }

        /** Loads an existing key pair. */
        fun load(privateKey: File, publicKey: File): TvKeys =
            TvKeys(AdbKeyPair.read(privateKey, publicKey))
    }
}
