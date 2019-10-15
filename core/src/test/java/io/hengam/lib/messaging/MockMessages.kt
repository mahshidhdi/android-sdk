package io.hengam.lib.messaging

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.reactivex.Single

@JsonClass(generateAdapter = true)
class MockPerson(
        @Json(name="first_name") val firstName: String,
        @Json(name="last_name") val lastName: String,
        val age: Int
)

@JsonClass(generateAdapter = true)
class UpstreamMockMessageBook(
        val title: String,
        val genre: Genre,
        val author: MockPerson
) : TypedUpstreamMessage<UpstreamMockMessageBook> (
        50,
        { UpstreamMockMessageBookJsonAdapter(it) }
)

@JsonClass(generateAdapter = true)
class UpstreamMockMessageMovie(
        @Json(name="title") val movieTitle: String,
        @Json(name="genre") val movieGenre: Genre,
        @Json(name="year") val movieYear: Int
) : TypedUpstreamMessage<UpstreamMockMessageMovie> (
        60,
        { UpstreamMockMessageMovieJsonAdapter(it) }
)

enum class Genre {
    @Json(name="sci-fi") SCIFI,
    @Json(name="mystery") MYSTERY,
    @Json(name="fantasy") FANTASY
}

object MockMessageMixin1 : MessageMixin() {
    override fun collectMixinData(): Single<Map<String, Any?>> = Single.just(mapOf("mixinKey1" to "mixinValue1"))
}

object MockMessageMixin2 : MessageMixin() {
    override fun collectMixinData(): Single<Map<String, Any?>> = Single.just(mapOf("mixinKey2" to "mixinValue2"))
}

@JsonClass(generateAdapter = true)
class UpstreamMockMessageMovieWithMixin(
        @Json(name="title") val movieTitle: String,
        @Json(name="genre") val movieGenre: Genre,
        @Json(name="year") val movieYear: Int
) : TypedUpstreamMessage<UpstreamMockMessageMovieWithMixin> (
        60,
        { UpstreamMockMessageMovieWithMixinJsonAdapter(it) },
        mixins = listOf(MockMessageMixin1, MockMessageMixin2)
)

