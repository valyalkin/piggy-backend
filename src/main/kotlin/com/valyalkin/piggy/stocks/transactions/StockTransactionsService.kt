package com.valyalkin.piggy.stocks.transactions

import com.valyalkin.piggy.cash.holdings.CashHolding
import com.valyalkin.piggy.cash.holdings.CashHoldingsRepository
import com.valyalkin.piggy.configuration.BusinessException
import com.valyalkin.piggy.configuration.SystemException
import com.valyalkin.piggy.stocks.holdings.StockHoldingEntity
import com.valyalkin.piggy.stocks.holdings.StockHoldingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class StockTransactionsService(
    private val stockTransactionRepository: StockTransactionRepository,
    private val cashHoldingsRepository: CashHoldingsRepository,
    private val stockHoldingsRepository: StockHoldingsRepository,
) {
    @Transactional
    fun processTransaction(stockTransactionDTO: StockTransactionDTO): StockTransaction {
        return when (stockTransactionDTO.transactionType) {
            TransactionType.BUY -> handleBuy(stockTransactionDTO)
            TransactionType.SELL -> handleSell(stockTransactionDTO)
        }
    }

    private fun handleBuy(stockTransactionDTO: StockTransactionDTO): StockTransaction {
        val (userId, ticker, date, quantity, price, transactionType, currency) = stockTransactionDTO

        // 1. Calculate total amount
        val buyCashAmount = BigDecimal.valueOf(quantity).multiply(price)

        // 2. Check if there is enough cash balance and update cash holding
        val holdings = cashHoldingsRepository.findByUserIdAndCurrency(userId = userId, currency = currency)
        if (holdings.isEmpty()) {
            throw BusinessException("No cash balance was found for the given currency $currency")
        }

        if (holdings.size != 1) {
            throw SystemException("There was more than one cash holding for currency $currency, it shouldn't happen")
        }

        val holding = holdings[0]
        val newAmount = holding.totalAmount.minus(buyCashAmount)
        if (newAmount < BigDecimal.ZERO) {
            throw BusinessException("Not enough cash to buy this stock")
        } else {
            cashHoldingsRepository.save(holding.copy(totalAmount = newAmount))
        }

        // 3. Get stock holding entity for a given ticker and update the average price
        val stockHoldings = stockHoldingsRepository.getByUserIdAndTicker(userId, ticker)

        val stockHolding = stockHoldings.firstOrNull()

        if (stockHolding == null) {
            stockHoldingsRepository.save(
                StockHoldingEntity(
                    userId = userId,
                    ticker = ticker,
                    quantity = quantity,
                    averagePrice = price,
                ),
            )
        } else {
            // Calculate the average price
            val previousQuantity = stockHolding.quantity
            val previousAveragePrice = stockHolding.averagePrice

            val newQuantity = previousQuantity.plus(quantity)
            val newAveragePrice =
                price.plus(previousAveragePrice)
                    .divide(BigDecimal.valueOf(newQuantity))

            stockHoldingsRepository.save(
                stockHolding.copy(
                    quantity = newQuantity,
                    averagePrice = newAveragePrice,
                ),
            )
        }

        return stockTransactionRepository.save(
            StockTransaction(
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

    private fun handleSell(stockTransactionDTO: StockTransactionDTO): StockTransaction {
        val (userId, ticker, date, quantity, price, transactionType, currency) = stockTransactionDTO

        // 1. Find stock holding and check if there is enough to sell
        val stockHoldings = stockHoldingsRepository.getByUserIdAndTicker(userId, ticker)

        val stockHolding = stockHoldings.firstOrNull()

        stockHolding?.let {
            // 2. Calculate new stock holding, only quantity changes, but the average price stays the same
            val holdingQuantity = it.quantity
            val newQuantity = holdingQuantity.minus(quantity)
            if (newQuantity < 0) {
                throw BusinessException("Sell quantity is bigger than a holding")
            }

            stockHoldingsRepository.save(stockHolding.copy(quantity = newQuantity))

            // 3. Increase cash balance for this currency
            val cashHoldings = cashHoldingsRepository.findByUserIdAndCurrency(userId, currency)

            val cashHolding = cashHoldings.firstOrNull()

            val releasedCash = price.multiply(BigDecimal.valueOf(quantity))

            if (cashHolding == null) {
                cashHoldingsRepository.save(
                    CashHolding(
                        userId = userId,
                        totalAmount = releasedCash,
                        currency = currency,
                    ),
                )
            } else {
                cashHoldingsRepository.save(
                    cashHolding.copy(
                        totalAmount = cashHolding.totalAmount.plus(releasedCash),
                    ),
                )
            }

            return stockTransactionRepository.save(
                StockTransaction(
                    userId = userId,
                    ticker = ticker,
                    date = date,
                    quantity = quantity,
                    price = price,
                    transactionType = transactionType,
                    currency = currency,
                ),
            )
        } ?: throw BusinessException("There is no stock holding of $ticker for this user")
    }
}
