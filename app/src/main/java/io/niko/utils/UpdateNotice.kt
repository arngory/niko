package io.niko.utils

import org.json.JSONObject

/**
 * A release's UPDATE.json (raycontrol docs/spec/client-min-v1.md §6): whether the update is mandatory, the lowest
 * version the release still supports, and what to tell the user by language. It is listed in the release's signed
 * SHA256SUMS, so it is read only after that signature and the file's hash hold (Updates.notice). A release without
 * one is an ordinary update.
 */
data class UpdateNotice(val mandatory: Boolean, val minSupported: String?, val message: Map<String, String>) {

    /** Whether [version] (this app's) must update: no "later" then. */
    fun required(version: String) = mandatory || (minSupported != null && compareVersions(version, minSupported) < 0)

    /** The message in [language] (an ISO 639-1 code), English where there is none in it; null none. */
    fun message(language: String) = message[language] ?: message["en"]

    companion object {
        const val NAME = "UPDATE.json"

        // a few lines: what is larger is not one
        const val MAX_BYTES = 16 * 1024
        private const val MAX_MESSAGE = 1000
        private val SEMVER = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$""")
        private val LANGUAGE = Regex("^[a-z]{2}$")

        /** UPDATE.json of version 1, else null: another version, or what does not read as one. */
        fun parse(bytes: ByteArray): UpdateNotice? {
            if (bytes.size > MAX_BYTES) return null
            val o = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return null
            if (o.opt("v") != 1) return null
            val mandatory = o.opt("mandatory") as? Boolean ?: return null
            val minSupported = when (val v = o.opt("min_supported")) {
                null -> null
                is String -> v.takeIf { SEMVER.matches(it) } ?: return null
                else -> return null
            }
            val messages = o.optJSONObject("message")
            val message = messages?.keys()?.asSequence().orEmpty().mapNotNull { language ->
                val text = (messages?.opt(language) as? String)?.trim()
                if (LANGUAGE.matches(language) && !text.isNullOrEmpty()) language to text.take(MAX_MESSAGE) else null
            }.toMap()
            return UpdateNotice(mandatory, minSupported, message)
        }
    }
}
