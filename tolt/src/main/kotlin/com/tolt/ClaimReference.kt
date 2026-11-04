package com.tolt

import java.net.URLDecoder

internal sealed interface ClaimReference {
    data class Code(val code: String) : ClaimReference

    data class Click(val clickId: String) : ClaimReference

    companion object {
        const val CODE_PARAMETER = "tolt_code"
        const val CLICK_PARAMETER = "tolt_click"

        fun fromQuery(query: String?): ClaimReference? {
            if (query.isNullOrEmpty()) return null
            val parameters = query.split('&').map { it.substringBefore('=') to it.substringAfter('=', "") }
            fun value(name: String): String? =
                parameters.firstOrNull { decode(it.first) == name }?.second?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() }

            value(CLICK_PARAMETER)?.let { return Click(it) }
            value(CODE_PARAMETER)?.let { return Code(it) }
            return null
        }

        private fun decode(value: String): String? =
            try {
                URLDecoder.decode(value, "UTF-8")
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
