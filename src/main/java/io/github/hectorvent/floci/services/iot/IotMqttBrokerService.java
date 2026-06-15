package io.github.hectorvent.floci.services.iot;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.moquette.BrokerConstants;
import io.moquette.broker.Server;
import io.moquette.broker.config.FluentConfig;
import io.moquette.broker.config.IConfig;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptConnectionLostMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.interception.messages.InterceptUnsubscribeMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class IotMqttBrokerService {

    private static final Logger LOG = Logger.getLogger(IotMqttBrokerService.class);
    private static final String INTERNAL_CLIENT_ID = "floci-iot";

    private final EmulatorConfig config;
    private final IotPublishEventRecorder eventRecorder;
    private final Instance<IotService> iotService;
    private final Map<String, Set<String>> subscriptionsByClient = new ConcurrentHashMap<>();
    private Server server;

    @Inject
    public IotMqttBrokerService(EmulatorConfig config, IotPublishEventRecorder eventRecorder,
                                Instance<IotService> iotService) {
        this.config = config;
        this.eventRecorder = eventRecorder;
        this.iotService = iotService;
    }

    void onStart(@Observes StartupEvent ignored) {
        if (!config.services().iot().enabled() || !config.services().iot().mqtt().enabled()) {
            LOG.info("IoT MQTT broker disabled by configuration");
            return;
        }
        if (!config.services().iot().mqtt().autoStart()) {
            LOG.info("IoT MQTT broker auto-start disabled by configuration");
            return;
        }
        startIfEnabled();
    }

    void onStop(@Observes ShutdownEvent ignored) {
        stop();
    }

    synchronized void startIfEnabled() {
        if (!config.services().iot().enabled() || !config.services().iot().mqtt().enabled()) {
            return;
        }
        if (server != null) {
            return;
        }

        IConfig brokerConfig = new FluentConfig()
                .host(config.services().iot().mqtt().host())
                .port(config.services().iot().mqtt().port())
                .disablePersistence()
                .build();
        brokerConfig.setProperty(BrokerConstants.ALLOW_RESERVED_PUBLISH_PREFIXES, "$aws/");

        try {
            Server broker = new Server();
            broker.startServer(brokerConfig, List.of(new PublishInterceptor()));
            server = broker;
            LOG.infov("IoT MQTT broker started on {0}:{1}",
                    config.services().iot().mqtt().host(), config.services().iot().mqtt().port());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start IoT MQTT broker", e);
        }
    }

    synchronized void stop() {
        if (server == null) {
            return;
        }
        server.stopServer();
        server = null;
        subscriptionsByClient.clear();
        LOG.info("IoT MQTT broker stopped");
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    void publish(String topic, byte[] payload) {
        Server broker = server;
        if (broker == null) {
            return;
        }
        ByteBuf buffer = Unpooled.wrappedBuffer(payload == null ? new byte[0] : payload);
        MqttPublishMessage message = MqttMessageBuilders.publish()
                .topicName(topic)
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(false)
                .payload(buffer)
                .properties(MqttProperties.NO_PROPERTIES)
                .build();
        broker.internalPublish(message, INTERNAL_CLIENT_ID);
    }

    boolean disconnectClient(String clientId, boolean cleanSession) {
        Server broker = server;
        if (broker == null) {
            return false;
        }
        boolean disconnected = cleanSession
                ? broker.disconnectAndPurgeClientState(clientId)
                : broker.disconnectClient(clientId);
        if (disconnected) {
            subscriptionsByClient.remove(clientId);
        }
        return disconnected;
    }

    Optional<ConnectionInfo> getConnection(String clientId) {
        Server broker = server;
        if (broker == null) {
            return Optional.empty();
        }
        return broker.listConnectedClients().stream()
                .filter(client -> clientId.equals(client.getClientID()))
                .findFirst()
                .map(client -> new ConnectionInfo(client.getClientID(), client.getAddress(), client.getPort()));
    }

    List<String> listSubscriptions(String clientId) {
        return subscriptionsByClient.getOrDefault(clientId, Set.of()).stream()
                .sorted()
                .toList();
    }

    private final class PublishInterceptor extends AbstractInterceptHandler {
        @Override
        public String getID() {
            return "floci-iot-publish";
        }

        @Override
        public void onPublish(InterceptPublishMessage message) {
            ByteBuf payload = message.getPayload();
            byte[] bytes = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), bytes);
            String topic = message.getTopicName();

            if (topic.startsWith("$aws/")) {
                iotService.get().handleReservedMqttPublish(topic, bytes, IotMqttBrokerService.this::publish);
                return;
            }

            iotService.get().publish(topic, bytes);
        }

        @Override
        public void onSubscribe(InterceptSubscribeMessage message) {
            subscriptionsByClient.computeIfAbsent(message.getClientID(), ignored -> ConcurrentHashMap.newKeySet())
                    .add(message.getTopicFilter());
        }

        @Override
        public void onUnsubscribe(InterceptUnsubscribeMessage message) {
            Set<String> subscriptions = subscriptionsByClient.get(message.getClientID());
            if (subscriptions != null) {
                subscriptions.remove(message.getTopicFilter());
                if (subscriptions.isEmpty()) {
                    subscriptionsByClient.remove(message.getClientID());
                }
            }
        }

        @Override
        public void onDisconnect(InterceptDisconnectMessage message) {
            subscriptionsByClient.remove(message.getClientID());
        }

        @Override
        public void onConnectionLost(InterceptConnectionLostMessage message) {
            subscriptionsByClient.remove(message.getClientID());
        }

        @Override
        public void onSessionLoopError(Throwable error) {
            LOG.warnv("IoT MQTT session loop error: {0}", error.getMessage());
        }
    }

    record ConnectionInfo(String clientId, String address, int port) {
    }
}
