// app/src/main/java/com/nativestream/android/data/remote/ApiError.kt
//
// NS-004: API Error types
// Mirrors APIError from APIClient.swift exactly.
// All Ktor exceptions are mapped to these at the call-site in ApiClient.kt.

package com.nativestream.android.data.remote

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Server did not respond or connection refused. */
    class ServerUnreachable(val url: String) :
        ApiError("Server unreachable at $url")

    /** Server responded with a non-2xx HTTP status. */
    class HttpError(val statusCode: Int, val body: String?) :
        ApiError("Server returned $statusCode${body?.let { ": $it" } ?: ""}")

    /** JSON decoding failed. */
    class DecodingFailed(cause: Throwable) :
        ApiError("Response decode failed: ${cause.localizedMessage}", cause)

    /** A string could not be resolved to a valid URL. */
    class InvalidUrl(val raw: String) :
        ApiError("Invalid URL: $raw")

    /** Channel has no active stream link on the server. */
    object NoActiveLink :
        ApiError("Channel has no active stream link")
}