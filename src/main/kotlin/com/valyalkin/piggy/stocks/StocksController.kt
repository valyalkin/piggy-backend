package com.valyalkin.piggy.stocks

import com.valyalkin.piggy.stocks.transactions.StockTransactionDTO
import com.valyalkin.piggy.stocks.transactions.StockTransactionEntity
import com.valyalkin.piggy.stocks.transactions.StockTransactionsService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/stocks")
class StocksController(
    private val service: StockTransactionsService,
) {
    @PostMapping("/transaction")
    @ResponseStatus(HttpStatus.CREATED)
    fun transaction(
        @RequestBody @Valid stockTransactionDTO: StockTransactionDTO,
    ): StockTransactionEntity = service.transaction(stockTransactionDTO)
}
