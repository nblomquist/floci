package io.github.hectorvent.floci.services.docdb.container;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.common.docker.ContainerBuilder;
import io.github.hectorvent.floci.core.common.docker.ContainerDetector;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager.ContainerInfo;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager.EndpointInfo;
import io.github.hectorvent.floci.core.common.docker.ContainerLogStreamer;
import io.github.hectorvent.floci.core.common.docker.ContainerSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages backend Docker container lifecycle for DocDB clusters.
 * Starts one stock MongoDB container per cluster and keeps it ephemeral.
 */
@ApplicationScoped
public class DocDbContainerManager {

    private static final Logger LOG = Logger.getLogger(DocDbContainerManager.class);
    private static final int BACKEND_PORT = 27017;
    private static final int BACKEND_READY_DEADLINE_MS = 60_000;
    private static final int BACKEND_READY_RETRY_MS = 100;
    private static final int BACKEND_PROBE_CONNECT_MS = 2_000;

    private final ContainerBuilder containerBuilder;
    private final ContainerLifecycleManager lifecycleManager;
    private final ContainerLogStreamer logStreamer;
    private final ContainerDetector containerDetector;
    private final EmulatorConfig config;
    private final RegionResolver regionResolver;
    private final Map<String, DocDbContainerHandle> activeContainers = new ConcurrentHashMap<>();

    @Inject
    public DocDbContainerManager(ContainerBuilder containerBuilder,
                                 ContainerLifecycleManager lifecycleManager,
                                 ContainerLogStreamer logStreamer,
                                 ContainerDetector containerDetector,
                                 EmulatorConfig config,
                                 RegionResolver regionResolver) {
        this.containerBuilder = containerBuilder;
        this.lifecycleManager = lifecycleManager;
        this.logStreamer = logStreamer;
        this.containerDetector = containerDetector;
        this.config = config;
        this.regionResolver = regionResolver;
    }

    public DocDbContainerHandle start(String clusterId) {
        LOG.infov("Starting DocDB backend container for cluster: {0}", clusterId);

        String containerName = "floci-docdb-" + clusterId;
        lifecycleManager.removeIfExists(containerName);

        ContainerBuilder.Builder specBuilder = containerBuilder.newContainer(config.services().docdb().defaultImage())
                .withName(containerName)
                .withDockerNetwork(config.services().docdb().dockerNetwork())
                .withLogRotation();

        if (!containerDetector.isRunningInContainer()) {
            specBuilder.withPortBindingHost("127.0.0.1")
                    .withDynamicPort(BACKEND_PORT);
        } else {
            specBuilder.withExposedPort(BACKEND_PORT);
        }

        ContainerSpec spec = specBuilder.build();
        ContainerInfo info = lifecycleManager.createAndStart(spec);
        EndpointInfo endpoint = info.getEndpoint(BACKEND_PORT);

        LOG.infov("DocDB backend for cluster {0}: {1}", clusterId, endpoint);

        DocDbContainerHandle handle = new DocDbContainerHandle(
                info.containerId(), clusterId, endpoint.host(), endpoint.port());
        activeContainers.put(clusterId, handle);

        try {
            String shortId = info.containerId().length() >= 8
                    ? info.containerId().substring(0, 8)
                    : info.containerId();
            String logGroup = "/aws/docdb/cluster/" + clusterId + "/mongodb";
            String logStream = logStreamer.generateLogStreamName(shortId);
            String region = regionResolver.getDefaultRegion();

            Closeable logHandle = logStreamer.attach(
                    info.containerId(), logGroup, logStream, region, "docdb:" + clusterId);
            handle.setLogStream(logHandle);

            waitForBackendReady(clusterId, endpoint.host(), endpoint.port());
        } catch (Exception e) {
            // Cleanup the started container so it is not leaked. Log any
            // cleanup failure but let the original readiness/log-stream
            // exception propagate to the caller.
            try {
                stop(handle);
            } catch (Exception cleanupEx) {
                LOG.warnv("Cleanup failed after start error for cluster {0}: {1}",
                        clusterId, cleanupEx.getMessage());
            }
            throw e;
        }

        return handle;
    }

    public void stop(DocDbContainerHandle handle) {
        if (handle == null) {
            return;
        }
        activeContainers.remove(handle.getClusterId());
        lifecycleManager.stopAndRemove(handle.getContainerId(), handle.getLogStream());
    }

    public void stopAll() {
        List<DocDbContainerHandle> handles = new ArrayList<>(activeContainers.values());
        if (!handles.isEmpty()) {
            LOG.infov("Stopping {0} DocDB container(s) on shutdown", handles.size());
        }
        for (DocDbContainerHandle handle : handles) {
            stop(handle);
        }
    }

    private static void waitForBackendReady(String clusterId, String host, int port) {
        long deadline = System.currentTimeMillis() + BACKEND_READY_DEADLINE_MS;
        int attempt = 0;
        while (System.currentTimeMillis() < deadline) {
            attempt++;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), BACKEND_PROBE_CONNECT_MS);
                if (attempt > 1) {
                    LOG.infov("DocDB backend ready for cluster {0} after {1} probe attempt(s)", clusterId, attempt);
                }
                return;
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debugv("DocDB probe for cluster {0} attempt {1}: {2}", clusterId, attempt, e.getMessage());
                }
            }
            try {
                Thread.sleep(BACKEND_READY_RETRY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for DocDB backend " + clusterId, ie);
            }
        }
        throw new RuntimeException(
                "DocDB backend for cluster " + clusterId + " did not become ready on " + host + ":" + port
                        + " within " + BACKEND_READY_DEADLINE_MS + "ms");
    }
}
