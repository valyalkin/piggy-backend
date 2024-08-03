package com.valyalkin.piggy.cash.transactions

import com.valyalkin.piggy.stocks.transactions.Currency
import jakarta.persistence.*
import org.springframework.data.repository.CrudRepository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

interface CashTransactionRepository : CrudRepository<CashTransactionEntity, UUID>

@Entity
@Table(name = "cash_transactions")
data class CashTransactionEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id") val userId: String,
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING) val currency: Currency,
    @Enumerated(EnumType.STRING) val cashTransactionType: CashTransactionType,
    @Temporal(TemporalType.TIMESTAMP) val date: OffsetDateTime,
)

enum class CashTransactionType {
    WITHDRAW,
    DEPOSIT,
}
