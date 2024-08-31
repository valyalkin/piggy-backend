package com.valyalkin.piggy.stocks.holdings

import jakarta.persistence.*
import org.springframework.data.repository.CrudRepository
import java.math.BigDecimal
import java.util.*

interface StockHoldingsRepository : CrudRepository<StockHoldingEntity, UUID> {
    fun getByUserIdAndTicker(
        userId: String,
        ticker: String,
    ): List<StockHoldingEntity>
}

@Entity
@Table(name = "stock_holdings")
data class StockHoldingEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id") val userId: String,
    @Column(name = "ticker", unique = true) val ticker: String,
    val quantity: Long,
    val averagePrice: BigDecimal,
)
