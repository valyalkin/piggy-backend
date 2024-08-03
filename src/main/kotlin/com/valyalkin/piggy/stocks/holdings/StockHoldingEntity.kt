package com.valyalkin.piggy.stocks.holdings

import jakarta.persistence.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

@Entity
@Table(name = "stock_holdings")
data class StockHoldingEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID? = null,
    @Column(name = "user_id") val userID: String,
    val ticker: String,
    val quantity: BigInteger,
    val averageBuyPrice: BigDecimal,
)
