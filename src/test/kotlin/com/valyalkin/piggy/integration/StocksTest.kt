package com.valyalkin.piggy.integration

import com.valyalkin.piggy.cash.holdings.CashHolding
import com.valyalkin.piggy.cash.holdings.CashHoldingsRepository
import com.valyalkin.piggy.configuration.Mapper
import com.valyalkin.piggy.stocks.holdings.StockHoldingEntity
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

    @Test
    fun `Buy - Should update previously held position and calculate new average price`() {
        val cashAmountAvailable = BigDecimal.valueOf(5000L)
        // Add previous cash holding
        cashHoldingsRepository.save(
            CashHolding(
                userId = testUserId,
                totalAmount = cashAmountAvailable,
                currency = testCurrency,
            ),
        )

        val previousQuantity = 5L
        val previousAveragePrice = BigDecimal.valueOf(100L)
        // Add previous stock holding
        stockHoldingsRepository.save(
            StockHoldingEntity(
                userId = testUserId,
                ticker = testTicker,
                quantity = previousQuantity,
                averagePrice = previousAveragePrice,
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
            assertThat(it.quantity).isEqualTo(15L) // Expected 10 + 5 = 15
            assertThat(it.averagePrice).isEqualByComparingTo(BigDecimal.valueOf(100.33))
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

    @Test
    fun `Sell - Should fail if there is nothing to sell`() {
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
                        .post("/v1/stocks/sell")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(stockTransactionDTO),
                        ),
                ).andExpect(
                    status().is4xxClientError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("There is no stock holding of $testTicker for this user")
    }

    @Test
    fun `Sell - Should fail if quantity to sell is bigger than that of the existing holding`() {
        val previousQuantity = 5L
        val previousAveragePrice = BigDecimal.valueOf(75L)
        // Add previous stock holding
        stockHoldingsRepository.save(
            StockHoldingEntity(
                userId = testUserId,
                ticker = testTicker,
                quantity = previousQuantity,
                averagePrice = previousAveragePrice,
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
                        .post("/v1/stocks/sell")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(stockTransactionDTO),
                        ),
                ).andExpect(
                    status().is4xxClientError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("Sell quantity is bigger than a holding")
    }

    @Test
    fun `Sell - Should fail if there was no cash holding in the even of selling`() {
        val previousQuantity = 25L
        val previousAveragePrice = BigDecimal.valueOf(75L)
        // Add previous stock holding
        stockHoldingsRepository.save(
            StockHoldingEntity(
                userId = testUserId,
                ticker = testTicker,
                quantity = previousQuantity,
                averagePrice = previousAveragePrice,
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
                        .post("/v1/stocks/sell")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(stockTransactionDTO),
                        ),
                ).andExpect(
                    status().is5xxServerError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("Cash holding of currency $testCurrency doesn't exist for this user")
    }

    @Test
    fun `Sell - Should decrease the existing holding quantity and increase cash holding by the release amount`() {
        val cashAmountAvailable = BigDecimal.valueOf(1000L)
        // Add previous cash holding
        cashHoldingsRepository.save(
            CashHolding(
                userId = testUserId,
                totalAmount = cashAmountAvailable,
                currency = testCurrency,
            ),
        )

        val previousQuantity = 25L
        val previousAveragePrice = BigDecimal.valueOf(75L)
        // Add previous stock holding
        stockHoldingsRepository.save(
            StockHoldingEntity(
                userId = testUserId,
                ticker = testTicker,
                quantity = previousQuantity,
                averagePrice = previousAveragePrice,
            ),
        )

        val stockTransactionDTO =
            StockTransactionDTO(
                userId = testUserId,
                ticker = testTicker,
                date = testDate,
                quantity = testQuantity, // 10 shares
                price = testPrice, // 100.5 USD per share
                currency = testCurrency,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/sell")
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
            assertThat(it.transactionType).isEqualTo(TransactionType.SELL)
            assertThat(it.currency).isEqualTo(testCurrency)
        }

        // Check stock holding
        val stockHoldings = stockHoldingsRepository.getByUserIdAndTicker(testUserId, testTicker)
        assertThat(stockHoldings.size).isEqualTo(1)
        stockHoldings[0].let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.ticker).isEqualTo(testTicker)
            assertThat(it.quantity).isEqualTo(15L) // Expected 25 - 10 = 15 shares
            assertThat(it.averagePrice).isEqualByComparingTo(previousAveragePrice) // average price doesn't change
        }
        // Check if cash amount has increased by the right value
        val cashHoldings = cashHoldingsRepository.findByUserIdAndCurrency(testUserId, testCurrency)
        assertThat(cashHoldings.size).isEqualTo(1)
        cashHoldings[0].let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.currency).isEqualTo(testCurrency)
            assertThat(it.totalAmount).isEqualByComparingTo(
                // was 1000, 10 shares * 100.5 USD price per share is sold
                // 1000 + 10 * 100.5 = 2005
                BigDecimal.valueOf(2005),
            )
        }
    }

    @Test
    fun `Sell - Should remove stock holding if it is fully sold and increase cash holding by the released amount`() {
        val cashAmountAvailable = BigDecimal.valueOf(1000L)
        // Add previous cash holding
        cashHoldingsRepository.save(
            CashHolding(
                userId = testUserId,
                totalAmount = cashAmountAvailable,
                currency = testCurrency,
            ),
        )

        val previousQuantity = testQuantity
        val previousAveragePrice = BigDecimal.valueOf(75L)
        // Add previous stock holding
        stockHoldingsRepository.save(
            StockHoldingEntity(
                userId = testUserId,
                ticker = testTicker,
                quantity = previousQuantity,
                averagePrice = previousAveragePrice,
            ),
        )

        val stockTransactionDTO =
            StockTransactionDTO(
                userId = testUserId,
                ticker = testTicker,
                date = testDate,
                quantity = testQuantity, // 10 shares
                price = testPrice, // 100.5 USD per share
                currency = testCurrency,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/sell")
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
            assertThat(it.transactionType).isEqualTo(TransactionType.SELL)
            assertThat(it.currency).isEqualTo(testCurrency)
        }

        // Check that stock holding is deleted
        val stockHoldings = stockHoldingsRepository.getByUserIdAndTicker(testUserId, testTicker)
        assertThat(stockHoldings.size).isEqualTo(0)

        // Check if cash amount has increased by the right value
        val cashHoldings = cashHoldingsRepository.findByUserIdAndCurrency(testUserId, testCurrency)
        assertThat(cashHoldings.size).isEqualTo(1)
        cashHoldings[0].let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.currency).isEqualTo(testCurrency)
            assertThat(it.totalAmount).isEqualByComparingTo(
                // was 1000, 10 shares * 100.5 USD price per share is sold
                // 1000 + 10 * 100.5 = 2005
                BigDecimal.valueOf(2005),
            )
        }
    }
}
