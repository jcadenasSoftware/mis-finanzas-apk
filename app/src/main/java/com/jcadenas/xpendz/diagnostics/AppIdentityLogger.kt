package com.jcadenas.xpendz.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.jcadenas.xpendz.R
import java.security.MessageDigest

object AppIdentityLogger {
    private const val TAG = "XPENDZ_DIAGNOSTIC"

    // TEMP DIAGNOSTIC
    fun logApplicationIdentity(context: Context) {
        try {
            Log.e("XPENDZ_STARTUP", "***** logApplicationIdentity() *****")
            Log.d(TAG, "========== XPENDZ DIAGNOSTIC ==========")
            Log.d(TAG, "[APP] BuildConfig.APPLICATION_ID=${readBuildConfigField("APPLICATION_ID")}")
            Log.d(TAG, "[APP] BuildConfig.VERSION_NAME=${readBuildConfigField("VERSION_NAME")}")
            Log.d(TAG, "[APP] BuildConfig.VERSION_CODE=${readBuildConfigField("VERSION_CODE")}")
            Log.d(TAG, "[APP] packageName=${context.packageName}")
            Log.d(TAG, "[APP] default_web_client_id=${context.getString(R.string.default_web_client_id)}")

            logInstallerInfo(context)
            logPackageInfo(context)
            logSigningCertificates(context)
        } catch (e: Exception) {
            Log.e("XPENDZ_STARTUP", "Identity logger failed", e)
        }
    }

    // TEMP DIAGNOSTIC
    fun logGoogleSignInBuilder(
        source: String,
        defaultWebClientId: String,
        requestEmail: Boolean,
        requestIdToken: Boolean,
        extraConfig: List<String> = emptyList()
    ) {
        Log.d(TAG, "[GSI-BUILDER] source=$source")
        Log.d(TAG, "[GSI-BUILDER] GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)")
        Log.d(TAG, "[GSI-BUILDER] requestEmail()=$requestEmail")
        Log.d(TAG, "[GSI-BUILDER] requestIdToken(default_web_client_id)=$requestIdToken")
        Log.d(TAG, "[GSI-BUILDER] default_web_client_id=$defaultWebClientId")
        if (extraConfig.isEmpty()) {
            Log.d(TAG, "[GSI-BUILDER] extraConfig=<none>")
        } else {
            extraConfig.forEachIndexed { index, item ->
                Log.d(TAG, "[GSI-BUILDER] extraConfig[$index]=$item")
            }
        }
    }

    // TEMP DIAGNOSTIC
    fun logIntentDiagnostics(source: String, intent: Intent?) {
        Log.d(TAG, "[INTENT] source=$source")
        if (intent == null) {
            Log.d(TAG, "[INTENT] intent is NULL")
            return
        }

        Log.d(TAG, "[INTENT] action=${intent.action}")
        Log.d(TAG, "[INTENT] data=${intent.data}")
        Log.d(TAG, "[INTENT] type=${intent.type}")
        Log.d(TAG, "[INTENT] flags=${intent.flags}")
        val extras = intent.extras
        if (extras == null) {
            Log.d(TAG, "[INTENT] extras=NULL")
        } else {
            Log.d(TAG, "[INTENT] extrasKeys=${extras.keySet()}")
            Log.d(TAG, "[INTENT] extras=$extras")
        }
    }

    // TEMP DIAGNOSTIC
    fun logApiException(source: String, exception: ApiException) {
        Log.e(TAG, "[API_EXCEPTION] source=$source")
        Log.e(TAG, "[API_EXCEPTION] statusCode=${exception.statusCode}")
        Log.e(TAG, "[API_EXCEPTION] status=${exception.status}")
        Log.e(TAG, "[API_EXCEPTION] statusMessage=${exception.statusMessage}")
        Log.e(TAG, "[API_EXCEPTION] message=${exception.message}")
        Log.e(TAG, "[API_EXCEPTION] localizedMessage=${exception.localizedMessage}")
        Log.e(TAG, "[API_EXCEPTION] cause=${describeThrowable(exception.cause)}")
        Log.e(TAG, "[API_EXCEPTION] stackTrace=\n${exception.stackTraceToString()}")
    }

