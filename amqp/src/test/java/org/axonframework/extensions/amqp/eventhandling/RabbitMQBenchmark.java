/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.extensions.amqp.eventhandling;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;

/**
 * Application benchmarking this extension by using RabbitMQ in a Test Container.
 *
 * @author Allard Buijze
 */
public class RabbitMQBenchmark {

    private static final int THREAD_COUNT = 10;
    private static final int COMMIT_SIZE = 10;
    private static final int COMMIT_COUNT = 1500;

    public static void main(String[] args) throws IOException, InterruptedException, TimeoutException {
        try (RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq")) {
            rabbitMQContainer.start();
            Address address = new Address(rabbitMQContainer.getHost(), rabbitMQContainer.getAmqpPort());

            final Connection connection = new ConnectionFactory().newConnection(Collections.singletonList(address));
            final Channel channel = connection.createChannel();

            String queueName = channel.queueDeclare().getQueue();
            execute("Transactional and Channel pooling", createChannelPoolSharingThreads(connection, queueName));
            queueName = refreshQueue(channel, queueName);
            execute("Transactional, new Channel per tx", createChannelCreatingThreads(connection, queueName, true));
            queueName = refreshQueue(channel, queueName);
            execute("Non-transactional, new Channel per tx",
                    createChannelCreatingThreads(connection, queueName, false));
            queueName = refreshQueue(channel, queueName);
            execute("Non-transactional, single Channel", createChannelSharingThreads(connection, queueName));
            channel.confirmSelect();
            connection.close();
        }
    }

    private static List<Thread> createChannelCreatingThreads(final Connection connection, final String queueName,
                                                             final boolean transactional) {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads.add(new Thread(() -> {
                try {
                    for (int t = 0; t < COMMIT_COUNT; t++) {
                        final Channel localChannel = connection.createChannel();
                        if (transactional) {
                            localChannel.txSelect();
                        }
                        for (int j = 0; j < COMMIT_SIZE; j++) {
                            localChannel.basicPublish(
                                    "", queueName, null, ("message" + t).getBytes(StandardCharsets.UTF_8)
                            );
                        }
                        if (transactional) {
                            localChannel.txCommit();
                        }
                        localChannel.close();
                    }
                } catch (IOException | TimeoutException e) {
                    e.printStackTrace();
                }
            }));
        }
        return threads;
    }

    private static List<Thread> createChannelSharingThreads(final Connection connection, final String queueName)
            throws IOException {
        List<Thread> threads = new ArrayList<>();
        final Channel localChannel = connection.createChannel();
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads.add(new Thread(() -> {
                try {
                    for (int t = 0; t < COMMIT_COUNT; t++) {
                        for (int j = 0; j < COMMIT_SIZE; j++) {
                            localChannel.basicPublish(
                                    "", queueName, null, ("message" + t).getBytes(StandardCharsets.UTF_8)
                            );
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
        }
        return threads;
    }

    private static List<Thread> createChannelPoolSharingThreads(final Connection connection, final String queueName) {
        List<Thread> threads = new ArrayList<>();
        final Queue<Channel> channels = new ArrayBlockingQueue<>(15);
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads.add(new Thread(() -> {
                try {
                    for (int t = 0; t < COMMIT_COUNT; t++) {
                        Channel localChannel = channels.poll();
                        if (localChannel == null) {
                            localChannel = connection.createChannel();
                        }
                        localChannel.txSelect();
                        for (int j = 0; j < COMMIT_SIZE; j++) {
                            localChannel.basicPublish(
                                    "", queueName, null, ("message" + t).getBytes(StandardCharsets.UTF_8)
                            );
                        }
                        localChannel.txCommit();
                        if (!channels.offer(localChannel)) {
                            localChannel.close();
                        }
                    }
                } catch (IOException | TimeoutException e) {
                    e.printStackTrace();
                }
            }));
        }
        return threads;
    }

    private static void execute(String description, List<Thread> threads) throws InterruptedException {
        long start = System.currentTimeMillis();
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        long end = System.currentTimeMillis();
        System.out.println(description + ". Dispatching took " + (end - start) + " millis");
    }

    private static String refreshQueue(Channel channel, String queueName) throws IOException {
        channel.queueDelete(queueName);
        queueName = channel.queueDeclare().getQueue();
        return queueName;
    }
}
