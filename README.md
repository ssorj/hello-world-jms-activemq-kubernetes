# Getting started with JMS and ActiveMQ on Kubernetes

This guide shows you how to send and receive messages using [Apache
Qpid JMS](http://qpid.apache.org/components/jms/index.html) and
[ActiveMQ Artemis](https://activemq.apache.org/artemis/index.html) on
[Kubernetes](https://kubernetes.io/).  It uses the [AMQP
1.0](http://www.amqp.org/) message protocol to send and receive
messages.

## Overview

The example application has three parts:

* An AMQP 1.0 message broker, Artemis

* A sender service exposing an HTTP endpoint that converts HTTP
  requests into AMQP 1.0 messages.  It sends the messages to a queue
  called `example/strings` on the broker.

* A receiver service that exposes another HTTP endpoint, this time one
  that causes it to consume an AMQP message from `example/strings` and
  convert it to an HTTP response.

The sender and the receiver use the Qpid JMS API to perform messaging
operations.

## Prerequisites

* You must have access to a Kubernetes instance and be logged in.
  [Minikube](https://kubernetes.io/docs/setup/minikube/)
  provides a way to run Kubernetes in your local environment.

## Deploying the services on Kubernetes

1. Use the `oc new-project` command to create a new namespace for the
   example services.

        oc new-project hello-world-jms

1. If you haven't already, use `git` to clone the example code to your
   local environment.

        git clone https://github.com/amq-io/hello-world-jms-kubernetes

1. Change directory to the example project.  The subsequent commands
   assume it is your current directory.

        cd hello-world-jms-kubernetes/

1. Use the `oc apply` command to load the project templates.

        oc apply -f templates/

1. Deploy the broker service.

        oc new-app --template=amq-broker-71-basic \
          -p APPLICATION_NAME=broker \
          -p IMAGE_STREAM_NAMESPACE=$(oc project -q) \
          -p AMQ_PROTOCOL=amqp \
          -p AMQ_QUEUES=example/strings \
          -p AMQ_USER=example \
          -p AMQ_PASSWORD=example

1. Deploy the message sender service.

        oc new-app --template=hello-world-jms-sender

1. Deploy the message receiver service.

        oc new-app --template=hello-world-jms-receiver

1. Use your browser to check the status of your services.  You should
   see three services ("applications") in the overview, each with one
   pod.

## Exercising the application

The application exposes two HTTP endpoints, one for sending messages
and one for receiving them.

    http://<sender-host>/api/send
    http://<receiver-host>/api/receive

The `<sender-host>` and `<receiver-host>` values vary with each
deployment.  Use the web interface to find the precise values.  They
are listed on the right side of each service ("application") listed in
the overview.

To send a message, use the `curl` command.  The value you supply for
the `string` field is used as the message payload.

    curl -X POST -d hello -H "Content-type: tex/plain" http://<sender-host>/api/send

If things go as planned, it will return `OK`.  If things go awry, add
the `-v` flag to see more about what's happening.

To then receive the string back, use the `curl` command again against
the receiver.  If no message is available, it will print `null`.

    curl -X POST http://<receiver-host>/api/receive

Upon success, you should see the message you sent echoed back in the
response.  Here's some sample output from a few operations:

```console
$ curl -X POST -d "hello 1" -H "Content-type: tex/plain" http://<sender-service>/api/send
OK
$ curl -X POST -d "hello 2" -H "Content-type: tex/plain" http://sender-t2.6923.rh-us-east-1.openshiftapps.com/api/send
OK
$ curl -X POST http://receiver-t2.6923.rh-us-east-1.openshiftapps.com/api/receive
hello 1
$ curl -X POST http://receiver-t2.6923.rh-us-east-1.openshiftapps.com/api/receive
hello 2
```

## Exploring the example code

### Maven coordinates

This example uses the
[Apache Qpid implementation](http://qpid.apache.org/components/jms/index.html)
of the JMS API.  To add Qpid JMS your own project, use the following
coordinates in your `pom.xml` file:

```xml
<dependency>
  <groupId>org.apache.qpid</groupId>
  <artifactId>qpid-jms-client</artifactId>
  <version>0.37.0</version>
</dependency>
```

### HTTP endpoints

The sender and receiver use JAX-RS to expose their (very simple!)
APIs.  The lifecycle of these HTTP operations is out of sync with that
of the long-lived messaging connections, so in-memory queues are used
to communicate between the threads handling HTTP requests and the ones
processing AMQP messages.

For example, this is how the send endpoint stores strings for use by
the sender messaging thread:

```java
static final LinkedBlockingQueue<String> strings = new LinkedBlockingQueue<>();

/* ... */

@POST
@Path("/send")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.TEXT_PLAIN)
public String send(@FormParam("string") String string) {
    strings.add(string);
    return "OK\n";
}
```

_From [Sender.java](https://github.com/amq-io/hello-world-jms-openshift/blob/master/sender/src/main/java/net/example/Sender.java#L45)_

### Establishing connections

The sender and receiver services contain essentially identical code
for making a connection to the broker:

```java
String url = String.format("failover:(amqp://%s:%s)", host, port);
String address = "example/strings";

Hashtable<Object, Object> env = new Hashtable<Object, Object>();
env.put("connectionfactory.factory1", url);

InitialContext context = new InitialContext(env);
ConnectionFactory factory = (ConnectionFactory) context.lookup("factory1");
Connection conn = factory.createConnection(user, password);

System.out.println(String.format("SENDER: Connecting to '%s'", url));

conn.start();
```

_From [SenderMessagingThread.java](https://github.com/amq-io/hello-world-jms-openshift/blob/master/sender/src/main/java/net/example/SenderMessagingThread.java#L42)_

JMS uses a connection URI to configure new connections.  See the
[Qpid JMS docs](http://qpid.apache.org/releases/qpid-jms-0.37.0/docs/index.html#connection-uri)
for more information about the syntax and options.

### Sending messages

When a user posts a new string to the `/api/send` endpoint, the string
is placed on an in-memory queue, `Sender.strings` below.  The
`SenderMessagingThread` class then takes the string, converts it to a
message, and sends it to the `example/strings` queue on the broker.

```java
Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
Queue queue = session.createQueue(address);
MessageProducer producer = session.createProducer(queue);

/* ... */

while (true) {
    try {
        String string = Sender.strings.take();
        TextMessage message = session.createTextMessage(string);

        producer.send(message);

        System.out.println(String.format("SENDER: Sent message '%s'", string));
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

_From [SenderMessagingThread.java](https://github.com/amq-io/hello-world-jms-openshift/blob/master/sender/src/main/java/net/example/SenderMessagingThread.java#L65)_

### Receiving messages

The receiver consumes messages from the `example/strings` queue on the
broker and places them on another in-memory queue (here referenced
using `Receiver.strings`) for the `/api/receive` endpoint to draw
from.

```java
Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
Queue queue = session.createQueue(address);
MessageConsumer consumer = session.createConsumer(queue);

/* ... */

while (true) {
    try {
        TextMessage message = (TextMessage) consumer.receive();
        String string = message.getText();

        Receiver.strings.add(string);

        System.out.println(String.format("RECEIVER: Received message '%s'", string));
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

_From [ReceiverMessagingThread.java](https://github.com/amq-io/hello-world-jms-openshift/blob/master/receiver/src/main/java/net/example/ReceiverMessagingThread.java#L62)_

## More information

* [AMQP](http://www.amqp.org/)
* [Apache ActiveMQ Artemis](https://activemq.apache.org/artemis/index.html)
* [Apache Qpid JMS](http://qpid.apache.org/components/jms/index.html)
* [JMS API reference](https://docs.oracle.com/javaee/7/api/index.html?javax/jms/package-summary.html)
* [Kubernetes](https://kubernetes.io/)
