package net.example;

import java.net.URI;
import java.util.Hashtable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.jms.CompletionListener;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.Consumes;
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
public class Sender {
    private static Logger log = LoggerFactory.getLogger(Sender.class);
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

            Sender.connectionFactory = factory;

            // HTTP

            String httpHost = System.getenv("HTTP_HOST");
            String httpPort = System.getenv("HTTP_PORT");

            if (httpHost == null) httpHost = "0.0.0.0";
            if (httpPort == null) httpPort = "8080";

            URI uri = URI.create(String.format("http://%s:%s/", httpHost, httpPort));
            ResourceConfig rc = new ResourceConfig(Sender.class);

            NettyHttpContainerProvider.createHttp2Server(uri, rc, null);
        } catch (Exception e) {
            log.error("Startup failed", e);
            System.exit(1);
        }
    }

    private JMSContext jmsContext = Sender.connectionFactory.createContext();

    @POST
    @Path("/send")
    @Consumes("text/plain")
    @Produces("text/plain")
    public String send(String string) {
        synchronized (jmsContext) {
            Topic topic = jmsContext.createTopic("example/strings");
            JMSProducer producer = jmsContext.createProducer();

            producer.setAsync(new SendListener()).send(topic, string);
        }

        log.info("SENDER: Sent message '{}'", string);

        return "OK\n";
    }

    class SendListener implements CompletionListener {
        @Override
        public void onCompletion(Message message) {
            try {
                log.info("SENDER: Receiver acknowledged '{}'", message.getBody(String.class));
            } catch (JMSException e) {
                log.error("Message access error", e);
            }
        }

        @Override
        public void onException(Message message, Exception e) {
            log.info("SENDER: Send failed: {}", e.toString());
        }
    }

    @GET
    @Path("/ready")
    @Produces("text/plain")
    public String ready() {
        log.info("SENDER: Readiness checked");

        return "OK\n";
    }
}
