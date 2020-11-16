package io.mendirl.proximityone

import io.ktor.client.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.serialization.json.Json

class OpenStreetMapClient {
    private val httpClient = HttpClient {
        install(JsonFeature) {
            val json = Json { ignoreUnknownKeys = true }
            serializer = KotlinxSerializer(json)
        }
    }

    suspend fun search(address: String): List<GeoPosition> {
        return httpClient.get("$OPENSTREETMAP_NOMINATIM_ENDPOINT$address?format=json")
    }

    companion object {
        private const val OPENSTREETMAP_NOMINATIM_ENDPOINT =
            "https://nominatim.openstreetmap.org/search/"
    }
}

