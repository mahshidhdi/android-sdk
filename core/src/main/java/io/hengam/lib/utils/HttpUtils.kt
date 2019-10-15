package io.hengam.lib.utils

import io.hengam.lib.dagger.CoreScope
import io.reactivex.Single
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@CoreScope
class HttpUtils @Inject constructor() {

    /**
     * Make HTTP GET request to a url.
     *
     * The function does not operate on any specific scheduler. You will need to subscribe on
     * you're desired scheduler.
     *
     * @return A [Single] which will emit the result as a String. If the response code received from
     * the request is 4xx or 5xx, an [HttpError] will be thrown by the Single. Any other errors raised
     * during the HTTP request will also be thrown by the [Single]
     */
    fun request(url: String): Single<String> {
        return Single.create { emitter ->
            try {
                emitter.onSuccess(requestBlocking(url))
            } catch (ex: Exception) {
                emitter.tryOnError(ex)
            }
        }
    }

    /**
     * Make HTTP GET request to a url.
     *
     * The request will block the thread the function is called on.
     *
     * @return The response as a String. If the response code received from the request is 4xx or
     * 5xx, an [HttpError] will be thrown. Any other errors raised during the HTTP request will also
     * be thrown.
     */
    @Throws(IOException::class, HttpError::class)
    fun requestBlocking(url: String): String {
        val urlConnection: HttpURLConnection = URL(url).openConnection() as HttpURLConnection
        if (urlConnection.responseCode >= 400) {
            urlConnection.inputStream.close()
            urlConnection.disconnect()
            throw HttpError(urlConnection.responseCode, urlConnection.responseMessage)
        }
        val bufferedReader = BufferedReader(InputStreamReader(urlConnection.inputStream))
        val sw = StringWriter()
        val buffer = CharArray(1024 * 4)
        var n = bufferedReader.read(buffer)
        while (-1 != n) {
            sw.write(buffer, 0, n)
            n = bufferedReader.read(buffer)
        }
        urlConnection.inputStream.close()
        urlConnection.disconnect()
        return sw.toString()
    }

    /**
     * Make HTTP GET request to a url.
     *
     * The request will block the thread the function is called on.
     *
     * @return The response as an [InputStream]. If the response code received from the request is 4xx or
     * 5xx, an [HttpError] will be thrown. Any other errors raised during the HTTP request will also
     * be thrown.
     */
    @Throws(IOException::class, HttpError::class)
    fun requestBlockingStream(url: String): InputStream {
        val urlConnection: HttpURLConnection = URL(url).openConnection() as HttpURLConnection
        if (urlConnection.responseCode >= 400) {
            urlConnection.inputStream.close()
            urlConnection.disconnect()
            throw HttpError(urlConnection.responseCode, urlConnection.responseMessage)
        }
        return urlConnection.inputStream
    }

    class HttpError(val statusCode: Int, val reason: String):
            Exception("Http Error: $statusCode $reason")
}