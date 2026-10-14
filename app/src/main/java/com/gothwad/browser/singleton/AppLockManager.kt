package com.gothwad.browser.singleton

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.gothwad.browser.BrowserApp
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App Lock (PIN) storage.
 *
 * History of this file (do not regress it):
 *  - the PIN used to be stored as **plaintext**, twice: in `SharedPreferences` under a key that was
 *    optimistically called `app_lock_pin_hash` and again in `files/app_lock_pin.dat`. Anyone with
 *    `adb run-as`/a backup of the app data, or any app that could read the backup, got the PIN.
 *  - the "session unlocked" marker was a file containing a timestamp that was **never checked**,
 *    so the very first successful unlock unlocked the lock for all future launches, permanently -
 *    i.e. the feature was decorative.
 *  - there was no limit on guesses: a 4-digit PIN is 10 000 combinations, trivially walkable with a
 *    TV remote or `adb shell input`.
 *
 * Now: PBKDF2-HMAC-SHA256 with a per-install random salt, constant-time comparison, a failed-attempt
 * lockout with exponential back-off, and a session grant that *expires*.
 *
 * The public API is unchanged so `AppLockActivity` / `TvPinDialog` / `MainActivity` keep working;
 * legacy plaintext records are migrated transparently on the next successful verification.
 */
object AppLockManager {
    private const val TAG = "AppLockManager"

    private const val PREFS_NAME = "tv_browser_app_lock_prefs"
    private const val KEY_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_PIN_RECORD = "app_lock_pin"
    private const val KEY_FAILED_ATTEMPTS = "app_lock_failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "app_lock_lockout_until"

    private const val RECORD_SCHEME = "pbkdf2"
    private const val PBKDF2_ITERATIONS = 25000
    private const val PBKDF2_KEY_BITS = 256
    private const val SALT_BYTES = 16

    /** Files kept only to migrate pre-hash installs, then deleted. */
    private const val FILE_PIN = "app_lock_pin.dat"
    private const val FILE_ENABLED = "app_lock_enabled.flag"
    private const val FILE_SESSION_TOKEN = "app_lock_session.token"

    /**
     * How long an unlocked session stays valid across processes/relaunches. Long enough that
     * jumping to the incognito process or restoring from Recents does not ask twice, short enough
     * that "locked again after you left the app" is actually true.
     */
    private const val SESSION_GRACE_MS = 60_000L

    private const val LOCKOUT_BASE_MS = 15_000L
    private const val LOCKOUT_MAX_MS = 15L * 60L * 1000L
    private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5

    @Volatile
    private var isSessionUnlocked = false

    /**
     * The lock state is read on every `onResume`. SharedPreferences are in-memory, but the
     * pre-prefs installs also left flag files, and `File.exists()` on the UI thread is exactly the
     * kind of thing AGENTS.md rule 6 forbids - so the answer is cached and only invalidated by the
     * methods that change it.
     */
    @Volatile
    private var lockEnabledCache: Boolean? = null

    @Volatile
    private var legacyPin: String? = null

    @Volatile
    private var legacyPinChecked = false

