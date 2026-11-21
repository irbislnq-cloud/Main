package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.diagnostics.AppLogBuffer
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

private const val TAG = "Auth"
const val EXPECTED_FIREBASE_SHA1 = "56:EB:0C:7A:A4:55:1D:E7:7B:51:E0:C8:FF:AF:D0:8E:9F:E0:21:B0"

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
fun readApkSigningSha1(context: Context): String? = runCatching {
    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
    } else {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES
        )
    }
    val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
    } else {
        pkgInfo.signatures?.firstOrNull()?.toByteArray()
    } ?: return@runCatching null

    val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
    digest.joinToString(":") { "%02X".format(it) }
}.getOrNull()

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Suppress("DiscouragedApi")
private fun resolveDefaultWebClientId(context: Context): String? {
    return runCatching {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        ).takeIf { it != 0 }
            ?: context.resources.getIdentifier(
                "default_web_client_id",
                "string",
                "com.example"
            )
        if (resId == 0) null else context.getString(resId)
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        AppLogBuffer.w(TAG, "Auto sign-in skipped: FirebaseApp not initialized")
        onUnauthenticated()
        return
    }
    val existingUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    if (existingUser != null) {
        AppLogBuffer.i(TAG, "Existing Firebase session found")
        onAuthSuccess()
        return
    }
    if (credentialManager == null) {
        AppLogBuffer.w(TAG, "Auto sign-in skipped: CredentialManager unavailable")
        onUnauthenticated()
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
        AppLogBuffer.w(TAG, "default_web_client_id missing; manual sign-in or offline mode required")
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val credContext = context.findActivity() ?: context
            val response = withTimeoutOrNull(2500L) {
                credentialManager.getCredential(credContext, request)
            }
            if (response == null) {
                AppLogBuffer.d(TAG, "Auto sign-in timed out")
                onUnauthenticated()
                return@launch
            }
            val cred = response.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                AppLogBuffer.i(TAG, "Auto sign-in succeeded")
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppLogBuffer.d(TAG, "Auto sign-in did not complete: ${t.message}")
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (diagnosticHint: String?) -> Unit = {}
) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        onAuthError("Firebase не инициализирован. Проверьте google-services.json или используйте автономный режим.")
        return
    }
    if (credentialManager == null) {
        onAuthError("Credential Manager недоступен на этом устройстве. Используйте автономный режим.")
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
        onAuthError("Конфигурация Google Sign-In не найдена (google-services.json / default_web_client_id).")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Не найден активный экран (Activity) для открытия окна входа Google.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                AppLogBuffer.i(TAG, "Google Sign-In succeeded")
                onAuthSuccess()
            } else {
                onAuthError("Неожиданный тип учётных данных Google.")
            }
        } catch (e: GetCredentialCancellationException) {
            val actualSha1 = readApkSigningSha1(activity)
            val shaMismatch = actualSha1 != null && !actualSha1.equals(EXPECTED_FIREBASE_SHA1, ignoreCase = true)
            val hint = if (shaMismatch) {
                "Вход Google отклонён (Developer Error / SHA-1 не совпадает). " +
                    "Подпись этого APK из GitHub: $actualSha1, а в Firebase зарегистрирован $EXPECTED_FIREBASE_SHA1. " +
                    "Добавьте $actualSha1 в Firebase Console или соберите с общим debug.keystore. Локальный режим и бесплатные AI работают и без облака!"
            } else {
                "Окно выбора Google-аккаунта закрыто или отклонено системой (${e.message ?: "RESULT_CANCELED"})."
            }
            AppLogBuffer.w(TAG, "Google Sign-In cancelled/rejected (apkSha1=$actualSha1): ${e.message}", e)
            onAuthCancelled(hint)
        } catch (e: NoCredentialException) {
            val actualSha1 = readApkSigningSha1(activity)
            val msg = buildString {
                append("Google Play Services не вернул аккаунтов (NoCredentialException). ")
                append("Проверьте наличие Google-аккаунта на устройстве. ")
                if (actualSha1 != null && !actualSha1.equals(EXPECTED_FIREBASE_SHA1, ignoreCase = true)) {
                    append("Внимание: SHA-1 этого APK ($actualSha1) отличается от ключа в Firebase ($EXPECTED_FIREBASE_SHA1).")
                }
            }
            AppLogBuffer.e(TAG, msg, e)
            onAuthError(msg)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val actualSha1 = readApkSigningSha1(activity)
            AppLogBuffer.e(TAG, "Google Sign-In failed (apkSha1=$actualSha1)", t)
            val raw = t.localizedMessage ?: t::class.java.simpleName
            val hint = if (actualSha1 != null && !actualSha1.equals(EXPECTED_FIREBASE_SHA1, ignoreCase = true)) {
                "$raw\n(SHA-1 текущего APK: $actualSha1 — отличается от Firebase $EXPECTED_FIREBASE_SHA1)"
            } else {
                raw
            }
            onAuthError(hint)
        }
    }
}

fun signOut(
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager?.clearCredentialState(ClearCredentialStateRequest())
            } catch (t: Throwable) {
                AppLogBuffer.w(TAG, "Failed to clear credential state", t)
            } finally {
                runCatching { Firebase.auth.signOut() }
                AppLogBuffer.i(TAG, "User signed out")
                onSignOutComplete()
            }
        }
    }
}
