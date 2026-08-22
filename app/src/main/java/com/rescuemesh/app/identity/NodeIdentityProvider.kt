package com.rescuemesh.app.identity

import android.content.Context
import java.security.SecureRandom

class NodeIdentityProvider(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "rescuemesh_identity",
        Context.MODE_PRIVATE,
    )

    val nodeId: ByteArray by lazy {
        val existing = preferences.getString(KEY_NODE_ID, null)
        if (existing != null) {
            existing.hexToBytes()
        } else {
            ByteArray(NODE_ID_BYTES).also { bytes ->
                SecureRandom().nextBytes(bytes)
                preferences.edit().putString(KEY_NODE_ID, bytes.toHex()).apply()
            }
        }
    }

    var nodeName: String
        get() = preferences.getString(KEY_NODE_NAME, "") ?: ""
        set(value) = preferences.edit().putString(KEY_NODE_NAME, value).apply()

    val displayId: String
        get() = nodeName.ifBlank { nodeId.toDisplayNodeId() }

    private companion object {
        const val KEY_NODE_ID = "node_id"
        const val KEY_NODE_NAME = "node_name"
        const val NODE_ID_BYTES = 16
    }
}

fun ByteArray.toDisplayNodeId(): String {
    return "NODE-${take(4).joinToString(separator = "") { "%02X".format(it) }}"
}

fun ByteArray.toHex(): String {
    return joinToString(separator = "") { "%02x".format(it) }
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Invalid hex length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
