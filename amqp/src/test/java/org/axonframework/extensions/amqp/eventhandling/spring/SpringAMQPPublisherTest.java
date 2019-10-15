/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.amqp.eventhandling.spring;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.extensions.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.extensions.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.messaging.EventPublicationFailedException;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.junit.*;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SpringAMQPPublisherTest {

    private SpringAMQPPublisher testSubject;

    private SimpleEventBus eventBus;
    private ConnectionFactory connectionFactory;
    private Serializer serializer;

    @Before
    public void setUp() {
        eventBus = SimpleEventBus.builder().build();
        connectionFactory = mock(ConnectionFactory.class);
        serializer = mock(Serializer.class);

        testSubject = new SpringAMQPPublisher(eventBus);
        testSubject.setConnectionFactory(connectionFactory);
        testSubject.setExchangeName("mockExchange");
        testSubject.setTransactional(true);
        AMQPMessageConverter messageConverter = DefaultAMQPMessageConverter.builder()
                                                                           .serializer(serializer)
                                                                           .build();
        testSubject.setMessageConverter(messageConverter);
        testSubject.start();
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        testSubject.shutDown();
    }

    @Test
    public void testSendMessage_NoUnitOfWork() throws Exception {
        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");

        SimpleSerializedObject<byte[]> testObject =
                new SimpleSerializedObject<>("Message".getBytes(StandardCharsets.UTF_8), byte[].class, "String", "0");
        when(serializer.serialize(message.getPayload(), byte[].class)).thenReturn(testObject);

        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));
        eventBus.publish(message);

        verify(transactionalChannel).basicPublish(
                eq("mockExchange"), eq("java.lang"), eq(false), eq(false), any(AMQP.BasicProperties.class),
                isA(byte[].class)
        );
        verify(transactionalChannel).txCommit();
        verify(transactionalChannel).close();
    }

    @Test
    public void testSendMessage_WithTransactionalUnitOfWork() throws Exception {
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(transactionalChannel.isOpen()).thenReturn(true);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);

        SimpleSerializedObject<byte[]> testObject =
                new SimpleSerializedObject<>("Message".getBytes(StandardCharsets.UTF_8), byte[].class, "String", "0");
        when(serializer.serialize(message.getPayload(), byte[].class)).thenReturn(testObject);
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));

        eventBus.publish(message);
        uow.commit();

        verify(transactionalChannel).basicPublish(
                eq("mockExchange"), eq("java.lang"), eq(false), eq(false), any(AMQP.BasicProperties.class),
                isA(byte[].class)
        );
        verify(transactionalChannel).txCommit();
        verify(transactionalChannel).close();
    }

    @Test
    public void testSendMessage_WithTransactionalUnitOfWork_ChannelClosedBeforeCommit() throws Exception {
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(transactionalChannel.isOpen()).thenReturn(false);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);
        SimpleSerializedObject<byte[]> testObject =
                new SimpleSerializedObject<>("Message".getBytes(StandardCharsets.UTF_8), byte[].class, "String", "0");
        when(serializer.serialize(message.getPayload(), byte[].class)).thenReturn(testObject);
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));

        eventBus.publish(message);

        try {
            uow.commit();
            fail("Expected exception");
        } catch (EventPublicationFailedException e) {
            assertNotNull(e.getMessage());
        }

        verify(transactionalChannel).txSelect();
        verify(transactionalChannel).basicPublish(
                eq("mockExchange"), eq("java.lang"), eq(false), eq(false), any(AMQP.BasicProperties.class),
                isA(byte[].class)
        );
        verify(transactionalChannel, never()).txCommit();
        verify(transactionalChannel).txRollback();
        verify(transactionalChannel).close();
    }

    @Test
    public void testSendMessage_WithUnitOfWorkRollback() throws Exception {
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel transactionalChannel = mock(Channel.class);
        when(connection.createChannel(true)).thenReturn(transactionalChannel);
        SimpleSerializedObject<byte[]> testObject =
                new SimpleSerializedObject<>("Message".getBytes(StandardCharsets.UTF_8), byte[].class, "String", "0");
        when(serializer.serialize(message.getPayload(), byte[].class)).thenReturn(testObject);
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));

        eventBus.publish(message);

        verify(transactionalChannel, never()).txRollback();
        verify(transactionalChannel, never()).txCommit();
        verify(transactionalChannel, never()).close();

        uow.rollback();

        verify(transactionalChannel, never()).basicPublish(
                eq("mockExchange"), eq("java.lang"), eq(false), eq(false), any(AMQP.BasicProperties.class),
                isA(byte[].class)
        );
        verify(transactionalChannel, never()).txCommit();
        verify(connectionFactory, never()).createConnection();
    }

    @Test
    public void testSendMessageWithPublisherAck_UnitOfWorkCommitted()
            throws InterruptedException, IOException, TimeoutException {
        testSubject.setTransactional(false);
        testSubject.setWaitForPublisherAck(true);
        testSubject.setPublisherAckTimeout(123);

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel channel = mock(Channel.class);

        when(channel.isOpen()).thenReturn(true);
        when(channel.waitForConfirms()).thenReturn(true);
        when(connection.createChannel(false)).thenReturn(channel);
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        SimpleSerializedObject<byte[]> testObject =
                new SimpleSerializedObject<>("Message".getBytes(StandardCharsets.UTF_8), byte[].class, "String", "0");
        when(serializer.serialize(message.getPayload(), byte[].class)).thenReturn(testObject);
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));

        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);

        eventBus.publish(message);
        verify(channel, never()).waitForConfirms();

        uow.commit();

        verify(channel).confirmSelect();
        verify(channel).basicPublish(
                eq("mockExchange"), eq("java.lang"), eq(false), eq(false), any(AMQP.BasicProperties.class),
                isA(byte[].class)
        );
        verify(channel).waitForConfirmsOrDie(123);
        verify(channel, never()).txSelect();
        verify(channel, never()).txCommit();
        verify(channel, never()).txRollback();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCannotSetPublisherAcksAfterTransactionalSetting() {
        testSubject.setTransactional(true);
        testSubject.setWaitForPublisherAck(true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCannotSetTransactionalBehaviorAfterPublisherAcks() {
        testSubject.setTransactional(false);

        testSubject.setWaitForPublisherAck(true);
        testSubject.setTransactional(true);
    }

    @Test
    public void testSendMessageWithPublisherAck_NoActiveUnitOfWork() throws InterruptedException, IOException {
        testSubject.setTransactional(false);
        testSubject.setWaitForPublisherAck(true);

        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        Channel channel = mock(Channel.class);

        when(channel.waitForConfirms()).thenReturn(true);
        when(connection.createChannel(false)).thenReturn(channel);
        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
        SimpleSerializedObject<byte[]> testObject =
                new SimpleSerializedObject<>("Message".getBytes(StandardCharsets.UTF_8), byte[].class, "String", "0");
        when(serializer.serialize(message.getPayload(), byte[].class)).thenReturn(testObject);
        when(serializer.serialize(message.getMetaData(), byte[].class))
                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));

        eventBus.publish(message);

        verify(channel).confirmSelect();
        verify(channel).basicPublish(
                eq("mockExchange"), eq("java.lang"), eq(false), eq(false), any(AMQP.BasicProperties.class),
                isA(byte[].class)
        );
        verify(channel).waitForConfirmsOrDie();
    }
}
