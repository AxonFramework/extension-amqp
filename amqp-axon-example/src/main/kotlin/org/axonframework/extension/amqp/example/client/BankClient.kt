/*
 * Copyright (c) 2010-2021. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extension.amqp.example.client

import mu.KLogging
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.extension.amqp.example.api.AccountBalanceQuery
import org.axonframework.extension.amqp.example.api.CreateBankAccountCommand
import org.axonframework.extension.amqp.example.api.DepositMoneyCommand
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*

/**
 * Bank client sending scheduled commands.
 */
@Component
class BankClient(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {

    companion object : KLogging()

    private var accountId = UUID.randomUUID().toString()
    private var amount = 100

    /**
     * Creates account once.
     */
    @Scheduled(initialDelay = 5_000, fixedDelay = 1000_000_000)
    fun createAccount() {
        accountId = UUID.randomUUID().toString()
        logger.info { "creating account '${accountId}'..." }
        commandGateway.send<String>(
            CreateBankAccountCommand(
                bankAccountId = accountId,
                overdraftLimit = 1000
            )
        )
    }

    /**
     * Deposit some money every 20 seconds.
     */
    @Scheduled(initialDelay = 10_000, fixedDelay = 20_000)
    fun deposit() {
        logger.info { "depositing money on '${accountId}'..." }
        commandGateway.send<Any?>(
            DepositMoneyCommand(
                bankAccountId = accountId,
                amountOfMoney = amount.toLong()
            )
        )
        amount = amount.inc()
    }

    /**
     * Query the balance every 15 seconds.
     */
    @Scheduled(initialDelay = 10_000, fixedDelay = 15_000)
    fun balance() {
        val amount = queryGateway.query(
            "org.axonframework.extension.amqp.example.api.AccountBalanceQuery",
            AccountBalanceQuery(
                bankAccountId = accountId
            ),
            ResponseTypes.instanceOf(Long::class.java)
        )
        logger.info { "Current amount is: ${amount.get()}" }
    }
}
