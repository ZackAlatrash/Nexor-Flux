package com.zack.recomptracker.data.preferences

import android.content.SharedPreferences
import java.io.IOException
import java.security.GeneralSecurityException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * The undecryptable-keyset recovery flow behind [SecureKeyStore] (review P1-4). After a
 * device-to-device restore, EncryptedSharedPreferences.create() throws because the backed-up keyset
 * can't be decrypted without the original Keystore master key; since the store is built eagerly in
 * Application.onCreate, an unhandled throw crash-loops the launch. The store must instead drop the
 * unreadable file and rebuild.
 */
class SecureKeyStoreRecoveryTest {

    @Test
    fun `recovers from an undecryptable keyset by dropping the store and rebuilding`() {
        val rebuilt = mock<SharedPreferences>()
        var attempt = 0
        var deleted = false

        val result = openEncryptedPrefsWithRecovery(
            build = {
                attempt++
                if (attempt == 1) throw GeneralSecurityException("keyset undecryptable") else rebuilt
            },
            deleteCorruptStore = { deleted = true },
        )

        assertTrue("the corrupt store is dropped before rebuilding", deleted)
        assertSame("the rebuilt store is returned", rebuilt, result)
    }

    @Test
    fun `recovers from an IOException on open`() {
        val rebuilt = mock<SharedPreferences>()
        var attempt = 0
        var deleted = false

        val result = openEncryptedPrefsWithRecovery(
            build = { attempt++; if (attempt == 1) throw IOException("corrupt file") else rebuilt },
            deleteCorruptStore = { deleted = true },
        )

        assertTrue(deleted)
        assertSame(rebuilt, result)
    }

    @Test
    fun `a healthy store is returned without dropping anything`() {
        val healthy = mock<SharedPreferences>()
        var deleted = false

        val result = openEncryptedPrefsWithRecovery(
            build = { healthy },
            deleteCorruptStore = { deleted = true },
        )

        assertFalse("a readable store is never dropped", deleted)
        assertSame(healthy, result)
    }

    @Test
    fun `propagates if the rebuild also fails`() {
        var deleted = false
        assertThrows(GeneralSecurityException::class.java) {
            openEncryptedPrefsWithRecovery(
                build = { throw GeneralSecurityException("still broken") },
                deleteCorruptStore = { deleted = true },
            )
        }
        assertTrue("recovery was attempted once", deleted)
    }

    @Test
    fun `an unexpected exception is not swallowed and the store is not dropped`() {
        var deleted = false
        assertThrows(IllegalStateException::class.java) {
            openEncryptedPrefsWithRecovery(
                build = { throw IllegalStateException("programming error") },
                deleteCorruptStore = { deleted = true },
            )
        }
        assertFalse("an unexpected failure must not delete the user's key store", deleted)
    }
}
