package org.axonframework.extensions.amqp.autoconfig;

import org.axonframework.extensions.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.extensions.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.extensions.amqp.eventhandling.PackageRoutingKeyResolver;
import org.axonframework.extensions.amqp.eventhandling.RoutingKeyResolver;
import org.axonframework.extensions.amqp.eventhandling.spring.SpringAMQPPublisher;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.common.ReflectionUtils.getFieldValue;
import static org.axonframework.extensions.amqp.autoconfig.utils.TestSerializer.secureXStreamSerializer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AMQPAutoConfiguration}
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
@ExtendWith(SpringExtension.class)
class AMQPAutoConfigurationTest {

    @Test
    void testDefaultAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DefaultContext.class))
                .withPropertyValues("axon.amqp.exchange=test")
                .run(context -> {
                    assertThat(context).getBeanNames(RoutingKeyResolver.class)
                                       .hasSize(1);
                    assertThat(context).getBean(RoutingKeyResolver.class)
                                       .isExactlyInstanceOf(PackageRoutingKeyResolver.class);

                    assertThat(context).getBeanNames(AMQPMessageConverter.class)
                                       .hasSize(1);
                    assertThat(context).getBean(AMQPMessageConverter.class)
                                       .isExactlyInstanceOf(DefaultAMQPMessageConverter.class);

                    assertThat(context).getBeanNames(SpringAMQPPublisher.class)
                                       .hasSize(1);
                });
    }

    @Test
    void testAutoConfigurationWithConfiguredEventSerializer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SerializerContext.class))
                .withPropertyValues("axon.amqp.exchange=test")
                .run(context -> {
                    AxonConfiguration axonConfig = context.getBean(AxonConfiguration.class);
                    assertNotEquals(axonConfig.serializer(), axonConfig.eventSerializer());

                    AMQPMessageConverter messageConverter = context.getBean(AMQPMessageConverter.class);
                    assertNotNull(messageConverter);

                    Field serializerField = DefaultAMQPMessageConverter.class.getDeclaredField("serializer");
                    Serializer serializer = getFieldValue(serializerField, messageConverter);
                    assertTrue(serializer instanceof JacksonSerializer);

                    assertThat(context).getBeanNames(Serializer.class)
                                       .hasSize(3);
                    assertThat(context).getBean(Serializer.class)
                                       .isExactlyInstanceOf(XStreamSerializer.class);
                    assertThat(context).getBean("myEventSerializer", Serializer.class)
                                       .isExactlyInstanceOf(JacksonSerializer.class);
                });
    }

    @EnableAutoConfiguration(exclude = {
            JmxAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            AxonServerAutoConfiguration.class
    })
    static class DefaultContext {

    }

    @EnableAutoConfiguration(exclude = {
            JmxAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            AxonServerAutoConfiguration.class
    })
    static class SerializerContext {

        @Bean
        @Primary
        public Serializer mySerializer() {
            return secureXStreamSerializer();
        }

        @Bean
        @Qualifier("eventSerializer")
        public Serializer myEventSerializer() {
            return JacksonSerializer.defaultSerializer();
        }
    }
}
