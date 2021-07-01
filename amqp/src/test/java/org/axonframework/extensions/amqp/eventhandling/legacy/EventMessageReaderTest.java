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
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Instant;
import java.util.Collections;

import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.common.DateTimeUtils.parseInstant;
import static org.axonframework.extensions.amqp.eventhandling.utils.TestSerializer.secureXStreamSerializer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EventMessageReader}.
 *
 * @author Allard Buijze
 */
class EventMessageReaderTest {

    @Test
    void testStreamEventMessage() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XStreamSerializer serializer = secureXStreamSerializer();
        EventMessageWriter out = new EventMessageWriter(new DataOutputStream(baos), serializer);

        GenericEventMessage<String> expected = new GenericEventMessage<>(
                "This is the payload", Collections.<String, Object>singletonMap("metaKey", "MetaValue")
        );
        // Parsing and formatting the Instant to simulate the process a reader/writer would take
        Instant expectedTimestamp = parseInstant(formatInstant(expected.getTimestamp()));

        out.writeEventMessage(expected);
        EventMessageReader in =
                new EventMessageReader(new DataInputStream(new ByteArrayInputStream(baos.toByteArray())), serializer);
        EventMessage<Object> result = in.readEventMessage();

        assertEquals(expected.getIdentifier(), result.getIdentifier());
        assertEquals(expected.getPayloadType(), result.getPayloadType());
        assertEquals(expectedTimestamp, result.getTimestamp());
        assertEquals(expected.getMetaData(), result.getMetaData());
        assertEquals(expected.getPayload(), result.getPayload());
    }

    @Test
    void testStreamDomainEventMessage() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XStreamSerializer serializer = secureXStreamSerializer();
        EventMessageWriter out = new EventMessageWriter(new DataOutputStream(baos), serializer);

        GenericDomainEventMessage<String> expected = new GenericDomainEventMessage<>(
                "type", "AggregateID", 1L, "This is the payload",
                Collections.<String, Object>singletonMap("metaKey", "MetaValue")
        );
        // Parsing and formatting the Instant to simulate the process a converter would take
        Instant expectedTimestamp = parseInstant(formatInstant(expected.getTimestamp()));

        out.writeEventMessage(expected);
        EventMessageReader in = new EventMessageReader(
                new DataInputStream(new ByteArrayInputStream(baos.toByteArray())), serializer);
        EventMessage<Object> serializedMessage = in.readEventMessage();
        assertTrue(serializedMessage instanceof DomainEventMessage);

        DomainEventMessage<?> result = (DomainEventMessage<?>) serializedMessage;

        assertEquals(expected.getIdentifier(), result.getIdentifier());
        assertEquals(expected.getPayloadType(), result.getPayloadType());
        assertEquals(expectedTimestamp, result.getTimestamp());
        assertEquals(expected.getMetaData(), result.getMetaData());
        assertEquals(expected.getPayload(), result.getPayload());
        assertEquals(expected.getAggregateIdentifier(), result.getAggregateIdentifier());
        assertEquals(expected.getSequenceNumber(), result.getSequenceNumber());
    }
}