    private fun getFilesDir(context: Context? = null): File? {
        return try {
            context?.filesDir ?: BrowserApp.instance.filesDir
        } catch (_: Throwable) {
            null
        }
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- PIN storage

    private fun derive(pin: String, saltB64: String): String {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
    }

    private fun isLegacyPlaintext(record: String): Boolean =
        record.length == 4 && record.all { it.isDigit() }

    /** @return true when a usable PIN exists (hashed, or a legacy plaintext record). */
    fun hasPinSet(context: Context): Boolean {
        val record = getPrefs(context).getString(KEY_PIN_RECORD, null)
        if (!record.isNullOrEmpty()) {
            if (isLegacyPlaintext(record)) return true
            val parts = record.split(':')
            return parts.size == 4 && parts[0] == RECORD_SCHEME && parts[3].isNotEmpty()
        }
        // Migration source only: a pre-hash install still has the raw file.
        return legacyPinOf(context) != null
    }

    private fun legacyPinOf(context: Context): String? {
        if (legacyPinChecked) return legacyPin
        val pin = readLegacyPinFile(context)
        legacyPin = pin
        legacyPinChecked = true
        return pin
    }

    private fun readLegacyPinFile(context: Context): String? {
        val pinFile = getFilesDir(context)?.let { File(it, FILE_PIN) } ?: return null
        if (!pinFile.exists()) return null
        return try {
            pinFile.readText().trim().takeIf { it.length == 4 && it.all { c -> c.isDigit() } }
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeRecord(context: Context, pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hash = derive(pin, saltB64)
        getPrefs(context).edit()
            .putString(KEY_PIN_RECORD, "$RECORD_SCHEME:$PBKDF2_ITERATIONS:$saltB64:$hash")
            .apply()
    }

    fun setPin(context: Context, pin: String) {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return
        writeRecord(context, pin)
        // Remove the plaintext leftovers from older versions, including the one this class used to write.
        try {
            getFilesDir(context)?.let { File(it, FILE_PIN).delete() }
        } catch (_: Throwable) {}
        resetFailures(context)
    }

    fun clearPinAndDisableLock(context: Context) {
        lockEnabledCache = false
        legacyPin = null
        legacyPinChecked = false
        getPrefs(context).edit()
            .remove(KEY_PIN_RECORD)
            .putBoolean(KEY_LOCK_ENABLED, false)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
        try {
            getFilesDir(context)?.let { dir ->
                File(dir, FILE_PIN).delete()
                File(dir, FILE_ENABLED).delete()
            }
        } catch (_: Throwable) {}
        setSessionUnlocked(context, true)
    }

    // ---------------------------------------------------------------- verification + lockout

    fun isLockedOut(context: Context): Boolean = lockoutRemainingMs(context) > 0

    fun lockoutRemainingMs(context: Context): Long {
        val until = getPrefs(context).getLong(KEY_LOCKOUT_UNTIL, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun resetFailures(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }

    private fun registerFailure(context: Context): Long {
        val prefs = getPrefs(context)
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
        var remaining = 0L
        if (attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            val exponent = (attempts - MAX_ATTEMPTS_BEFORE_LOCKOUT).coerceAtMost(5)
            val wait = (LOCKOUT_BASE_MS shl exponent).coerceAtMost(LOCKOUT_MAX_MS)
            editor.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + wait)
            remaining = wait
        }
        editor.apply()
        return remaining
    }

    /**
     * @param lockoutMs when the PIN is now rate-limited, how long the caller must wait before the
     *                  next attempt (0 = allow immediately).
     */
    class VerifyResult(val success: Boolean, val lockoutMs: Long, val legacyMigrated: Boolean)

    fun verifyPinDetailed(context: Context, inputPin: String): VerifyResult {
        val remaining = lockoutRemainingMs(context)
        if (remaining > 0) return VerifyResult(false, remaining, false)

        val prefs = getPrefs(context)
        var record = prefs.getString(KEY_PIN_RECORD, null)
        if (record.isNullOrEmpty()) {
            val legacyFile = readLegacyPinFile(context)
            if (legacyFile == null) return VerifyResult(false, 0, false)
            if (legacyFile == inputPin) {
                migrateToHash(context, legacyFile)
                return VerifyResult(true, 0, true)
            }
            registerFailure(context)
            return VerifyResult(false, 0, false)
        }

        if (isLegacyPlaintext(record)) {
            // Very old installs stored the PIN itself. Compare, then upgrade in place.
            if (constantTimeEquals(record, inputPin)) {
                migrateToHash(context, inputPin)
                return VerifyResult(true, 0, true)
            }
            val wait = registerFailure(context)
            return VerifyResult(false, wait, false)
        }

        val parts = record.split(':')
        if (parts.size != 4 || parts[0] != RECORD_SCHEME) {
            // Unreadable record (should not happen): fail closed but let the user set a new PIN.
            Log.w(TAG, "Corrupt app-lock record, refusing unlock")
            return VerifyResult(false, 0, false)
        }
        val ok = try {
            constantTimeEquals(derive(inputPin, parts[2]), parts[3])
        } catch (e: Throwable) {
            Log.w(TAG, "PBKDF2 unavailable on this device", e)
            false
        }
        if (ok) {
            resetFailures(context)
            return VerifyResult(true, 0, false)
        }
        return VerifyResult(false, registerFailure(context), false)
    }

    private fun migrateToHash(context: Context, pin: String) {
        try {
            writeRecord(context, pin)
            getFilesDir(context)?.let { File(it, FILE_PIN).delete() }
            resetFailures(context)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to migrate legacy PIN storage", e)
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    /** Back-compatible boolean form used by the dialogs. */
    fun verifyPin(context: Context, inputPin: String): Boolean =
        verifyPinDetailed(context, inputPin).success

    // ---------------------------------------------------------------- enable / session

    fun isLockEnabled(context: Context): Boolean {
        if (!hasPinSet(context)) return false
        lockEnabledCache?.let { return it }
        val enabled = if (getPrefs(context).getBoolean(KEY_LOCK_ENABLED, false)) {
            true
        } else {
            val flagFile = getFilesDir(context)?.let { File(it, FILE_ENABLED) }
            flagFile?.exists() == true
        }
        lockEnabledCache = enabled
        return enabled
    }

    fun setLockEnabled(context: Context, enabled: Boolean) {
        if (enabled && !hasPinSet(context)) return
        lockEnabledCache = enabled
        getPrefs(context).edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
        val flagFile = getFilesDir(context)?.let { File(it, FILE_ENABLED) }
        try {
            if (enabled) {
                flagFile?.writeText("true")
            } else {
                flagFile?.delete()
                setSessionUnlocked(context, true)
            }
        } catch (_: Throwable) {}
    }

    fun isSessionUnlocked(context: Context? = null): Boolean {
        if (isSessionUnlocked) return true
        val file = getFilesDir(context)?.let { File(it, FILE_SESSION_TOKEN) } ?: return false
        if (!file.exists()) return false
        return try {
            val expiresAt = file.readText().trim().toLongOrNull() ?: 0L
            if (System.currentTimeMillis() < expiresAt) {
                isSessionUnlocked = true
                true
            } else {
                file.delete()
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun setSessionUnlocked(context: Context?, unlocked: Boolean) {
        isSessionUnlocked = unlocked
        val file = getFilesDir(context)?.let { File(it, FILE_SESSION_TOKEN) } ?: return
        try {
            if (unlocked) {
                file.writeText((System.currentTimeMillis() + SESSION_GRACE_MS).toString())
            } else {
                file.delete()
            }
        } catch (_: Throwable) {}
    }

    /** Same grace window for the no-context overload used by menu actions. */
    fun setSessionUnlocked(unlocked: Boolean) = setSessionUnlocked(null as Context?, unlocked)

    fun requiresUnlock(context: Context): Boolean = isLockEnabled(context) && !isSessionUnlocked(context)
}
