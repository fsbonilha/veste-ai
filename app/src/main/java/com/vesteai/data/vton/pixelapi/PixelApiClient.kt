package com.vesteai.data.vton.pixelapi

import com.vesteai.data.vton.VtonCredentials
import com.vesteai.data.vton.pixelapi.dto.JobStatusDto
import com.vesteai.data.vton.pixelapi.dto.SubmitResponseDto
import com.vesteai.data.vton.pixelapi.dto.pixelApiJson
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Speaks PixelAPI's HTTP surface. Throws [VtonException] for mapped HTTP errors and lets
 * [IOException] through for the engine to map as a transport failure.
 */
internal class PixelApiClient(private val httpClient: OkHttpClient) {

    suspend fun submit(
        baseUrl: HttpUrl,
        credentials: VtonCredentials,
        submission: PixelApiSubmission,
    ): String {
        val (path, json) = submission.endpoint()
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(path).build())
            .authenticated(credentials)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request).use { response ->
            decodeSuccess<SubmitResponseDto>(response).jobId
        }
    }

    suspend fun fetchJob(baseUrl: HttpUrl, credentials: VtonCredentials, jobId: String): JobStatusDto {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(JOBS_PATH).addPathSegment(jobId).build())
            .authenticated(credentials)
            .get()
            .build()
        return execute(request).use { response -> decodeSuccess<JobStatusDto>(response) }
    }

    private fun Request.Builder.authenticated(credentials: VtonCredentials): Request.Builder =
        header("Authorization", "Bearer ${credentials.apiKey}")
            .header("User-Agent", PixelApiConfig.USER_AGENT)

    private fun PixelApiSubmission.endpoint(): Pair<String, String> = when (this) {
        is PixelApiSubmission.Single -> SINGLE_PATH to pixelApiJson.encodeToString(body)
        is PixelApiSubmission.Outfit -> OUTFIT_PATH to pixelApiJson.encodeToString(body)
    }

    private inline fun <reified T> decodeSuccess(response: Response): T {
        val body = response.body.string()
        if (!response.isSuccessful) {
            throw VtonException(PixelApiErrorMapper.fromResponse(response.code, body))
        }
        return try {
            pixelApiJson.decodeFromString<T>(body)
        } catch (e: SerializationException) {
            throw VtonException(VtonError.ServerError(response.code))
        }
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call: Call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private companion object {
        const val SINGLE_PATH = "v1/virtual-tryon"
        const val OUTFIT_PATH = "v1/virtual-tryon/outfit"
        const val JOBS_PATH = "v1/virtual-tryon/jobs"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
