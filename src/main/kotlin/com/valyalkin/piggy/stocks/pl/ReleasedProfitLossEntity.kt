package com.valyalkin.piggy.stocks.pl

import com.valyalkin.piggy.stocks.transactions.Currency
import jakarta.persistence.*
import org.springframework.data.repository.CrudRepository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

interface ReleasedProfitLossEntityRepository : CrudRepository<ReleasedProfitLossEntity, UUID> {
    fun deleteByUserIdAndTickerAndCurrency(
        userId: String,
        ticker: String,
        currency: Currency,
    ): Long

    fun getByUserIdAndTickerAndCurrency(
        userId: String,
        ticker: String,
        currency: Currency,
    ): List<ReleasedProfitLossEntity>
}

@Entity
@Table(name = "released_profit_loss")
data class ReleasedProfitLossEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id") val userId: String,
    val ticker: String,
    @Temporal(TemporalType.TIMESTAMP) val date: OffsetDateTime,
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING) val currency: Currency,
)
