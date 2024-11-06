package com.valyalkin.piggy.data

import com.valyalkin.piggy.configuration.SystemException
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

@Service
class MarketDataService(
    @Value("\${data.marketstack.url}") private val marketStackUrl: String,
    @Value("\${data.marketstack.apikey}") private val marketStackApiKey: String,
) {
    private val client =
        RestClient
            .builder()
            .build()

    fun processEndOfDayData() {
        val url =
            UriComponentsBuilder
                .fromUriString("$marketStackUrl/v1/eod")
                .queryParam("access_key", marketStackApiKey)
                .queryParam("symbols", "AAPL,NET")
                .queryParam("date_from", "2019-01-01")
                .queryParam("limit", 100)
                .queryParam("offset", 0)
                .toUriString()

        val result =
            client
                .get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    throw SystemException(response.statusText)
                }.body(String::class.java)

        logger.info(result)
    }

    @PostConstruct
    fun test() {
        processEndOfDayData()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }
}
