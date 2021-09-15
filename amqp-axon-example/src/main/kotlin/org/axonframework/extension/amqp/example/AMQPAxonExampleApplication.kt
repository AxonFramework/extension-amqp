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

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling


/**
 * Starting point.
 */
fun main(args: Array<String>) {
    runApplication<AMQPAxonExampleApplication>(*args)
}

/**
 * Main application class.
 */
@SpringBootApplication
@EnableScheduling
class AMQPAxonExampleApplication {

    @Bean
    fun eventsExchange(): TopicExchange {
        return TopicExchange(topicExchangeName)
    }

    @Bean
    fun eventsQueue(): Queue {
        return Queue(queueName, true)
    }

    @Bean
    fun eventsBinding(queue: Queue, exchange: TopicExchange): Binding {
        return BindingBuilder.bind(queue)
            .to(exchange)
            .with("#")
    }

    companion object {

        const val topicExchangeName = "exchange"

        const val queueName = "queue"
    }

}
