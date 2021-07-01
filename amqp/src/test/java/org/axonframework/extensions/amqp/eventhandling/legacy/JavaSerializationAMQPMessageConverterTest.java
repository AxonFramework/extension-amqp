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

package org.axonframework.extensions.amqp.eventhandling.legacy;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.extensions.amqp.eventhandling.AMQPMessage;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.time.Instant;

import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.common.DateTimeUtils.parseInstant;
import static org.axonframework.extensions.amqp.eventhandling.utils.TestSerializer.secureXStreamSerializer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link JavaSerializationAMQPMessageConverter}.
 *
 * @author Allard Buijze
 */
class JavaSerializationAMQPMessageConverterTest {

    private JavaSerializationAMQPMessageConverter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new JavaSerializationAMQPMessageConverter(secureXStreamSerializer());
    }

    @Test
    void testWriteAndReadEventMessage() {
        EventMessage<?> expected = GenericEventMessage.asEventMessage("SomePayload")
                                                      .withMetaData(MetaData.with("key", "value"));
        // Parsing and formatting the Instant to simulate the process a converter would take
        Instant expectedTimestamp = parseInstant(formatInstant(expected.getTimestamp()));

        AMQPMessage amqpMessage = testSubject.createAMQPMessage(expected);
        EventMessage<?> result =
                testSubject.readAMQPMessage(amqpMessage.getBody(), amqpMessage.getProperties().getHeaders())
                           .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEquals(expected.getIdentifier(), result.getIdentifier());
        assertEquals(expected.getMetaData(), result.getMetaData());
        assertEquals(expected.getPayload(), result.getPayload());
        assertEquals(expected.getPayloadType(), result.getPayloadType());
        assertEquals(expectedTimestamp, result.getTimestamp());
    }

    @Test
    void testWriteAndReadDomainEventMessage() {
        DomainEventMessage<?> expected =
                new GenericDomainEventMessage<>("Stub", "1234", 1L, "Payload", MetaData.with("key", "value"));
        // Parsing and formatting the Instant to simulate the process a converter would take
        Instant expectedTimestamp = parseInstant(formatInstant(expected.getTimestamp()));

        AMQPMessage amqpMessage = testSubject.createAMQPMessage(expected);
        EventMessage<?> result =
                testSubject.readAMQPMessage(amqpMessage.getBody(), amqpMessage.getProperties().getHeaders())
                           .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertTrue(result instanceof DomainEventMessage);
        assertEquals(expected.getIdentifier(), result.getIdentifier());
        assertEquals(expected.getMetaData(), result.getMetaData());
        assertEquals(expected.getPayload(), result.getPayload());
        assertEquals(expected.getPayloadType(), result.getPayloadType());
        assertEquals(expectedTimestamp, result.getTimestamp());
        assertEquals(
                expected.getAggregateIdentifier(), ((DomainEventMessage<?>) result).getAggregateIdentifier()
        );
        assertEquals(expected.getSequenceNumber(), ((DomainEventMessage<?>) result).getSequenceNumber());
    }
}
