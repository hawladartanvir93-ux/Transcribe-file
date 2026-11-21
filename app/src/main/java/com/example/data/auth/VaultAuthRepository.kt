package com.example.data.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.vaultAuthDataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_auth_store")

data class VaultAuthSnapshot(
    val isAccountRegistered: Boolean = false,
    val maskedNumberHint: String = "",
    val failedAttempts: Int = 0,
    val lockoutUntilMs: Long = 0L,
    val isLoaded: Boolean = false,
)

sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val message: String, val lockoutRemainingSeconds: Int = 0) : AuthResult
}

class VaultAuthRepository(
    private val dataStore: DataStore<Preferences>,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private object Keys {
        val REGISTERED = booleanPreferencesKey("auth_registered")
        val NORMALIZED_NUMBER = stringPreferencesKey("auth_number")
        val SALT_B64 = stringPreferencesKey("auth_salt_b64")
        val HASH_B64 = stringPreferencesKey("auth_hash_b64")
        val KDF_ALGO = stringPreferencesKey("auth_kdf_algo")
        val FAILED_ATTEMPTS = intPreferencesKey("auth_failed_attempts")
        val LOCKOUT_UNTIL_MS = longPreferencesKey("auth_lockout_until_ms")
    }

    val authStateFlow: Flow<VaultAuthSnapshot> = dataStore.data.map { prefs ->
        val registered = prefs[Keys.REGISTERED] ?: false
        val num = prefs[Keys.NORMALIZED_NUMBER] ?: ""
        VaultAuthSnapshot(
            isAccountRegistered = registered && num.isNotEmpty() && !prefs[Keys.HASH_B64].isNullOrEmpty(),
            maskedNumberHint = maskNumber(num),
            failedAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
            lockoutUntilMs = prefs[Keys.LOCKOUT_UNTIL_MS] ?: 0L,
            isLoaded = true,
        )
    }

    suspend fun register(
        numberInput: String,
        passwordInput: String,
        confirmPasswordInput: String,
    ): AuthResult {
        val numberError = validateNumber(numberInput)
        if (numberError != null) return AuthResult.Error(numberError)

        val passwordError = validatePassword(passwordInput)
        if (passwordError != null) return AuthResult.Error(passwordError)

        if (passwordInput != confirmPasswordInput) {
            return AuthResult.Error("Passwords do not match.")
        }

        val normalized = normalizeNumber(numberInput)
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val (algo, hash) = deriveKey(passwordInput.toCharArray(), salt, preferredAlgo = DEFAULT_KDF)

        dataStore.edit { prefs ->
            prefs[Keys.REGISTERED] = true
            prefs[Keys.NORMALIZED_NUMBER] = normalized
            prefs[Keys.SALT_B64] = encodeB64(salt)
            prefs[Keys.HASH_B64] = encodeB64(hash)
            prefs[Keys.KDF_ALGO] = algo
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
        return AuthResult.Success
    }

    suspend fun login(numberInput: String, passwordInput: String): AuthResult {
        val prefs = dataStore.data.first()
        val isRegistered = prefs[Keys.REGISTERED] == true
        val storedNumber = prefs[Keys.NORMALIZED_NUMBER].orEmpty()
        val storedSaltB64 = prefs[Keys.SALT_B64].orEmpty()
        val storedHashB64 = prefs[Keys.HASH_B64].orEmpty()
        val algo = prefs[Keys.KDF_ALGO] ?: DEFAULT_KDF

        if (!isRegistered || storedNumber.isEmpty() || storedSaltB64.isEmpty() || storedHashB64.isEmpty()) {
            return AuthResult.Error("No local vault account is set up yet.")
        }

        val now = clockMs()
        val lockoutUntil = prefs[Keys.LOCKOUT_UNTIL_MS] ?: 0L
        if (lockoutUntil > now) {
            val remainingSec = (((lockoutUntil - now) + 999L) / 1000L).toInt().coerceAtLeast(1)
            return AuthResult.Error(
                message = "Too many failed attempts. Try again in ${remainingSec}s.",
                lockoutRemainingSeconds = remainingSec,
            )
        }

        val normalizedInput = normalizeNumber(numberInput)
        if (normalizedInput.length < MIN_NUMBER_DIGITS || passwordInput.isEmpty()) {
            return AuthResult.Error("Enter both your registered number and password.")
        }

        val salt = decodeB64(storedSaltB64)
        val expectedHash = decodeB64(storedHashB64)
        val (_, candidateHash) = deriveKey(passwordInput.toCharArray(), salt, preferredAlgo = algo)

        val numberMatches = MessageDigest.isEqual(
            normalizedInput.toByteArray(Charsets.UTF_8),
            storedNumber.toByteArray(Charsets.UTF_8),
        )
        val passwordMatches = MessageDigest.isEqual(candidateHash, expectedHash)

        return if (numberMatches && passwordMatches) {
            dataStore.edit {
                it[Keys.FAILED_ATTEMPTS] = 0
                it[Keys.LOCKOUT_UNTIL_MS] = 0L
            }
            AuthResult.Success
        } else {
            val currentFailures = (prefs[Keys.FAILED_ATTEMPTS] ?: 0) + 1
            val shouldLock = currentFailures >= MAX_FAILED_ATTEMPTS
            val nextLockoutMs = if (shouldLock) now + LOCKOUT_DURATION_MS else 0L
            dataStore.edit {
                it[Keys.FAILED_ATTEMPTS] = if (shouldLock) 0 else currentFailures
                it[Keys.LOCKOUT_UNTIL_MS] = nextLockoutMs
            }
            if (shouldLock) {
                val sec = (LOCKOUT_DURATION_MS / 1000L).toInt()
                AuthResult.Error(
                    message = "Incorrect credentials. Locked for ${sec}s to protect your data.",
                    lockoutRemainingSeconds = sec,
                )
            } else {
                val left = MAX_FAILED_ATTEMPTS - currentFailures
                AuthResult.Error("Incorrect number or password ($left attempts left before cooldown).")
            }
        }
    }

    suspend fun updateCredentials(
        currentPasswordInput: String,
        newNumberInput: String,
        newPasswordInput: String,
        confirmNewPasswordInput: String,
    ): AuthResult {
        val prefs = dataStore.data.first()
        val storedNumber = prefs[Keys.NORMALIZED_NUMBER].orEmpty()
        val storedSaltB64 = prefs[Keys.SALT_B64].orEmpty()
        val storedHashB64 = prefs[Keys.HASH_B64].orEmpty()
        val algo = prefs[Keys.KDF_ALGO] ?: DEFAULT_KDF

        if (storedNumber.isEmpty() || storedSaltB64.isEmpty() || storedHashB64.isEmpty()) {
            return AuthResult.Error("Account not initialized.")
        }

        val (_, checkHash) = deriveKey(
            currentPasswordInput.toCharArray(),
            decodeB64(storedSaltB64),
            preferredAlgo = algo,
        )
        if (!MessageDigest.isEqual(checkHash, decodeB64(storedHashB64))) {
            return AuthResult.Error("Current password is incorrect.")
        }

        return register(newNumberInput, newPasswordInput, confirmNewPasswordInput)
    }

    suspend fun resetAccount() {
        dataStore.edit { it.clear() }
    }

    fun normalizeNumber(raw: String): String = raw.filter { it.isDigit() }

    private fun validateNumber(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "Please enter your number."
        if (trimmed.any { it.isLetter() }) return "Number field can only contain digits (and optional + or spaces)."
        val digits = normalizeNumber(trimmed)
        if (digits.length < MIN_NUMBER_DIGITS) return "Number must be at least $MIN_NUMBER_DIGITS digits."
        if (digits.length > MAX_NUMBER_DIGITS) return "Number cannot exceed $MAX_NUMBER_DIGITS digits."
        return null
    }

    private fun validatePassword(password: String): String? {
        if (password.length < MIN_PASSWORD_LENGTH) {
            return "Password must be at least $MIN_PASSWORD_LENGTH characters."
        }
        if (password.isBlank()) {
            return "Password cannot be only whitespace."
        }
        return null
    }

    private fun maskNumber(digits: String): String {
        if (digits.isEmpty()) return ""
        val visible = digits.takeLast(minOf(4, digits.length))
        return "••••$visible"
    }

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        preferredAlgo: String,
    ): Pair<String, ByteArray> {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(preferredAlgo)
            preferredAlgo to factory.generateSecret(spec).encoded
        } catch (_: NoSuchAlgorithmException) {
            val fallback = SecretKeyFactory.getInstance(FALLBACK_KDF)
            FALLBACK_KDF to fallback.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodeB64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decodeB64(raw: String): ByteArray = Base64.decode(raw, Base64.NO_WRAP)

    companion object {
        private const val SALT_BYTES = 16
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val DEFAULT_KDF = "PBKDF2WithHmacSHA256"
        private const val FALLBACK_KDF = "PBKDF2WithHmacSHA1"
        const val MIN_NUMBER_DIGITS = 4
        const val MAX_NUMBER_DIGITS = 15
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30_000L

        @Volatile
        private var INSTANCE: VaultAuthRepository? = null

        fun getInstance(context: Context): VaultAuthRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VaultAuthRepository(context.applicationContext.vaultAuthDataStore).also {
                    INSTANCE = it
                }
            }
        }
    }
}
