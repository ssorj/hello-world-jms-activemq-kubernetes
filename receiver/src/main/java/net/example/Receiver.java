package net.example;

import java.net.URI;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Singleton;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.glassfish.jersey.netty.httpserver.NettyHttpContainerProvider;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Path("/api")
public class Receiver {
    private static Logger log = LoggerFactory.getLogger(Receiver.class);
    private static ConnectionFactory connectionFactory = null;

    public static void main(String[] args) {
        try {
            // AMQP

            String amqpHost = System.getenv("MESSAGING_SERVICE_HOST");
            String amqpPort = System.getenv("MESSAGING_SERVICE_PORT");
            String user = System.getenv("MESSAGING_SERVICE_USER");
            String password = System.getenv("MESSAGING_SERVICE_PASSWORD");

            if (amqpHost == null) amqpHost = "localhost";
            if (amqpPort == null) amqpPort = "5672";
            if (user == null) user = "example";
            if (password == null) password = "example";

            String url = String.format("failover:(amqp://%s:%s)", amqpHost, amqpPort);

            Hashtable<Object, Object> env = new Hashtable<Object, Object>();
            env.put("connectionfactory.factory1", url);

            InitialContext context = new InitialContext(env);
            ConnectionFactory factory = (ConnectionFactory) context.lookup("factory1");

            Receiver.connectionFactory = factory;

            // HTTP

            String host = System.getenv("HTTP_HOST");
            String port = System.getenv("HTTP_PORT");

            if (host == null) host = "0.0.0.0";
            if (port == null) port = "8080";

            URI uri = URI.create(String.format("http://%s:%s/", host, port));
            ResourceConfig rc = new ResourceConfig(Receiver.class);

            NettyHttpContainerProvider.createHttp2Server(uri, rc, null);
        } catch (Exception e) {
            log.error("Startup failed", e);
            System.exit(1);
        }
    }

    private JMSContext jmsContext = Receiver.connectionFactory.createContext();
    private ConcurrentLinkedQueue<String> strings = new ConcurrentLinkedQueue<>();

    public Receiver() {
        synchronized (jmsContext) {
            Queue queue = jmsContext.createQueue("example/strings");
            JMSConsumer consumer = jmsContext.createConsumer(queue);

            consumer.setMessageListener(new ReceiveListener());
        }
    }

    class ReceiveListener implements MessageListener {
        @Override
        public void onMessage(Message message) {
            String string;

            try {
                string = message.getBody(String.class);
            } catch (JMSException e) {
                log.error("Message access error", e);
                return;
            }

            log.info("RECEIVER: Received message '{}'", string);

            strings.add(string);
        }
    }

    @POST
    @Path("/receive")
    @Produces("text/plain")
    public String receive() {
        log.info("RECEIVER: Receive endpoint invoked");

        return strings.poll();
    }

    @GET
    @Path("/ready")
    @Produces("text/plain")
    public String ready() {
        log.info("RECEIVER: Readiness checked");

        return "OK\n";
    }
}
