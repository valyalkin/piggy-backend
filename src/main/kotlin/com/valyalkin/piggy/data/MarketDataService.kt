package com.valyalkin.piggy.data

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.valyalkin.piggy.configuration.SystemException
import com.valyalkin.piggy.data.eod.EndOfDayPriceDataEntity
import com.valyalkin.piggy.data.eod.EndOfDayPriceDataRepository
import com.valyalkin.piggy.stocks.transactions.Currency
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.io.IOException
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Service
class MarketDataService(
    @Value("\${data.marketstack.url}") private val marketStackUrl: String,
    @Value("\${data.marketstack.apikey}") private val marketStackApiKey: String,
    private val endOfDayPriceDataRepository: EndOfDayPriceDataRepository,
) {
    private val client =
        RestClient
            .builder()
            .build()

    private fun fetchEndOfDayData(
        dateFrom: LocalDate,
        offset: Int,
        symbols: String,
    ): MarketStackEndOfDayPrices {
        val url =
            UriComponentsBuilder
                .fromUriString("$marketStackUrl/v1/eod")
                .queryParam("access_key", marketStackApiKey)
                .queryParam("symbols", symbols)
                .queryParam("date_from", dateFrom.toString())
                .queryParam("limit", 100)
                .queryParam("offset", offset)
                .toUriString()

        return client
            .get()
            .uri(url)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { _, response ->
                throw SystemException(
                    "Marketstack api call failed with error ${response.statusCode.value()}," +
                        " Details: ${response.body.readAllBytes().toString(Charset.defaultCharset())}",
                )
            }.body(MarketStackEndOfDayPrices::class.java)
            ?: throw SystemException("Not able to fetch the data from marketstack")
    }

    fun processEndOfDayData() {
        val list = mutableListOf<MarketStackEndOfDayData>()

        val latestDate = endOfDayPriceDataRepository.findLatestPriceDateForTicker("AAPL")
        val symbols = "AAPL,NET"

        val dateFrom =
            if (latestDate == null) {
                LocalDate.of(2019, 1, 1)
            } else {
                latestDate.plusDays(1)
            }

        val result =
            fetchEndOfDayData(
                dateFrom = dateFrom,
                offset = 0,
                symbols = symbols,
            )

        list.addAll(result.data)
        val pagination = result.pagination

        val numCalls = ceil(pagination.total.toDouble() / pagination.limit).toInt()

        if (numCalls != 0) {
            for (i in 1..numCalls) {
                val offset = i * pagination.limit
                val eod = fetchEndOfDayData(dateFrom = dateFrom, offset = offset, symbols = symbols)
                list.addAll(
                    eod.data,
                )
            }
        }

        logger.info(list.toString())
        logger.info(list.size.toString())

        list.forEach { eod ->
            logger.info("saving $eod")
            endOfDayPriceDataRepository.save(
                EndOfDayPriceDataEntity(
                    ticker = eod.symbol,
                    date = eod.date,
                    price = eod.close,
                    currency = Currency.USD,
                ),
            )
        }
    }

//    @PostConstruct
//    fun test() {
//        processEndOfDayData()
//    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }
}

data class MarketStackEndOfDayPrices(
    val data: List<MarketStackEndOfDayData>,
    val pagination: MarketStackPagination,
)

data class MarketStackEndOfDayData(
    val close: BigDecimal,
    val symbol: String,
    val exchange: String,
    @JsonDeserialize(using = CustomLocalDateDeserializer::class) val date: LocalDate,
)

data class MarketStackPagination(
    val limit: Int,
    val offset: Int,
    val count: Int,
    val total: Int,
)

class CustomLocalDateDeserializer : JsonDeserializer<LocalDate>() {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(
        jsonParser: JsonParser,
        context: DeserializationContext?,
    ): LocalDate {
        val date: String = jsonParser.text
        return LocalDate.parse(date, formatter) // Only date part will be extracted
    }

    companion object {
        private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
    }
}
