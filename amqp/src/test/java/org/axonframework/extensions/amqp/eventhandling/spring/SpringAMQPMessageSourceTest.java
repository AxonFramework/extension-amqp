/*
 * Copyright (c) 2010-2018. Axon Framework
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

import com.rabbitmq.client.Channel;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.extensions.amqp.eventhandling.AMQPMessage;
import org.axonframework.extensions.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.List;
import java.util.function.Consumer;

import static org.axonframework.extensions.amqp.eventhandling.utils.TestSerializer.secureXStreamSerializer;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SpringAMQPMessageSource}.
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 */
class SpringAMQPMessageSourceTest {

    @Test
    void testMessageListenerInvokesAllEventProcessors() {
        //noinspection unchecked
        Consumer<List<? extends EventMessage<?>>> eventProcessor = mock(Consumer.class);
        DefaultAMQPMessageConverter messageConverter =
                DefaultAMQPMessageConverter.builder()
                                           .serializer(secureXStreamSerializer())
                                           .build();
        SpringAMQPMessageSource testSubject = new SpringAMQPMessageSource(messageConverter);
        testSubject.subscribe(eventProcessor);

        AMQPMessage message = messageConverter.createAMQPMessage(GenericEventMessage.asEventMessage("test"));

        MessageProperties messageProperties = new MessageProperties();
        message.getProperties().getHeaders().forEach(messageProperties::setHeader);
        testSubject.onMessage(new Message(message.getBody(), messageProperties), mock(Channel.class));

        verify(eventProcessor).accept(argThat(item -> item.size() == 1 && item.get(0).getPayload().equals("test")));
    }

    @Test
    void testMessageListenerIgnoredOnUnsupportedMessageType() {
        //noinspection unchecked
        Consumer<List<? extends EventMessage<?>>> eventProcessor = mock(Consumer.class);
        DefaultAMQPMessageConverter messageConverter =
                DefaultAMQPMessageConverter.builder()
                                           .serializer(secureXStreamSerializer())
                                           .build();
        SpringAMQPMessageSource testSubject = new SpringAMQPMessageSource(messageConverter);
        testSubject.subscribe(eventProcessor);

        AMQPMessage message = messageConverter.createAMQPMessage(GenericEventMessage.asEventMessage("test"));

        MessageProperties messageProperties = new MessageProperties();
        message.getProperties().getHeaders().forEach(messageProperties::setHeader);
        // we make the message unreadable
        messageProperties.getHeaders().remove("axon-message-type");
        testSubject.onMessage(new Message(message.getBody(), messageProperties), mock(Channel.class));

        //noinspection unchecked
        verify(eventProcessor, never()).accept(any(List.class));
    }

    @Test
    void testMessageListenerInvokedOnUnknownSerializedType() {
        //noinspection unchecked
        Consumer<List<? extends EventMessage<?>>> eventProcessor = mock(Consumer.class);
        XStreamSerializer serializer = secureXStreamSerializer();
        DefaultAMQPMessageConverter messageConverter = DefaultAMQPMessageConverter.builder()
                                                                                  .serializer(serializer)
                                                                                  .build();
        SpringAMQPMessageSource testSubject = new SpringAMQPMessageSource(messageConverter);
        testSubject.subscribe(eventProcessor);

        AMQPMessage message = messageConverter.createAMQPMessage(GenericEventMessage.asEventMessage("test"));

        MessageProperties messageProperties = new MessageProperties();
        message.getProperties().getHeaders().forEach(messageProperties::setHeader);
        // we make the type unknown
        messageProperties.setHeader("axon-message-type", "strong");
        testSubject.onMessage(new Message(message.getBody(), messageProperties), mock(Channel.class));

        //noinspection unchecked
        verify(eventProcessor).accept(any(List.class));
    }
}
