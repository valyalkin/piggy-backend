package com.valyalkin.piggy.stocks

import com.valyalkin.piggy.stocks.holdings.StockHoldingEntity
import com.valyalkin.piggy.stocks.holdings.StockHoldingsService
import com.valyalkin.piggy.stocks.transactions.Currency
import com.valyalkin.piggy.stocks.transactions.StockTransactionDTO
import com.valyalkin.piggy.stocks.transactions.StockTransactionEntity
import com.valyalkin.piggy.stocks.transactions.StockTransactionsService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/stocks")
class StocksController(
    private val transactionsService: StockTransactionsService,
    private val holdingsService: StockHoldingsService,
) {
    @PostMapping("/transaction")
    @ResponseStatus(HttpStatus.CREATED)
    fun transaction(
        @RequestBody @Valid stockTransactionDTO: StockTransactionDTO,
    ): StockTransactionEntity = transactionsService.transaction(stockTransactionDTO)

    @GetMapping("/transactions")
    @ResponseStatus(HttpStatus.OK)
    fun transactions(
        @RequestParam(name = "userId") userId: String,
        @RequestParam(name = "currency") currency: Currency,
        @RequestParam(name = "page") page: Int,
        @RequestParam(name = "pageSize", required = false) pageSize: Int?,
    ): Page<StockTransactionEntity> = transactionsService.getTransactions(userId, currency, page, pageSize)

    @GetMapping("/holdings")
    @ResponseStatus(HttpStatus.OK)
    fun holdings(
        @RequestParam(name = "userId") userId: String,
        @RequestParam(name = "currency") currency: Currency,
    ): List<StockHoldingEntity> = holdingsService.stockHoldings(userId, currency)
}
