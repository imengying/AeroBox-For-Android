package com.aerobox.core.subscription

import org.json.JSONArray

/**
 * Shared extension functions used by both [UriNodeParser] and [JsonNodeParser].
 */
internal fun JSONArray.toCommaSeparatedString(): String? {
    val values = buildList {
        for (i in 0 until length()) {
            optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }
    return values.takeIf { it.isNotEmpty() }?.joinToString(",")
}
