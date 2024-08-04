package com.valyalkin.piggy.stocks.transactions

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/stocks/transactions")
class StockTransactionController(
    private val service: StockTransactionsService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTransaction(
        @RequestBody @Valid stockTransactionDTO: StockTransactionDTO,
    ) {
        return service.processTransaction(stockTransactionDTO)
    }
}
