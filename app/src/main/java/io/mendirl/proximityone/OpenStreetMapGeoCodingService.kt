package io.mendirl.proximityone

class OpenStreetMapGeoCodingService : GeoCodingService {
    private val client = OpenStreetMapClient()


    override suspend fun info(address: String): List<GeoPosition> {
        return client.search(address)
    }


}