    // TEMP DIAGNOSTIC
    fun logThrowable(source: String, throwable: Throwable) {
        Log.e(TAG, "[THROWABLE] source=$source")
        Log.e(TAG, "[THROWABLE] class=${throwable.javaClass.name}")
        Log.e(TAG, "[THROWABLE] message=${throwable.message}")
        Log.e(TAG, "[THROWABLE] localizedMessage=${throwable.localizedMessage}")
        Log.e(TAG, "[THROWABLE] cause=${describeThrowable(throwable.cause)}")
        Log.e(TAG, "[THROWABLE] stackTrace=\n${throwable.stackTraceToString()}")
    }

    private fun logInstallerInfo(context: Context) {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val installerPackageName = packageManager.getInstallerPackageName(packageName)
        Log.d(TAG, "[INSTALLER] installerPackageName=$installerPackageName")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val installSourceInfo = packageManager.getInstallSourceInfo(packageName)
                Log.d(TAG, "[INSTALLER] installingPackageName=${installSourceInfo.installingPackageName}")
                Log.d(TAG, "[INSTALLER] initiatingPackageName=${installSourceInfo.initiatingPackageName}")
                Log.d(TAG, "[INSTALLER] originatingPackageName=${installSourceInfo.originatingPackageName}")
            } catch (e: Exception) {
                Log.e(TAG, "[INSTALLER] Failed to read InstallSourceInfo", e)
            }
        } else {
            Log.d(TAG, "[INSTALLER] installingPackageName=unavailable_on_api_${Build.VERSION.SDK_INT}")
            Log.d(TAG, "[INSTALLER] initiatingPackageName=unavailable_on_api_${Build.VERSION.SDK_INT}")
        }
    }

    private fun logPackageInfo(context: Context) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            }

            val longVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            Log.d(TAG, "[PACKAGE_INFO] versionCode=${getVersionCodeCompat(packageInfo)}")
            Log.d(TAG, "[PACKAGE_INFO] longVersionCode=$longVersionCode")
            Log.d(TAG, "[PACKAGE_INFO] firstInstallTime=${packageInfo.firstInstallTime}")
            Log.d(TAG, "[PACKAGE_INFO] lastUpdateTime=${packageInfo.lastUpdateTime}")
        } catch (e: Exception) {
            Log.e(TAG, "[PACKAGE_INFO] Failed to read package info", e)
        }
    }

    private fun logSigningCertificates(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d(TAG, "[SIGNING] signing certificates unavailable below API 28")
            return
        }

        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = packageInfo.signingInfo
            if (signingInfo == null) {
                Log.d(TAG, "[SIGNING] signingInfo=NULL")
                return
            }

            val signers = when {
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                signingInfo.signingCertificateHistory != null && signingInfo.signingCertificateHistory.isNotEmpty() -> signingInfo.signingCertificateHistory
                else -> signingInfo.apkContentsSigners
            }

            Log.d(TAG, "[SIGNING] signerCount=${signers.size}")
            signers.forEachIndexed { index, signer ->
                logSigner(index, signer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[SIGNING] Failed to read signing certificates", e)
        }
    }

    private fun logSigner(index: Int, signer: Signature) {
        val sha1 = digestSignature(signer, "SHA-1")
        val sha256 = digestSignature(signer, "SHA-256")
        Log.d(TAG, "[SIGNING] signer[$index].SHA-1=$sha1")
        Log.d(TAG, "[SIGNING] signer[$index].SHA-256=$sha256")
    }

    private fun digestSignature(signature: Signature, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val bytes = digest.digest(signature.toByteArray())
            bytes.joinToString(":") { byte -> "%02X".format(byte) }
        } catch (e: Exception) {
            "unavailable(${e.javaClass.simpleName})"
        }
    }

    private fun getVersionCodeCompat(packageInfo: android.content.pm.PackageInfo): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun describeThrowable(throwable: Throwable?): String {
        return if (throwable == null) {
            "null"
        } else {
            "${throwable.javaClass.name}: ${throwable.message}"
        }
    }

    private fun readBuildConfigField(fieldName: String): String {
        return try {
            val buildConfigClass = Class.forName("com.jcadenas.xpendz.BuildConfig")
            val field = buildConfigClass.getDeclaredField(fieldName)
            val value = field.get(null)
            value?.toString() ?: "null"
        } catch (e: Exception) {
            "unavailable(${e.javaClass.simpleName})"
        }
    }
}
