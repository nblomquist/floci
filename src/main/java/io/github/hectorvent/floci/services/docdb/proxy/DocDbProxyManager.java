package io.github.hectorvent.floci.services.docdb.proxy;

import io.github.hectorvent.floci.core.common.docker.ContainerDetector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active DocumentDB TCP proxies. One proxy per cluster.
 */
@ApplicationScoped
public class DocDbProxyManager {

    private static final Logger LOG = Logger.getLogger(DocDbProxyManager.class);

    private final ContainerDetector containerDetector;
    private final ConcurrentHashMap<String, DocDbTcpProxy> proxies = new ConcurrentHashMap<>();

    @Inject
    public DocDbProxyManager(ContainerDetector containerDetector) {
        this.containerDetector = containerDetector;
    }

    public void startProxy(String clusterId, int proxyPort, String backendHost, int backendPort) {
        String bindHost = containerDetector.isRunningInContainer() ? "0.0.0.0" : "127.0.0.1";
        DocDbTcpProxy proxy = new DocDbTcpProxy(clusterId, bindHost, backendHost, backendPort);
        try {
            proxy.start(proxyPort);
            DocDbTcpProxy previous = proxies.put(clusterId, proxy);
            if (previous != null) {
                previous.stop();
            }
            LOG.infov("Started DocDB proxy for cluster {0} on {1} -> {2}:{3}",
                    clusterId, proxyPort, backendHost, backendPort);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start DocDB proxy for cluster " + clusterId
                    + " on port " + proxyPort, e);
        }
    }

    public void stopProxy(String clusterId) {
        DocDbTcpProxy proxy = proxies.remove(clusterId);
        if (proxy != null) {
            proxy.stop();
            LOG.infov("Stopped DocDB proxy for cluster {0}", clusterId);
        }
    }

    public void stopAll() {
        proxies.values().forEach(DocDbTcpProxy::stop);
        proxies.clear();
        LOG.info("Stopped all DocDB TCP proxies");
    }
}
