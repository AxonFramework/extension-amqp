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

package org.axonframework.extensions.amqp.eventhandling.spring;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.extensions.amqp.eventhandling.AMQPMessage;
import org.axonframework.extensions.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.extensions.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.extensions.amqp.eventhandling.utils.TestSerializer.secureXStreamSerializer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the Spring AMQP approach of this extension.
 *
 * @author Allard Buijze
 */
@Testcontainers
class SpringAMQPIntegrationTest {

    // The admin password is set to null,
    //  as the RabbitMQContainer will otherwise set the deprecated RABBITMQ_DEFAULT_PASS environment property.
    // This environment property breaks the test.
    @Container
    private static final RabbitMQContainer RABBIT_MQ_CONTAINER =
            new RabbitMQContainer("rabbitmq").withAdminPassword(null);

    private AMQPMessageConverter messageConverter;
    private SpringAMQPMessageSource springAMQPMessageSource;

    private ConnectionFactory connectionFactory;
    private SimpleMessageListenerContainer listenerContainer;
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void setUp() {
        messageConverter = DefaultAMQPMessageConverter.builder()
                                                      .serializer(secureXStreamSerializer())
                                                      .build();
        springAMQPMessageSource = new SpringAMQPMessageSource(messageConverter);


        connectionFactory = new CachingConnectionFactory(URI.create(RABBIT_MQ_CONTAINER.getAmqpUrl()));
        listenerContainer = new SimpleMessageListenerContainer(connectionFactory);
        listenerContainer.setQueueNames("testQueue");
        listenerContainer.setAutoStartup(false);

        amqpAdmin = new RabbitAdmin(connectionFactory);
        amqpAdmin.declareQueue(new Queue("testQueue", false, false, true));
        amqpAdmin.declareExchange(new FanoutExchange("testExchange", false, true));
        amqpAdmin.declareBinding(new Binding("testQueue", Binding.DestinationType.QUEUE, "testExchange", "", null));
    }

    @AfterEach
    void tearDown() {
        listenerContainer.stop();
        amqpAdmin.deleteExchange("testExchange");
        amqpAdmin.deleteQueue("testQueue");
    }

    @Test
    void testReadFromAMQP() throws Exception {
        listenerContainer.setMessageListener(springAMQPMessageSource);
        CountDownLatch cdl = new CountDownLatch(100);
        springAMQPMessageSource.subscribe(em -> em.forEach(m -> cdl.countDown()));
        listenerContainer.start();

        Connection connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);

        try {
            for (int i = 0; i < 100; i++) {
                AMQPMessage message = messageConverter.createAMQPMessage(asEventMessage("test" + i));
                channel.basicPublish("testExchange", message.getRoutingKey(), false, false,
                                     message.getProperties(), message.getBody());
            }
        } finally {
            channel.close();
            connection.close();
        }

        assertTrue(cdl.await(10, TimeUnit.SECONDS));
    }

    @Test
    void testPublishMessagesFromEventBus() throws Exception {
        SimpleEventBus messageSource = SimpleEventBus.builder().build();
        SpringAMQPPublisher publisher = new SpringAMQPPublisher(messageSource);
        publisher.setConnectionFactory(connectionFactory);
        publisher.setExchangeName("testExchange");
        publisher.setMessageConverter(messageConverter);
        publisher.start();

        messageSource.publish(asEventMessage("test1"), asEventMessage("test2"));

        Connection connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        try {
            assertEquals("test1", readMessage(channel).getPayload());
            assertEquals("test2", readMessage(channel).getPayload());
        } finally {
            channel.close();
            connection.close();
        }
    }

    private EventMessage<?> readMessage(Channel channel) throws IOException {
        GetResponse message = channel.basicGet("testQueue", true);
        assertNotNull(message, "Expected message on the queue");
        return messageConverter.readAMQPMessage(message.getBody(), message.getProps().getHeaders()).orElse(null);
    }
}
