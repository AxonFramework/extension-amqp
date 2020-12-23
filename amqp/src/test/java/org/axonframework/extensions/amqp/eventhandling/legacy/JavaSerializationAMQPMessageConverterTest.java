package org.axonframework.extensions.amqp.eventhandling.legacy;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.extensions.amqp.eventhandling.AMQPMessage;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

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
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage("SomePayload")
                                                          .withMetaData(MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);
        EventMessage<?> actualResult = testSubject.readAMQPMessage(amqpMessage.getBody(),
                                                                   amqpMessage.getProperties().getHeaders())
                                                  .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEquals(eventMessage.getIdentifier(), actualResult.getIdentifier());
        assertEquals(eventMessage.getMetaData(), actualResult.getMetaData());
        assertEquals(eventMessage.getPayload(), actualResult.getPayload());
        assertEquals(eventMessage.getPayloadType(), actualResult.getPayloadType());
        assertEquals(eventMessage.getTimestamp(), actualResult.getTimestamp());
    }

    @Test
    void testWriteAndReadDomainEventMessage() {
        DomainEventMessage<?> eventMessage = new GenericDomainEventMessage<>(
                "Stub", "1234", 1L, "Payload", MetaData.with("key", "value")
        );
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);
        EventMessage<?> actualResult = testSubject.readAMQPMessage(amqpMessage.getBody(),
                                                                   amqpMessage.getProperties().getHeaders())
                                                  .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertTrue(actualResult instanceof DomainEventMessage);
        assertEquals(eventMessage.getIdentifier(), actualResult.getIdentifier());
        assertEquals(eventMessage.getMetaData(), actualResult.getMetaData());
        assertEquals(eventMessage.getPayload(), actualResult.getPayload());
        assertEquals(eventMessage.getPayloadType(), actualResult.getPayloadType());
        assertEquals(eventMessage.getTimestamp(), actualResult.getTimestamp());
        assertEquals(
                eventMessage.getAggregateIdentifier(), ((DomainEventMessage<?>) actualResult).getAggregateIdentifier()
        );
        assertEquals(eventMessage.getSequenceNumber(), ((DomainEventMessage<?>) actualResult).getSequenceNumber());
    }
}
