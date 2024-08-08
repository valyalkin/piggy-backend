package com.valyalkin.piggy.cash

import com.valyalkin.piggy.cash.holdings.CashHolding
import com.valyalkin.piggy.cash.holdings.CashHoldingsRepository
import com.valyalkin.piggy.cash.model.CashTransactionDTO
import com.valyalkin.piggy.cash.transactions.CashTransactionEntity
import com.valyalkin.piggy.cash.transactions.CashTransactionRepository
import com.valyalkin.piggy.cash.transactions.CashTransactionType
import com.valyalkin.piggy.configuration.BusinessException
import com.valyalkin.piggy.configuration.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class CashService(
    private val cashHoldingsRepository: CashHoldingsRepository,
    private val cashTransactionRepository: CashTransactionRepository,
) {
    @Transactional
    fun depositCash(cashTransactionDTO: CashTransactionDTO): CashTransactionEntity {
        // Add the amount to holdings
        // 1. Get all holdings for the given user
        val holdings = cashHoldingsRepository.findByUserId(userId = cashTransactionDTO.userId)

        // 2. Create a new holding or update the existing holding's amount
        val holding = holdings.firstOrNull { it.currency == cashTransactionDTO.currency }

        if (holding == null) {
            val newHolding =
                CashHolding(
                    userId = cashTransactionDTO.userId,
                    totalAmount = cashTransactionDTO.amount,
                    currency = cashTransactionDTO.currency,
                )
            cashHoldingsRepository.save(newHolding)
        } else {
            val oldAmount = holding.totalAmount
            val newAmount = oldAmount.plus(cashTransactionDTO.amount)
            cashHoldingsRepository.save(holding.copy(totalAmount = newAmount))
        }

        // Add transaction to the list of transactions
        return cashTransactionRepository.save(
            CashTransactionEntity(
                userId = cashTransactionDTO.userId,
                amount = cashTransactionDTO.amount,
                currency = cashTransactionDTO.currency,
                cashTransactionType = CashTransactionType.DEPOSIT,
                date = cashTransactionDTO.date,
            ),
        )
    }

    @Transactional
    fun withdrawCash(cashTransactionDTO: CashTransactionDTO): CashTransactionEntity {
        val (userId, amount, currency, date) = cashTransactionDTO

        // 1. Get all cash holdings for the given user
        val holdings = cashHoldingsRepository.findByUserIdAndCurrency(userId = userId, currency = currency)

        if (holdings.isEmpty()) {
            throw BusinessException("Did not find any cash holdings for user $userId and currency $currency")
        }

        // 2. Get a holding for the given currency
        val holding = holdings.firstOrNull()

        holding?.let {
            val oldAmount = it.totalAmount
            val newAmount = oldAmount.minus(amount)

            if (newAmount < BigDecimal.ZERO) {
                throw BusinessException("Amount to withdraw is bigger than available balance")
            } else {
                cashHoldingsRepository.save(it.copy(totalAmount = newAmount))
            }
        } ?: throw NotFoundException("Did not find any cash holdings for user $userId and  currency $currency")

        return cashTransactionRepository.save(
            CashTransactionEntity(
                userId = userId,
                amount = amount,
                currency = currency,
                cashTransactionType = CashTransactionType.WITHDRAW,
                date = date,
            ),
        )
    }

    fun getCashBalanceForUser(userId: String): List<CashHolding> = cashHoldingsRepository.findByUserId(userId)
}
