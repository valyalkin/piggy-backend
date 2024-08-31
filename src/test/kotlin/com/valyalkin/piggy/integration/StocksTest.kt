package com.valyalkin.piggy.integration

import com.valyalkin.piggy.cash.holdings.CashHoldingsRepository
import com.valyalkin.piggy.configuration.Mapper
import com.valyalkin.piggy.stocks.holdings.StockHoldingEntity
import com.valyalkin.piggy.stocks.holdings.StockHoldingsRepository
import com.valyalkin.piggy.stocks.pl.ReleasedProfitLossEntityRepository
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

    @Autowired
    private lateinit var releasedProfitLossEntityRepository: ReleasedProfitLossEntityRepository

    @BeforeEach
    fun cleanUp() {
        cashHoldingsRepository.deleteAll()
        stockTransactionRepository.deleteAll()
        stockHoldingsRepository.deleteAll()
        releasedProfitLossEntityRepository.deleteAll()
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
    fun `Buy - Should add new transaction and add new stock holding`() {
        val stockTransactionDTO =
            StockTransactionDTO(
                userId = testUserId,
                ticker = testTicker,
                date = testDate,
                quantity = testQuantity,
                price = testPrice,
                currency = testCurrency,
                transactionType = TransactionType.BUY,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/transaction")
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
    }

    @Test
    fun `Sell - Should fail if first transaction is SELL`() {
        val stockTransactionDTO =
            StockTransactionDTO(
                userId = testUserId,
                ticker = testTicker,
                date = testDate,
                quantity = testQuantity,
                price = testPrice,
                currency = testCurrency,
                transactionType = TransactionType.SELL,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/transaction")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(stockTransactionDTO),
                        ),
                ).andExpect(
                    status().is4xxClientError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("Transaction cannot be added, first transaction should be BUY")
    }

    @Test
    fun `Buy - Should add new transaction and update the existing stock holding with new average price and quantity`() {
        val previousQuantity = 5L
        val previousAveragePrice = BigDecimal.valueOf(100L)

        // Previous transaction and stock holding
        stockTransactionRepository.save(
            StockTransactionEntity(
                userId = testUserId,
                ticker = testTicker,
                date = testDate.minusDays(1), // one day before
                quantity = previousQuantity,
                price = previousAveragePrice,
                currency = testCurrency,
                transactionType = TransactionType.BUY,
            ),
        )

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
                transactionType = TransactionType.BUY,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/transaction")
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
            assertThat(it.quantity).isEqualTo(testQuantity.plus(previousQuantity))
            assertThat(it.averagePrice).isEqualByComparingTo(BigDecimal.valueOf(100.33))
        }
    }

    @Test
    fun `Sell - Should add new transaction and update the existing stock holding with new quantity, record released pl`() {
        val previousQuantity = testQuantity
        val previousAveragePrice = BigDecimal.valueOf(80L)

        // Previous transaction and stock holding
        stockTransactionRepository.save(
            StockTransactionEntity(
                userId = testUserId,
                ticker = testTicker,
                date = testDate.minusDays(1), // one day before
                quantity = previousQuantity,
                price = previousAveragePrice,
                currency = testCurrency,
                transactionType = TransactionType.BUY,
            ),
        )

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
                transactionType = TransactionType.SELL,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/transaction")
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
            assertThat(it.quantity).isEqualTo(0L)
            assertThat(it.averagePrice).isEqualByComparingTo(previousAveragePrice)
        }

        // Check released profit
        val releasedPL =
            releasedProfitLossEntityRepository.getByUserIdAndTickerAndCurrency(
                testUserId,
                testTicker,
                testCurrency,
            )
        assertThat(releasedPL.size).isEqualTo(1)
        releasedPL[0].let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.ticker).isEqualTo(testTicker)
            assertThat(it.date).isEqualTo(testDate)
            assertThat(it.amount).isEqualByComparingTo(BigDecimal.valueOf(205.0))
        }
    }

    @Test
    fun `Sell - Should fail if quantity to sell is bigger than holdings`() {
        val previousQuantity = testQuantity
        val previousAveragePrice = BigDecimal.valueOf(80L)

        // Previous transaction and stock holding
        stockTransactionRepository.save(
            StockTransactionEntity(
                userId = testUserId,
                ticker = testTicker,
                date = testDate.minusDays(1), // one day before
                quantity = previousQuantity,
                price = previousAveragePrice,
                currency = testCurrency,
                transactionType = TransactionType.BUY,
            ),
        )

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
                quantity = testQuantity.plus(20), // Quantity is bigger
                price = testPrice,
                currency = testCurrency,
                transactionType = TransactionType.SELL,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/stocks/transaction")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(stockTransactionDTO),
                        ),
                ).andExpect(
                    status().is4xxClientError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("Cannot add SELL transaction, cannot sell more than current holding at this time")
    }
}
