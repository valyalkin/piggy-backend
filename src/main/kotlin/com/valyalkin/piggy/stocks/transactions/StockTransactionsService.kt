package com.valyalkin.piggy.stocks.transactions

import com.valyalkin.piggy.configuration.BusinessException
import com.valyalkin.piggy.stocks.holdings.StockHoldingEntity
import com.valyalkin.piggy.stocks.holdings.StockHoldingsRepository
import com.valyalkin.piggy.stocks.pl.ReleasedProfitLossEntity
import com.valyalkin.piggy.stocks.pl.ReleasedProfitLossEntityRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

@Service
class StockTransactionsService(
    private val stockTransactionRepository: StockTransactionRepository,
    private val stockHoldingsRepository: StockHoldingsRepository,
    private val releasedProfitLossEntityRepository: ReleasedProfitLossEntityRepository,
) {
    @Transactional
    fun transaction(stockTransactionDTO: StockTransactionDTO): StockTransactionEntity {
        val (userId, ticker, date, quantity, price, currency, transactionType) = stockTransactionDTO
        // Get all transactions by user id and ticker, sorted by date
        val previousTransactions =
            stockTransactionRepository.findByUserIdAndTickerAndCurrencyOrderByDateAsc(
                userId,
                ticker,
                currency,
            )

        // If there are no transactions, record transaction and save stock holding
        if (previousTransactions.isEmpty()) {
            return handleFirstBuy(stockTransactionDTO)
        }

        // Compute latest stock holding and released P/L
        val transactions =
            previousTransactions
                .map {
                    StockTransaction(
                        date = it.date,
                        quantity = it.quantity,
                        price = it.price,
                        transactionType = it.transactionType,
                    )
                }.plus(
                    StockTransaction(
                        date = date,
                        quantity = quantity,
                        price = price,
                        transactionType = transactionType,
                    ),
                ).sortedBy {
                    it.date
                }

        if (transactions.first().transactionType == TransactionType.SELL) {
            throw BusinessException("Transaction cannot be added, first transaction should be BUY")
        }

        // Released PL is affected by SELL transactions
        if (TransactionType.SELL in transactions.map { it.transactionType }) {
            releasedProfitLossEntityRepository.deleteByUserIdAndTickerAndCurrency(
                userId,
                ticker,
                currency,
            )
        }

        var qty = transactions.first().quantity
        var averagePrice = transactions.first().price

        for (i in 1..<transactions.size) {
            val currentTransactionType = transactions[i].transactionType

            val incomingPrice = transactions[i].price
            val incomingQuantity = transactions[i].quantity
            val incomingDate = transactions[i].date

            when (currentTransactionType) {
                TransactionType.BUY -> {
                    val previousTotal = averagePrice.multiply(BigDecimal.valueOf(qty))
                    val incomingTotal = incomingPrice.multiply(BigDecimal.valueOf(incomingQuantity))

                    val newTotal = previousTotal.plus(incomingTotal)
                    val newQuantity = qty.plus(incomingQuantity)
                    qty = newQuantity
                    averagePrice = newTotal.divide(BigDecimal.valueOf(qty), RoundingMode.DOWN)
                }
                TransactionType.SELL -> {
                    val newQuantity = qty.minus(incomingQuantity)
                    if (newQuantity < 0) {
                        throw BusinessException(
                            "Cannot add SELL transaction, cannot sell more than current holding at this time",
                        )
                    }
                    qty = newQuantity
                    val releasedPL = (incomingPrice.minus(averagePrice)).multiply(BigDecimal.valueOf(incomingQuantity))
                    releasedProfitLossEntityRepository.save(
                        ReleasedProfitLossEntity(
                            userId = userId,
                            ticker = ticker,
                            date = incomingDate,
                            amount = releasedPL,
                            currency = currency,
                        ),
                    )
                }
            }
        }

        val stockHolding = stockHoldingsRepository.getByUserIdAndTicker(userId, ticker).firstOrNull()

        if (stockHolding == null) {
            stockHoldingsRepository.save(
                StockHoldingEntity(
                    userId = userId,
                    ticker = ticker,
                    quantity = qty,
                    averagePrice = averagePrice,
                    currency = currency,
                ),
            )
        } else {
            stockHoldingsRepository.save(
                stockHolding.copy(
                    quantity = qty,
                    averagePrice = averagePrice,
                ),
            )
        }

        return stockTransactionRepository.save(
            StockTransactionEntity(
                userId = userId,
                ticker = ticker,
                date = date,
                quantity = quantity,
                price = price,
                transactionType = transactionType,
                currency = currency,
            ),
        )
    }

    fun getTransactions(
        userId: String,
        currency: Currency,
        page: Int,
        pageSize: Int?,
    ): Page<StockTransactionEntity> {
        val sortBy = Sort.by(DATE_FIELD).descending()
        val pageable = PageRequest.of(page - 1, pageSize ?: DEFAULT_PAGE_SIZE, sortBy)
        return stockTransactionRepository.findByUserIdAndCurrency(
            userId,
            currency,
            pageable,
        )
    }

    private data class StockTransaction(
        val date: OffsetDateTime,
        val quantity: Long,
        val price: BigDecimal,
        val transactionType: TransactionType,
    )

    private fun handleFirstBuy(stockTransactionDTO: StockTransactionDTO): StockTransactionEntity {
        val (userId, ticker, date, quantity, price, currency, transactionType) = stockTransactionDTO

        if (transactionType == TransactionType.SELL) {
            throw BusinessException("Transaction cannot be added, first transaction should be BUY")
        }

        val stockHolding = stockHoldingsRepository.getByUserIdAndTicker(userId, ticker).firstOrNull()

        if (stockHolding == null) {
            stockHoldingsRepository.save(
                StockHoldingEntity(
                    userId = userId,
                    ticker = ticker,
                    quantity = quantity,
                    averagePrice = price,
                    currency = currency,
                ),
            )
        } else {
            stockHoldingsRepository.deleteById(stockHolding.id)
            stockHoldingsRepository.save(
                StockHoldingEntity(
                    userId = userId,
                    ticker = ticker,
                    quantity = quantity,
                    averagePrice = price,
                    currency = currency,
                ),
            )
        }

        return stockTransactionRepository.save(
            StockTransactionEntity(
                userId = userId,
                ticker = ticker,
                date = date,
                quantity = quantity,
                price = price,
                transactionType = transactionType,
                currency = currency,
            ),
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 10
        private const val DATE_FIELD = "date"
    }
}
