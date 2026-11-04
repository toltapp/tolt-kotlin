package com.tolt

public sealed class ToltException(override val message: String) : Exception(message) {
    public class NotConfigured : ToltException("Call Tolt.configure before claiming")

    public class InvalidInput(public val field: String) : ToltException("$field must not be blank")

    public class CodeNotFound : ToltException("Code not found")

    public class CodeConflict(message: String) : ToltException(message)

    public class RateLimited : ToltException("Too many claims, retry later")

    public class Server(public val status: Int, message: String) : ToltException(message)
}
