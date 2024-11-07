package com.valyalkin.piggy.data.eod

import com.valyalkin.piggy.stocks.transactions.Currency
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "end_of_day")
open class EndOfDayPriceDataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "ticker") val ticker: String = "",
    @Temporal(TemporalType.DATE) val date: LocalDate = LocalDate.now(),
    val price: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING) val currency: Currency = Currency.USD,
)

interface EndOfDayPriceDataRepository : JpaRepository<EndOfDayPriceDataEntity, UUID> {
    @Query(
        """
        SELECT MAX(e.date)
            FROM EndOfDayPriceDataEntity e
            WHERE e.ticker = :ticker
    """,
    )
    fun findLatestPriceDateForTicker(
        @Param("ticker") ticker: String,
    ): LocalDate?
}
