package com.valyalkin.piggy.stocks.holdings

import com.valyalkin.piggy.stocks.transactions.Currency
import org.springframework.stereotype.Service

@Service
class StockHoldingsService(
    private val stockHoldingsRepository: StockHoldingsRepository,
) {
    fun stockHoldings(
        userId: String,
        currency: Currency,
    ): List<StockHoldingEntity> = stockHoldingsRepository.getAllByUserIdAndCurrency(userId, currency)
}
