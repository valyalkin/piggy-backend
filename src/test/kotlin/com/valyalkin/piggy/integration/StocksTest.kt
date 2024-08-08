package com.valyalkin.piggy.integration

import com.valyalkin.piggy.cash.holdings.CashHolding
import com.valyalkin.piggy.cash.holdings.CashHoldingsRepository
import com.valyalkin.piggy.configuration.Mapper
import com.valyalkin.piggy.stocks.holdings.StockHoldingsRepository
import com.valyalkin.piggy.stocks.transactions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StocksTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var cashHoldingsRepository: CashHoldingsRepository

    @Autowired
    private lateinit var stockTransactionRepository: StockTransactionRepository

    @Autowired
    private lateinit var stockHoldingsRepository: StockHoldingsRepository

    @BeforeEach
    fun cleanUp() {
        cashHoldingsRepository.deleteAll()
        stockTransactionRepository.deleteAll()
        stockHoldingsRepository.deleteAll()
    }

    private val testUserId = "test"
    private val testDate =
        OffsetDateTime
            .of(
                2023,
                10,
                10,
                10,
                10,
                10,
                0,
                ZoneOffset.UTC,
            )

    private val testCurrency = Currency.USD
    private val testTicker = "APPL"

    private val testQuantity = 10L
    private val testPrice = BigDecimal.valueOf(100.5)

    @Test
    fun `Buy - Should fail if there is no cash balance available in the required currency`() {
        val stockTransactionDTO =
            StockTransactionDTO(
                userId = testUserId,
                ticker = testTicker,
                date = testDate,
                quantity = testQuantity,
                price = testPrice,
                currency = testCurrency,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/buy")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(stockTransactionDTO),
                        ),
                ).andExpect(
                    status().is4xxClientError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("No cash balance was found for the given currency USD")
    }

    @Test
    fun `Buy - Should fail if there is not enough cash available`() {
        val cashAmountAvailable = BigDecimal.valueOf(1L)
        // Add previous holding
        cashHoldingsRepository.save(
            CashHolding(
                userId = testUserId,
                totalAmount = cashAmountAvailable,
                currency = testCurrency,
            ),
        )

        val stockTransactionDTO =
            StockTransactionDTO(
                userId = testUserId,
                ticker = testTicker,
                date = testDate,
                quantity = testQuantity,
                price = testPrice,
                currency = testCurrency,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/buy")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(stockTransactionDTO),
                        ),
                ).andExpect(
                    status().is4xxClientError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("Not enough cash to buy this stock")
    }

    @Test
    fun `Buy - Should create a new stock holding if the stock previously wasn't owned by this user`() {
        val cashAmountAvailable = BigDecimal.valueOf(5000L)
        // Add previous holding
        cashHoldingsRepository.save(
            CashHolding(
                userId = testUserId,
                totalAmount = cashAmountAvailable,
                currency = testCurrency,
            ),
        )

        val stockTransactionDTO =
            StockTransactionDTO(
                userId = testUserId,
                ticker = testTicker,
                date = testDate,
                quantity = testQuantity,
                price = testPrice,
                currency = testCurrency,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/buy")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(stockTransactionDTO),
                        ),
                ).andExpect(
                    status().isCreated,
                ).andReturn()
                .response.contentAsString

        val transaction = Mapper.objectMapper.readValue(transactionResponse, StockTransactionEntity::class.java)
        val id = transaction.id

        // Check transaction
        val savedTransaction = stockTransactionRepository.findById(id)
        assertThat(savedTransaction.isPresent).isTrue()
        savedTransaction.get().let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.ticker).isEqualTo(testTicker)
            assertThat(it.date).isEqualTo(testDate)
            assertThat(it.quantity).isEqualTo(testQuantity)
            assertThat(it.price).isEqualByComparingTo(testPrice)
            assertThat(it.transactionType).isEqualTo(TransactionType.BUY)
            assertThat(it.currency).isEqualTo(testCurrency)
        }

        // Check stock holding
        val stockHoldings = stockHoldingsRepository.getByUserIdAndTicker(testUserId, testTicker)
        assertThat(stockHoldings.size).isEqualTo(1)
        stockHoldings[0].let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.ticker).isEqualTo(testTicker)
            assertThat(it.quantity).isEqualTo(testQuantity)
            assertThat(it.averagePrice).isEqualByComparingTo(testPrice)
        }
        // Check if cash amount has decreased by the right value
        val cashHoldings = cashHoldingsRepository.findByUserIdAndCurrency(testUserId, testCurrency)
        assertThat(cashHoldings.size).isEqualTo(1)
        cashHoldings[0].let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.currency).isEqualTo(testCurrency)
            assertThat(it.totalAmount).isEqualByComparingTo(
                cashAmountAvailable.minus(
                    testPrice.multiply(BigDecimal.valueOf(testQuantity)),
                ),
            )
        }
    }
}
