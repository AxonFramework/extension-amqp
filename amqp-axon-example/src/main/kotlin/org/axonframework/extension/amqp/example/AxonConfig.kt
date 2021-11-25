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

package org.axonframework.extension.amqp.example

import com.rabbitmq.client.Channel
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine
import org.axonframework.extension.amqp.example.AMQPAxonExampleApplication.Companion.QUEUE_NAME
import org.axonframework.extensions.amqp.eventhandling.AMQPMessageConverter
import org.axonframework.extensions.amqp.eventhandling.spring.SpringAMQPMessageSource
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AxonConfig {

    /**
     * The SpringAMQPMessageSource allows event processors to read messages from a queue instead of the event store or
     * event bus. It acts as an adapter between Spring AMQP and the SubscribableMessageSource needed by these processors.
     *
     * @param messageConverter Converter to/from AMQP Messages to/from Axon Messages.
     */
    @Bean
    fun amqpMessageSource(messageConverter: AMQPMessageConverter): SpringAMQPMessageSource {
        return object : SpringAMQPMessageSource(messageConverter) {
            @RabbitListener(queues = [QUEUE_NAME])
            override fun onMessage(message: Message?, channel: Channel?) {
                println("amqp event $message received")
                super.onMessage(message, channel)
            }
        }
    }

    /**
     * Creates an InMemoryEventStorageEngine.
     * NOT PRODUCTION READY
     */
    @Bean
    fun storageEngine(): EventStorageEngine = InMemoryEventStorageEngine()

    /**
     * Creates an InMemoryTokenStore.
     * NOT PRODUCTION READY
     */
    @Bean
    fun tokenStore(): TokenStore = InMemoryTokenStore()

}