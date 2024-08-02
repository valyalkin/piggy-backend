package com.valyalkin.piggy.transactions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/stocks/transactions")
class StockTransactionController(
    private val stockTransactionRepository: StockTransactionRepository,
) {
    @GetMapping
    fun getTransactions(): List<StockTransaction> = stockTransactionRepository.findAll().toList()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTransaction(
        @RequestBody stockTransactionDTO: StockTransactionDTO,
    ): StockTransaction {
        val stockTransaction =
            StockTransaction(
                userId = stockTransactionDTO.userId,
                ticker = stockTransactionDTO.ticker,
                date = stockTransactionDTO.date,
                quantity = stockTransactionDTO.quantity,
                price = stockTransactionDTO.price,
                transactionType = stockTransactionDTO.transactionType,
                currency = stockTransactionDTO.currency,
            )

        return stockTransactionRepository.save(stockTransaction)
    }
}
