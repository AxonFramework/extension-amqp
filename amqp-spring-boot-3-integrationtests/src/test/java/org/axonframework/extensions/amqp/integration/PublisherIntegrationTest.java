/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.extensions.amqp.integration;

import com.rabbitmq.client.Channel;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.extensions.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.extensions.amqp.eventhandling.spring.SpringAMQPMessageSource;
import org.axonframework.extensions.amqp.eventhandling.spring.SpringAMQPPublisher;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvent;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PublisherIntegrationTest {

    static final String TOPIC_EXCHANGE_NAME = "spring-boot-exchange";

    static final String QUEUE_NAME = "spring-boot";

    @Container
    private static final RabbitMQContainer RABBIT_MQ_CONTAINER = new RabbitMQContainer("rabbitmq:3");
    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner();
    }

    @Test
    void canPublishViaRabbitMQ() {
        testApplicationContext
                .withPropertyValues("axon.axonserver.enabled=false")
                .withPropertyValues("axon.amqp.exchange=" + TOPIC_EXCHANGE_NAME)
                .withPropertyValues("spring.rabbitmq.port=" + RABBIT_MQ_CONTAINER.getAmqpPort())
                .withUserConfiguration(DefaultContext.class)
                .run(context -> {
                    SpringAMQPPublisher publisher = context.getBean(SpringAMQPPublisher.class);
                    assertNotNull(publisher);
                    EventGateway eventGateway = context.getBean(EventGateway.class);
                    assertNotNull(eventGateway);
                    publishEvent(eventGateway);
                    SpringAMQPMessageSource messageSource = context.getBean(SpringAMQPMessageSource.class);
                    assertNotNull(messageSource);
                    receiveMessage(messageSource);
                });
    }

    private void publishEvent(EventGateway eventGateway) {
        DomainEventMessage<String> event = createEvent();
        eventGateway.publish(event);
    }

    private void receiveMessage(SpringAMQPMessageSource messageSource) {
        AtomicReference<EventMessage<?>> result = new AtomicReference<>();
        messageSource.subscribe(list -> list.forEach(result::set));
        await().atMost(Duration.ofSeconds(5L)).until(() -> result.get() != null);
        assertEquals("payload", result.get().getPayload());
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    public static class DefaultContext {

        @Bean
        public SpringAMQPMessageSource amqpMessageSource(AMQPMessageConverter messageConverter) {
            return new SpringAMQPMessageSource(messageConverter) {
                @RabbitListener(queues = QUEUE_NAME)
                @Override
                public void onMessage(Message message, Channel channel) {
                    super.onMessage(message, channel);
                }
            };
        }

        @Bean
        public TopicExchange exchange() {
            return new TopicExchange(TOPIC_EXCHANGE_NAME);
        }

        @Bean
        public Queue eventsQueue() {
            return new Queue(QUEUE_NAME, true);
        }

        @Bean
        public Binding eventsBinding(Queue queue, TopicExchange exchange) {
            return BindingBuilder.bind(queue)
                                 .to(exchange)
                                 .with("#");
        }
    }
}
