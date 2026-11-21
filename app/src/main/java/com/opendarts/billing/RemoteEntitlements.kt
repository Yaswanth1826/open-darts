package com.opendarts.billing

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.security.MessageDigest
import java.util.UUID

/**
 * Who the player is and what they may do, decided by the server (backend/functions): Google account, free-game counter and
 * Play subscription. The phone keeps a copy of the last answer so screens answer instantly, and every camera game that
 * starts is reported to the server, so clearing the app's data cannot bring free games back.
 * Debug builds count as subscribed so testing is never blocked, unless [simulateFreePlan] is on.
 */
class RemoteEntitlements(context: Context) : EntitlementProvider {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("opendarts_access", Context.MODE_PRIVATE)
    private val testPrefs = app.getSharedPreferences("opendarts_plan", Context.MODE_PRIVATE)

    val isDebugBuild = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val auth: FirebaseAuth? = runCatching {
        val firebase = FirebaseApp.getApps(app).firstOrNull() ?: FirebaseApp.initializeApp(app)
        firebase?.let { AppCheckSetup.install(it) }
        firebase?.let { FirebaseAuth.getInstance(it) }
    }.getOrNull()

    private val webClientId: String? = app.resources.getIdentifier("default_web_client_id", "string", app.packageName)
        .takeIf { it != 0 }?.let(app::getString)

    /** False in builds made without the Firebase configuration: there is no account to sign in to. */
    val available: Boolean get() = auth != null && webClientId != null

    private var revision by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    var simulateFreePlan: Boolean
        get() = testPrefs.getBoolean("simulate_free", false)
        set(value) { testPrefs.edit().putBoolean("simulate_free", value).apply(); revision++ }

    val email: String? get() { revision; return auth?.currentUser?.email }
    val signedIn: Boolean get() { revision; return auth?.currentUser != null }

    /** Hash of the account id, attached to purchases so the server can tell whose purchase it is. */
    fun accountHash(): String? = auth?.currentUser?.uid?.let(::sha256)

    override fun access(): Access {
        revision
        if (isDebugBuild && !simulateFreePlan) return Access(premium = true, freeLeft = FREE_CAMERA_GAMES)
        val user = auth?.currentUser ?: return Access(premium = false, freeLeft = 0, signedIn = false)
        if (prefs.getString(UID, null) != user.uid) return Access(premium = false, freeLeft = 0, known = false)
        return Access(premium = prefs.getLong(UNTIL, 0) > System.currentTimeMillis(), freeLeft = prefs.getInt(FREE, 0))
    }

    override fun useFreeGame() {
        if (access().premium) return
        prefs.edit().putInt(FREE, (prefs.getInt(FREE, 0) - 1).coerceAtLeast(0)).putInt(PENDING, prefs.getInt(PENDING, 0) + 1).apply()
        revision++
        flush { }
    }

    /** Asks the server for the current plan (after reporting any games started while offline). Safe to call any time. */
    fun refresh() {
        val user = auth?.currentUser ?: return
        claimed {
            flush { call("getAccess", device()) { store(user.uid, it) } }
        }
    }

    /**
     * Only one sign-in may use an account at a time. This device makes itself that one (the server then signs the others out)
     * before anything else is asked of the server; if that cannot be reached yet, nothing else is sent.
     */
    private fun claimed(then: () -> Unit) {
        if (prefs.getBoolean(CLAIMED, false) && prefs.getString(SESSION, null) != null) return then()
        val id = prefs.getString(SESSION, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(SESSION, it).apply() }
        call("claimSession", device()) {
            prefs.edit().putBoolean(CLAIMED, true).apply()
            then()
        }
    }

    /** The server says the account was signed in somewhere else since: this device is signed out. */
    private fun replacedElsewhere() {
        auth?.signOut()
        forgetSession()
        busy = false
        revision++
        message = "You signed in on another device, so this one was signed out. Sign in again to use it here."
    }

    private fun forgetSession() {
        prefs.edit().remove(UID).remove(UNTIL).remove(FREE).remove(PENDING).remove(SESSION).remove(CLAIMED).apply()
    }

    private fun flush(then: () -> Unit) {
        val user = auth?.currentUser ?: return
        if (prefs.getInt(PENDING, 0) <= 0) { then(); return }
        call("startCameraGame", device()) { answer ->
            prefs.edit().putInt(PENDING, (prefs.getInt(PENDING, 0) - 1).coerceAtLeast(0)).apply()
            store(user.uid, answer)
            flush(then)
        }
    }

