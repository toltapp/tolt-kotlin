package com.tolt

public data class ClaimResult(
    public val status: Status,
    public val appUserId: String,
) {
    public enum class Status(internal val value: String) {
        CLAIMED("claimed"),
        ALREADY_CLAIMED("already_claimed"),
    }
}
