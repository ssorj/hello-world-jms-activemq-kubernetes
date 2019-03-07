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

## Links

[Presentation](https://docs.google.com/presentation/d/1kOsWwLcJWZGoCF8O_NPUB0jkAre9LMhE2VETnafxcMw/edit?usp=sharing)