    /** Keeps the server's answer, minus the games already started that it has not heard about yet. */
    private fun store(uid: String, answer: Map<*, *>) {
        val premiumUntil = (answer["premiumUntil"] as? Number)?.toLong() ?: 0L
        val free = ((answer["freeLeft"] as? Number)?.toInt() ?: 0) - prefs.getInt(PENDING, 0)
        prefs.edit().putString(UID, uid).putLong(UNTIL, premiumUntil).putInt(FREE, free.coerceAtLeast(0)).apply()
        revision++
    }

    /** Hands a Play purchase token to the server, which checks it with Google and unlocks the camera for this account. */
    fun verifyPurchase(token: String, done: (Boolean) -> Unit = {}) {
        val user = auth?.currentUser ?: return done(false)
        call("verifyPurchase", device().plus("purchaseToken" to token), onFail = { done(false) }) { answer ->
            val ok = answer["ok"] == true
            if (ok) {
                prefs.edit().putString(UID, user.uid).putLong(UNTIL, (answer["until"] as? Number)?.toLong() ?: 0L).apply()
                revision++
            }
            done(ok)
        }
    }

    fun signIn(activity: Activity) {
        val clientId = webClientId ?: return
        val firebase = auth ?: return
        busy = true; message = null
        val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
        CredentialManager.create(activity).getCredentialAsync(
            activity, request, null, ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential
                    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        busy = false; message = "That sign-in method is not supported."
                        return
                    }
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    firebase.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                        .addOnSuccessListener {
                            // A new sign-in is a new session, which replaces any other device on this account.
                            prefs.edit().putString(SESSION, UUID.randomUUID().toString()).putBoolean(CLAIMED, false).apply()
                            busy = false; revision++; refresh()
                        }
                        .addOnFailureListener { busy = false; message = "Sign-in failed. Check your connection and try again." }
                }

                override fun onError(e: GetCredentialException) {
                    busy = false
                    if (e !is GetCredentialCancellationException) message = "Sign-in failed. Check your connection and try again."
                }
            },
        )
    }

    fun signOut(activity: Activity) {
        auth?.signOut()
        forgetSession()
        revision++
        CredentialManager.create(activity).clearCredentialStateAsync(
            ClearCredentialStateRequest(), null, ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<Void?, ClearCredentialException> {
                override fun onResult(result: Void?) {}
                override fun onError(e: ClearCredentialException) {}
            },
        )
    }

    /** Deletes the account and its server data. The device's free-game count stays, so deleting is no way to get a new trial. */
    fun deleteAccount(activity: Activity) {
        busy = true; message = null
        call("deleteAccount", device(), onFail = { busy = false; message = "Could not delete the account. Try again when you are online." }) {
            busy = false
            signOut(activity)
            message = "Your account was deleted."
        }
    }

    private fun device(): Map<String, Any> {
        val session = prefs.getString(SESSION, null)
        val id = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)?.takeIf { it.length >= 8 }
            ?: prefs.getString(FALLBACK_DEVICE, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(FALLBACK_DEVICE, it).apply() }
        return if (session != null) mapOf("deviceId" to id, "sessionId" to session) else mapOf("deviceId" to id)
    }

    private fun call(name: String, data: Map<String, Any>, onFail: (Exception) -> Unit = {}, onOk: (Map<*, *>) -> Unit) {
        runCatching {
            FirebaseFunctions.getInstance(REGION).getHttpsCallable(name).call(data)
                .addOnSuccessListener { onOk(it.getData() as? Map<*, *> ?: emptyMap<Any, Any>()) }
                .addOnFailureListener { e ->
                    val code = (e as? FirebaseFunctionsException)?.code
                    when {
                        code == FirebaseFunctionsException.Code.FAILED_PRECONDITION && e.message == "session-replaced" -> { onFail(e); replacedElsewhere() }
                        code == FirebaseFunctionsException.Code.FAILED_PRECONDITION && e.message == "app-check" -> {
                            onFail(e)
                            message = "This copy of Open Darts could not be verified. Install it from Google Play."
                        }
                        else -> onFail(e)
                    }
                }
        }.onFailure { onFail(it as? Exception ?: Exception(it)) }
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val REGION = "europe-west1"
        const val UID = "uid"
        const val UNTIL = "until"
        const val FREE = "free"
        const val PENDING = "pending"
        const val FALLBACK_DEVICE = "device"
        const val SESSION = "session"
        const val CLAIMED = "claimed"
    }
}
