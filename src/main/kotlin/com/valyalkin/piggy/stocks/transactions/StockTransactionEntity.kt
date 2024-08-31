@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.valyalkin.piggy.stocks.transactions

import jakarta.persistence.*
import org.springframework.data.repository.CrudRepository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

interface StockTransactionRepository : CrudRepository<StockTransactionEntity, UUID> {
    fun findByUserIdAndTickerOrderByDateAsc(
        userId: String,
        ticker: String,
    ): MutableList<StockTransactionEntity>

    fun findByUserIdAndTickerAndCurrencyOrderByDateAsc(
        userId: String,
        ticker: String,
        currency: Currency,
    ): MutableList<StockTransactionEntity>
}

@Entity
@Table(name = "stock_transactions")
data class StockTransactionEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id") val userId: String,
    val ticker: String,
    @Temporal(TemporalType.TIMESTAMP) val date: OffsetDateTime,
    val quantity: Long,
    val price: BigDecimal,
    @Enumerated(EnumType.STRING) @Column(name = "transaction_type") val transactionType: TransactionType,
    @Enumerated(EnumType.STRING) val currency: Currency,
)

enum class TransactionType {
    BUY,
    SELL,
}

enum class Currency {
    USD,
    SGD,
}
