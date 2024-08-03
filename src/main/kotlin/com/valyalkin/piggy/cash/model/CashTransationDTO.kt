package com.valyalkin.piggy.cash.model

import com.valyalkin.piggy.stocks.transactions.Currency
import jakarta.validation.constraints.DecimalMin
import org.springframework.validation.annotation.Validated
import java.math.BigDecimal
import java.time.OffsetDateTime

@Validated
data class CashTransactionDTO(
    val userId: String,
    @field:DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    val amount: BigDecimal,
    val currency: Currency,
    val date: OffsetDateTime,
)
