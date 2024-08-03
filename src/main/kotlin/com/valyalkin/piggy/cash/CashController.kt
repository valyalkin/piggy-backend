package com.valyalkin.piggy.cash

import com.valyalkin.piggy.cash.holdings.CashHolding
import com.valyalkin.piggy.cash.model.CashTransactionDTO
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/cash")
class CashController(
    private val cashService: CashService,
) {
    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    fun depositCash(
        @RequestBody @Valid cashTransactionDTO: CashTransactionDTO,
    ) {
        cashService.depositCash(cashTransactionDTO)
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    fun withdrawCash(
        @RequestBody @Valid cashTransactionDTO: CashTransactionDTO,
    ) {
        cashService.withdrawCash(cashTransactionDTO)
    }

    @GetMapping("/balance/{userId}")
    @ResponseStatus(HttpStatus.OK)
    fun getBalance(
        @PathVariable("userId") userId: String,
    ): List<CashHolding> {
        return cashService.getCashBalanceForUser(userId)
    }
}
