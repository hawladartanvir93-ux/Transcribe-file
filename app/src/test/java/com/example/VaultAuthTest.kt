package com.example

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.auth.AuthResult
import com.example.data.auth.VaultAuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultAuthTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepo(clockProvider: () -> Long = { 1_000_000L }): VaultAuthRepository {
        val dsFile = File(tempFolder.newFolder(), "auth_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { dsFile })
        return VaultAuthRepository(dataStore = dataStore, clockMs = clockProvider)
    }

    @Test
    fun register_validatesNumberAndPasswordRules() = runTest {
        val repo = createRepo()

        val shortNum = repo.register("123", "secret123", "secret123")
        assertTrue(shortNum is AuthResult.Error)

        val alphaNum = repo.register("abc1234", "secret123", "secret123")
        assertTrue(alphaNum is AuthResult.Error)

        val shortPw = repo.register("01712345678", "12345", "12345")
        assertTrue(shortPw is AuthResult.Error)

        val mismatch = repo.register("01712345678", "secret123", "secret999")
        assertTrue(mismatch is AuthResult.Error)

        val valid = repo.register("+880 1712-345678", "MyVault#2026", "MyVault#2026")
        assertEquals(AuthResult.Success, valid)

        val snapshot = repo.authStateFlow.first()
        assertTrue(snapshot.isAccountRegistered)
        assertEquals("••••5678", snapshot.maskedNumberHint)
    }

    @Test
    fun login_verifiesExactNumberAndPasswordWithSaltedHash() = runTest {
        val repo = createRepo()
        assertEquals(AuthResult.Success, repo.register("01712345678", "VaultPass99", "VaultPass99"))

        assertTrue(repo.login("01712345679", "VaultPass99") is AuthResult.Error)
        assertTrue(repo.login("01712345678", "WrongPass99") is AuthResult.Error)
        assertEquals(AuthResult.Success, repo.login("01712345678", "VaultPass99"))
        // Formatting separators normalize cleanly to the same digits
        assertEquals(AuthResult.Success, repo.login("01712-345-678", "VaultPass99"))
    }

    @Test
    fun login_enforcesLockoutAfterFiveFailuresAndRecoversAfterCooldown() = runTest {
        var currentTime = 10_000L
        val repo = createRepo { currentTime }
        assertEquals(AuthResult.Success, repo.register("4829", "StrongPass1", "StrongPass1"))

        repeat(4) {
            val res = repo.login("4829", "WrongPass")
            assertTrue(res is AuthResult.Error)
            assertEquals(0, (res as AuthResult.Error).lockoutRemainingSeconds)
        }

        val fifth = repo.login("4829", "WrongPass")
        assertTrue(fifth is AuthResult.Error)
        assertTrue((fifth as AuthResult.Error).lockoutRemainingSeconds > 0)

        // Even the right password must be rejected during lockout
        val duringLockout = repo.login("4829", "StrongPass1")
        assertTrue(duringLockout is AuthResult.Error)
        assertTrue((duringLockout as AuthResult.Error).lockoutRemainingSeconds > 0)

        // Advance clock past 30s cooldown
        currentTime += VaultAuthRepository.LOCKOUT_DURATION_MS + 1000L
        assertEquals(AuthResult.Success, repo.login("4829", "StrongPass1"))
    }

    @Test
    fun updateCredentials_requiresCurrentPasswordAndInvalidatesOldOne() = runTest {
        val repo = createRepo()
        assertEquals(AuthResult.Success, repo.register("112233", "OldPass123", "OldPass123"))

        val badCurrent = repo.updateCredentials("WrongOld", "998877", "NewPass123", "NewPass123")
        assertTrue(badCurrent is AuthResult.Error)

        val updated = repo.updateCredentials("OldPass123", "998877", "NewPass123", "NewPass123")
        assertEquals(AuthResult.Success, updated)

        assertTrue(repo.login("112233", "OldPass123") is AuthResult.Error)
        assertEquals(AuthResult.Success, repo.login("998877", "NewPass123"))
    }

    @Test
    fun resetAccount_clearsRegistrationState() = runTest {
        val repo = createRepo()
        assertEquals(AuthResult.Success, repo.register("556677", "Pass5566", "Pass5566"))
        assertTrue(repo.authStateFlow.first().isAccountRegistered)

        repo.resetAccount()
        assertFalse(repo.authStateFlow.first().isAccountRegistered)
    }
}
