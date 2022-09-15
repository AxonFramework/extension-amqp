/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.extensions.amqp.autoconfig;

import org.axonframework.config.Configuration;
import org.axonframework.extensions.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.extensions.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.extensions.amqp.eventhandling.PackageRoutingKeyResolver;
import org.axonframework.extensions.amqp.eventhandling.RoutingKeyResolver;
import org.axonframework.extensions.amqp.eventhandling.spring.SpringAMQPPublisher;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.config.SpringAxonConfiguration;
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
 * Test class validating the {@link AMQPAutoConfiguration}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
@ExtendWith(SpringExtension.class)
class AMQPAutoConfigurationTest {

    @Test
    void defaultAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DefaultContext.class))
                .withPropertyValues("axon.amqp.exchange=test", "axon.axonserver.enabled=false")
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
    void autoConfigurationWithConfiguredEventSerializer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SerializerContext.class))
                .withPropertyValues("axon.amqp.exchange=test", "axon.axonserver.enabled=false")
                .run(context -> {
                    Configuration config = context.getBean(SpringAxonConfiguration.class).getObject();
                    assertNotEquals(config.serializer(), config.eventSerializer());

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
            DataSourceAutoConfiguration.class
    })
    static class DefaultContext {

    }

    @EnableAutoConfiguration(exclude = {
            JmxAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceAutoConfiguration.class
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
