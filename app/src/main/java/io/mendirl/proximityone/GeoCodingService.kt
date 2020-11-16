package io.mendirl.proximityone

interface GeoCodingService {

    suspend fun info(address: String): List<GeoPosition>
}
