package com.valyalkin.piggy.cash.holdings

import com.valyalkin.piggy.stocks.transactions.Currency
import jakarta.persistence.*
import org.springframework.data.repository.CrudRepository
import java.math.BigDecimal
import java.util.*

interface CashHoldingsRepository : CrudRepository<CashHolding, UUID> {
    fun findByUserId(userId: String): List<CashHolding>

    fun findByUserIdAndCurrency(
        userId: String,
        currency: Currency,
    ): List<CashHolding>
}

@Entity
@Table(name = "cash_holdings")
data class CashHolding(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id") val userId: String,
    @Column(name = "total_amount") val totalAmount: BigDecimal,
    @Enumerated(EnumType.STRING) val currency: Currency,
)
