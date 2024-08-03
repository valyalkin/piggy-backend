package com.valyalkin.piggy.stocks.transactions

import java.math.BigDecimal
import java.math.BigInteger
import java.time.OffsetDateTime

data class StockTransactionDTO(
    val userId: String,
    val ticker: String,
    val date: OffsetDateTime,
    val quantity: BigInteger,
    val price: BigDecimal,
    val transactionType: TransactionType,
    val currency: Currency,
)
