package app.openhealth.android.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/** Why an unlock did not succeed, in terms the lock screen can state plainly. */
enum class LockError { NO_ENROLMENT, LOCKED_OUT, CANCELLED, FAILED }

/** The three strings the platform prompt shows; the app owns their copy. */
data class LockPrompt(val title: String, val subtitle: String, val cancel: String)

/**
 * The app lock of `pr.md` §6.23 and `DESIGN.md` §14.2, on the platform BiometricPrompt
 * (API 28+). It never sees or stores a biometric: the platform authenticates and only
 * tells the app yes or no. Device credential is allowed where the platform supports it,
 * so a phone with a PIN and no fingerprint can still lock the app.
 */
class BiometricGate {
    fun available(context: Context): Boolean = hasDeviceCredential(context)

    fun authenticate(
        activity: Activity,
        prompt: LockPrompt,
        onSuccess: () -> Unit,
        onError: (LockError) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onError(errorOf(errorCode))
            }
        }
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(prompt.title)
            .setSubtitle(prompt.subtitle)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                builder.setDeviceCredentialAllowed(true)
            else -> builder.setNegativeButton(
                prompt.cancel,
                executor,
                DialogInterface.OnClickListener { _, _ -> },
            )
        }
        builder.build().authenticate(CancellationSignal(), executor, callback)
    }

    private fun hasDeviceCredential(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    private fun errorOf(code: Int): LockError = when (code) {
        BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
        BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT,
        BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE,
        -> LockError.NO_ENROLMENT
        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
        -> LockError.LOCKED_OUT
        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
        BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
        BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
        -> LockError.CANCELLED
        else -> LockError.FAILED
    }
}
