package com.valyalkin.piggy.stocks.transactions

import com.valyalkin.piggy.cash.holdings.CashHoldingsRepository
import com.valyalkin.piggy.configuration.BusinessException
import com.valyalkin.piggy.configuration.SystemException
import com.valyalkin.piggy.stocks.holdings.StockHoldingEntity
import com.valyalkin.piggy.stocks.holdings.StockHoldingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class StockTransactionsService(
    private val stockTransactionRepository: StockTransactionRepository,
    private val cashHoldingsRepository: CashHoldingsRepository,
    private val stockHoldingsRepository: StockHoldingsRepository,
) {
    @Transactional
    fun buy(stockTransactionDTO: StockTransactionDTO): StockTransactionEntity {
        val (userId, ticker, date, quantity, price, currency) = stockTransactionDTO

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

            val previousTotalValue = previousAveragePrice.multiply(BigDecimal.valueOf(previousQuantity))
            val incomingTotalValue = price.multiply(BigDecimal.valueOf(quantity))

            val newTotalValue = previousTotalValue.plus(incomingTotalValue)
            val newQuantity = previousQuantity.plus(quantity)
            val newAveragePrice = newTotalValue.divide(BigDecimal.valueOf(newQuantity), RoundingMode.DOWN)

            stockHoldingsRepository.save(
                stockHolding.copy(
                    quantity = newQuantity,
                    averagePrice = newAveragePrice,
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
                transactionType = TransactionType.BUY,
                currency = currency,
            ),
        )
    }

    @Transactional
    fun sell(stockTransactionDTO: StockTransactionDTO): StockTransactionEntity {
        val (userId, ticker, date, quantity, price, currency) = stockTransactionDTO

        // 1. Find stock holding and check if there is enough to sell
        val stockHoldings = stockHoldingsRepository.getByUserIdAndTicker(userId, ticker)

        val stockHolding = stockHoldings.firstOrNull()

        stockHolding?.let {
            // 2. Calculate new stock holding, only quantity changes, but the average price stays the same
            val holdingQuantity = it.quantity
            val newQuantity = holdingQuantity - quantity
            if (newQuantity > 0) {
                stockHoldingsRepository.save(stockHolding.copy(quantity = newQuantity))
            } else if (newQuantity == 0L) {
                // Delete the holding from the list of holdings, it is sold completely
                stockHoldingsRepository.deleteById(it.id)
            } else {
                throw BusinessException("Sell quantity is bigger than a holding")
            }

            // 3. Increase cash balance for this currency
            val cashHoldings = cashHoldingsRepository.findByUserIdAndCurrency(userId, currency)

            val cashHolding = cashHoldings.firstOrNull()

            val releasedCash = price.multiply(BigDecimal.valueOf(quantity))

            // This should never happen, it is a system error
            if (cashHolding == null) {
                throw SystemException(
                    "Cash holding of currency $currency doesn't exist for this user",
                )
            } else {
                cashHoldingsRepository.save(
                    cashHolding.copy(
                        totalAmount = cashHolding.totalAmount.plus(releasedCash),
                    ),
                )
            }

            // 4. Record the transaction
            return stockTransactionRepository.save(
                StockTransactionEntity(
                    userId = userId,
                    ticker = ticker,
                    date = date,
                    quantity = quantity,
                    price = price,
                    transactionType = TransactionType.SELL,
                    currency = currency,
                ),
            )
        } ?: throw BusinessException("There is no stock holding of $ticker for this user")
    }
}
