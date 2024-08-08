package com.valyalkin.piggy.integration

import com.fasterxml.jackson.module.kotlin.readValue
import com.valyalkin.piggy.cash.holdings.CashHolding
import com.valyalkin.piggy.cash.holdings.CashHoldingsRepository
import com.valyalkin.piggy.cash.model.CashTransactionDTO
import com.valyalkin.piggy.cash.transactions.CashTransactionEntity
import com.valyalkin.piggy.cash.transactions.CashTransactionRepository
import com.valyalkin.piggy.cash.transactions.CashTransactionType
import com.valyalkin.piggy.configuration.Mapper
import com.valyalkin.piggy.stocks.transactions.Currency
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
class CashTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var cashHoldingsRepository: CashHoldingsRepository

    @Autowired
    private lateinit var cashTransactionRepository: CashTransactionRepository

    @BeforeEach
    fun cleanUp() {
        cashHoldingsRepository.deleteAll()
        cashTransactionRepository.deleteAll()
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

    @Test
    fun `Deposit - Should add transaction and add the amount to cash holdings when no holding is present`() {
        val amount = BigDecimal.valueOf(10000.25)

        val cashTransactionDTO =
            CashTransactionDTO(
                userId = testUserId,
                amount = amount,
                currency = testCurrency,
                date = testDate,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/cash/deposit")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(cashTransactionDTO),
                        ),
                ).andExpect(
                    status().isCreated,
                ).andReturn()
                .response.contentAsString

        val transaction = Mapper.objectMapper.readValue(transactionResponse, CashTransactionEntity::class.java)
        val id = transaction.id

        val savedTransaction = cashTransactionRepository.findById(id)
        assertThat(savedTransaction.isPresent).isTrue()
        savedTransaction.get().let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.date).isEqualTo(testDate)
            assertThat(it.currency).isEqualTo(testCurrency)
            assertThat(it.amount).isEqualTo(amount)
            assertThat(it.cashTransactionType).isEqualTo(CashTransactionType.DEPOSIT)
        }

        val cashHoldings = cashHoldingsRepository.findByUserIdAndCurrency(testUserId, testCurrency)
        assertThat(cashHoldings.size).isEqualTo(1)
        cashHoldings[0].let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.currency).isEqualTo(testCurrency)
            assertThat(it.totalAmount).isEqualTo(amount)
        }
    }

    @Test
    fun `Deposit - Should add transaction and add the amount to the previously stored holding`() {
        val amount = BigDecimal.valueOf(10000.25)
        val previousAmount = BigDecimal.valueOf(50000)

        // Add previous holding
        cashHoldingsRepository.save(
            CashHolding(
                userId = testUserId,
                totalAmount = previousAmount,
                currency = testCurrency,
            ),
        )

        val cashTransactionDTO =
            CashTransactionDTO(
                userId = testUserId,
                amount = amount,
                currency = testCurrency,
                date = testDate,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/cash/deposit")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(cashTransactionDTO),
                        ),
                ).andExpect(
                    status().isCreated,
                ).andReturn()
                .response.contentAsString

        val transaction = Mapper.objectMapper.readValue(transactionResponse, CashTransactionEntity::class.java)
        val id = transaction.id

        val savedTransaction = cashTransactionRepository.findById(id)
        assertThat(savedTransaction.isPresent).isTrue()
        savedTransaction.get().let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.date).isEqualTo(testDate)
            assertThat(it.currency).isEqualTo(testCurrency)
            assertThat(it.amount).isEqualTo(amount)
            assertThat(it.cashTransactionType).isEqualTo(CashTransactionType.DEPOSIT)
        }

        val cashHoldings = cashHoldingsRepository.findByUserIdAndCurrency(testUserId, testCurrency)
        assertThat(cashHoldings.size).isEqualTo(1)
        cashHoldings[0].let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.currency).isEqualTo(testCurrency)
            assertThat(it.totalAmount).isEqualTo(amount.plus(previousAmount))
        }
    }

    @Test
    fun `Withdrawal - Should fail if there are no cash holdings present for this user and currency`() {
        val amountToWithdraw = BigDecimal.valueOf(1000)

        val cashTransactionDTO =
            CashTransactionDTO(
                userId = testUserId,
                amount = amountToWithdraw,
                currency = testCurrency,
                date = testDate,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/cash/withdraw")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(cashTransactionDTO),
                        ),
                ).andExpect(
                    status().is4xxClientError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("Did not find any cash holdings for user test and currency USD")
    }

    @Test
    fun `Withdrawal - Should fail if there is not enough cash to withdraw`() {
        val previousAmount = BigDecimal.valueOf(1000L)
        val amountToWithdraw = BigDecimal.valueOf(5000L)
        // Add previous holding
        cashHoldingsRepository.save(
            CashHolding(
                userId = testUserId,
                totalAmount = previousAmount,
                currency = testCurrency,
            ),
        )

        val cashTransactionDTO =
            CashTransactionDTO(
                userId = testUserId,
                amount = amountToWithdraw,
                currency = testCurrency,
                date = testDate,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/cash/withdraw")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(cashTransactionDTO),
                        ),
                ).andExpect(
                    status().is4xxClientError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("Amount to withdraw is bigger than available balance")
    }

    @Test
    fun `Withdrawal - Should fail if there is no holding for the currency`() {
        val previousAmount = BigDecimal.valueOf(1000L)
        val amountToWithdraw = BigDecimal.valueOf(5000L)
        // Add previous holding
        cashHoldingsRepository.save(
            CashHolding(
                userId = testUserId,
                totalAmount = previousAmount,
                currency = testCurrency,
            ),
        )

        val cashTransactionDTO =
            CashTransactionDTO(
                userId = testUserId,
                amount = amountToWithdraw,
                currency = Currency.SGD,
                date = testDate,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/cash/withdraw")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(cashTransactionDTO),
                        ),
                ).andExpect(
                    status().is4xxClientError,
                ).andReturn()
                .response.contentAsString

        assertThat(transactionResponse).contains("Did not find any cash holdings for user test and currency SGD")
    }

    @Test
    fun `Withdrawal - Should remove the required amount from cash holding and add transaction`() {
        val previousAmount = BigDecimal.valueOf(5000)
        val amountToWithdraw = BigDecimal.valueOf(1000)
        // Add previous holding
        cashHoldingsRepository.save(
            CashHolding(
                userId = testUserId,
                totalAmount = previousAmount,
                currency = testCurrency,
            ),
        )

        val cashTransactionDTO =
            CashTransactionDTO(
                userId = testUserId,
                amount = amountToWithdraw,
                currency = testCurrency,
                date = testDate,
            )

        val transactionResponse =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/v1/cash/withdraw")
                        .contentType("application/json")
                        .content(
                            Mapper.objectMapper.writeValueAsString(cashTransactionDTO),
                        ),
                ).andExpect(
                    status().isCreated,
                ).andReturn()
                .response.contentAsString

        val transaction = Mapper.objectMapper.readValue(transactionResponse, CashTransactionEntity::class.java)
        val id = transaction.id

        val savedTransaction = cashTransactionRepository.findById(id)
        assertThat(savedTransaction.isPresent).isTrue()
        savedTransaction.get().let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.date).isEqualTo(testDate)
            assertThat(it.currency).isEqualTo(testCurrency)
            assertThat(it.amount).isEqualByComparingTo(amountToWithdraw)
            assertThat(it.cashTransactionType).isEqualTo(CashTransactionType.WITHDRAW)
        }

        val cashHoldings = cashHoldingsRepository.findByUserIdAndCurrency(testUserId, testCurrency)
        assertThat(cashHoldings.size).isEqualTo(1)
        cashHoldings[0].let {
            assertThat(it.userId).isEqualTo(testUserId)
            assertThat(it.currency).isEqualTo(testCurrency)
            assertThat(it.totalAmount).isEqualByComparingTo(previousAmount.minus(amountToWithdraw))
        }
    }
}
