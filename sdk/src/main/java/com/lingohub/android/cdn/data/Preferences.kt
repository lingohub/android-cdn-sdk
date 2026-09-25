package com.lingohub.android.cdn.data

import android.content.Context
import android.content.SharedPreferences
import com.lingohub.android.cdn.data.model.BundleMetadata
import androidx.core.content.edit

internal interface IPreferences {
    fun getBundleMetadata(): BundleMetadata?
    fun saveBundleMetadata(metadata: BundleMetadata)
    fun clearBundleMetadata()
    fun getClientId(): String?
    fun saveClientId(clientId: String)

    /** The schedule stored for [scope], or a fresh one when none is stored for it. */
    fun getUpdateSchedule(scope: UpdateSchedule.Scope): UpdateSchedule
    fun saveUpdateSchedule(schedule: UpdateSchedule)
}

internal class Preferences(context: Context) : IPreferences {
    companion object {
        const val BUNDLE_ID = "bundle_identifier"
        const val APP_VERSION = "app_version"
        const val CLIENT_ID = "client_id"
        const val SCHEDULE_APP_VERSION = "update_schedule_app_version"
        const val SCHEDULE_ENVIRONMENT = "update_schedule_environment"
        const val SCHEDULE_API_KEY_DIGEST = "update_schedule_api_key_digest"
        const val SCHEDULE_LAST_SUCCESS = "update_schedule_last_success"
        const val SCHEDULE_SERVER_ERRORS = "update_schedule_server_errors"
        const val SCHEDULE_COOLDOWN_UNTIL = "update_schedule_cooldown_until"
        const val SCHEDULE_COOLDOWN_STATUS = "update_schedule_cooldown_status"
        const val SCHEDULE_COOLDOWN_CODES = "update_schedule_cooldown_codes"
    }

    // Storage identifier, not brand surface: this name must stay "Lingohub" so apps
    // upgrading from older SDK versions keep their bundle metadata. Renaming it would
    // orphan the metadata while the downloaded bundle in files/lingohub survives,
    // which lets checkIfUpdated() skip the app-version-change cleanup and serve
    // stale translations until the next successful update.
    private val prefs: SharedPreferences = context.getSharedPreferences("Lingohub", Context.MODE_PRIVATE)

    override fun getBundleMetadata(): BundleMetadata? {
        val bundleId = prefs.getString(BUNDLE_ID, null) ?: return null
        val appVersion = prefs.getString(APP_VERSION, null) ?: return null
        return BundleMetadata(bundleId, appVersion)
    }

    override fun saveBundleMetadata(metadata: BundleMetadata) = prefs.edit() {
        putString(APP_VERSION, metadata.appVersion)
            .putString(BUNDLE_ID, metadata.bundleIdentifier)
    }

    // Remove only the bundle keys: clear() would also wipe the per-install
    // client id, which must survive an app-version purge.
    override fun clearBundleMetadata() = prefs.edit() {
        remove(APP_VERSION)
            .remove(BUNDLE_ID)
    }

    override fun getClientId(): String? = prefs.getString(CLIENT_ID, null)

    override fun saveClientId(clientId: String) = prefs.edit() {
        putString(CLIENT_ID, clientId)
    }

    override fun getUpdateSchedule(scope: UpdateSchedule.Scope): UpdateSchedule {
        val storedScope = UpdateSchedule.Scope(
            appVersion = prefs.getString(SCHEDULE_APP_VERSION, null) ?: return UpdateSchedule(scope),
            environment = prefs.getString(SCHEDULE_ENVIRONMENT, null) ?: return UpdateSchedule(scope),
            apiKeyDigest = prefs.getString(SCHEDULE_API_KEY_DIGEST, null) ?: return UpdateSchedule(scope),
        )
        if (storedScope != scope) return UpdateSchedule(scope)
        val cooldown = if (prefs.contains(SCHEDULE_COOLDOWN_UNTIL)) {
            UpdateSchedule.Cooldown(
                untilMs = prefs.getLong(SCHEDULE_COOLDOWN_UNTIL, 0),
                statusCode = prefs.getInt(SCHEDULE_COOLDOWN_STATUS, 0),
                errorCodes = prefs.getString(SCHEDULE_COOLDOWN_CODES, null)?.split(',')?.filter { it.isNotEmpty() }.orEmpty(),
            )
        } else {
            null
        }
        return UpdateSchedule(
            scope = scope,
            lastSuccessfulUpdateMs = if (prefs.contains(SCHEDULE_LAST_SUCCESS)) prefs.getLong(SCHEDULE_LAST_SUCCESS, 0) else null,
            consecutiveServerErrors = prefs.getInt(SCHEDULE_SERVER_ERRORS, 0),
            cooldown = cooldown,
        )
    }

    // One edit, so the schedule is always written as a whole.
    override fun saveUpdateSchedule(schedule: UpdateSchedule) = prefs.edit() {
        putString(SCHEDULE_APP_VERSION, schedule.scope.appVersion)
        putString(SCHEDULE_ENVIRONMENT, schedule.scope.environment)
        putString(SCHEDULE_API_KEY_DIGEST, schedule.scope.apiKeyDigest)
        schedule.lastSuccessfulUpdateMs?.let { putLong(SCHEDULE_LAST_SUCCESS, it) } ?: remove(SCHEDULE_LAST_SUCCESS)
        putInt(SCHEDULE_SERVER_ERRORS, schedule.consecutiveServerErrors)
        val cooldown = schedule.cooldown
        if (cooldown != null) {
            putLong(SCHEDULE_COOLDOWN_UNTIL, cooldown.untilMs)
            putInt(SCHEDULE_COOLDOWN_STATUS, cooldown.statusCode)
            putString(SCHEDULE_COOLDOWN_CODES, cooldown.errorCodes.joinToString(","))
        } else {
            remove(SCHEDULE_COOLDOWN_UNTIL)
            remove(SCHEDULE_COOLDOWN_STATUS)
            remove(SCHEDULE_COOLDOWN_CODES)
        }
    }
}