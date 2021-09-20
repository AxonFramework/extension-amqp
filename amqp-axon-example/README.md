# AMQP Axon Springboot Example

This is an example SpringBoot application using Axon's AMQP extension. It uses an In-Memory Event Store and RabbitMQ as the Message Bus.

## How to run

### Preparation

You will need `docker` and `docker-compose` to run this example.

Please run:

```bash
docker compose -f ./amqp-axon-example/docker-compose.yaml up -d
```

This will start RabbitMQ with default values.

Now build the application by running:

```bash
mvn clean package -f ./amqp-axon-example
```

### Running example application

You can start the application by running `java -jar ./amqp-axon-example/target/amqp-axon-example.jar`.

You can access the `rabbitmq` UI on [http://localhost:15672/](http://localhost:15672/) (using the default `guest`/`guest`credentials) where you can see the queues, bindings and exchanges used by Axon and inspect the messages on them.
