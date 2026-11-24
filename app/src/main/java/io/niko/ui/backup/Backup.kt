package io.niko.ui.backup

import android.os.Parcel
import android.os.Parcelable
import io.niko.database.DataStore
import io.niko.database.ParcelizeBridge
import io.niko.database.ProxyEntity
import io.niko.database.ProxyGroup
import io.niko.database.RuleEntity
import io.niko.database.SagerDatabase
import io.niko.database.preference.KeyValuePair
import io.niko.database.preference.PublicDatabase
import io.niko.ktx.toStringPretty
import io.niko.utils.Util
import org.json.JSONArray
import org.json.JSONObject

/** What a backup holds: the groups with their profiles, the routing rules, the settings. */
enum class BackupPart { PROFILES, RULES, SETTINGS }

/**
 * The backup file as the old screen wrote and read it (version 1): each part a JSON array of parcels in URL-safe
 * base64; the settings without this installation's secrets (DataStore.installSecretKeys), which a restore keeps.
 */
object Backup {

    const val VERSION = 1

    /** The JSON key that says a file holds [part]. */
    fun key(part: BackupPart) = when (part) {
        BackupPart.PROFILES -> "profiles"
        BackupPart.RULES -> "rules"
        BackupPart.SETTINGS -> "settings"
    }

    /** The parts [file] holds, or null for a file this version does not read (no JSON, another version). */
    fun parts(file: String): Pair<JSONObject, Set<BackupPart>>? {
        val json = runCatching { JSONObject(file) }.getOrNull() ?: return null
        if (json.optInt("version", 0) != VERSION) return null
        return json to BackupPart.entries.filterTo(LinkedHashSet()) { json.has(key(it)) }
    }

    private fun Parcelable.toBase64Str(): String {
        val parcel = Parcel.obtain()
        writeToParcel(parcel, 0)
        try {
            return Util.b64EncodeUrlSafe(parcel.marshall())
        } finally {
            parcel.recycle()
        }
    }

    private fun <T> JSONArray.parcels(create: (Parcel) -> T): List<T> = (0 until length()).map { i ->
        val data = Util.b64Decode(getString(i))
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(data, 0, data.size)
            parcel.setDataPosition(0)
            create(parcel)
        } finally {
            parcel.recycle()
        }
    }

    /** The backup of [parts], as the file holds it. Reads the database: not on the main thread. */
    fun write(parts: Set<BackupPart>): String = JSONObject().apply {
        put("version", VERSION)
        if (BackupPart.PROFILES in parts) {
            put("profiles", JSONArray().apply { SagerDatabase.proxyDao.getAll().forEach { put(it.toBase64Str()) } })
            put("groups", JSONArray().apply { SagerDatabase.groupDao.allGroups().forEach { put(it.toBase64Str()) } })
        }
        if (BackupPart.RULES in parts) {
            put("rules", JSONArray().apply { SagerDatabase.rulesDao.allRules().forEach { put(it.toBase64Str()) } })
        }
        if (BackupPart.SETTINGS in parts) {
            put("settings", JSONArray().apply {
                PublicDatabase.kvPairDao.all().forEach { if (it.key !in DataStore.installSecretKeys) put(it.toBase64Str()) }
            })
        }
    }.toStringPretty()

    /**
     * [parts] of [json] in place of what the app has: each part replaced whole. Writes the database: not on the main
     * thread, and the app restarts after it.
     */
    fun restore(json: JSONObject, parts: Set<BackupPart>) {
        if (BackupPart.PROFILES in parts && json.has("profiles")) {
            val profiles = json.getJSONArray("profiles").parcels { ProxyEntity.CREATOR.createFromParcel(it) }
            SagerDatabase.proxyDao.reset()
            SagerDatabase.proxyDao.insert(profiles)
            val groups = json.getJSONArray("groups").parcels { ProxyGroup.CREATOR.createFromParcel(it) }
            SagerDatabase.groupDao.reset()
            SagerDatabase.groupDao.insert(groups)
        }
        if (BackupPart.RULES in parts && json.has("rules")) {
            val rules: List<RuleEntity> = json.getJSONArray("rules").parcels { ParcelizeBridge.createRule(it) }
            SagerDatabase.rulesDao.reset()
            SagerDatabase.rulesDao.insert(rules)
        }
        if (BackupPart.SETTINGS in parts && json.has("settings")) {
            val settings = json.getJSONArray("settings").parcels { KeyValuePair.CREATOR.createFromParcel(it) }
            // keep this installation's own secrets; backups from 1.5.1 still carry the old device's
            DataStore.replaceSettings(settings)
        }
    }
}
