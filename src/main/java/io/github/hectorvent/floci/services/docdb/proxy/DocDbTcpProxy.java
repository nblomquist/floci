package io.github.hectorvent.floci.services.docdb.proxy;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Transparent TCP proxy for a single DocumentDB cluster.
 *
 * <p>Relays raw socket bytes in both directions without inspecting or parsing the payload.
 */
public class DocDbTcpProxy {

    private static final Logger LOG = Logger.getLogger(DocDbTcpProxy.class);

    private final String clusterId;
    private final String bindHost;
    private final String backendHost;
    private final int backendPort;

    private volatile boolean running;
    private volatile int proxyPort;
    private ServerSocket serverSocket;

    public DocDbTcpProxy(String clusterId, String backendHost, int backendPort) {
        this(clusterId, "127.0.0.1", backendHost, backendPort);
    }

    public DocDbTcpProxy(String clusterId, String bindHost, String backendHost, int backendPort) {
        this.clusterId = clusterId;
        this.bindHost = bindHost;
        this.backendHost = backendHost;
        this.backendPort = backendPort;
    }

    public void start(int proxyPort) throws IOException {
        this.proxyPort = proxyPort;
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(bindHost, proxyPort));
        running = true;
        Thread.ofPlatform().daemon(true).name("docdb-proxy-accept-" + clusterId).start(this::acceptLoop);
        LOG.infov("DocDB TCP proxy started for cluster {0} on {1}:{2} -> {3}:{4}",
                clusterId, bindHost, proxyPort, backendHost, backendPort);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.warnv("Error closing DocDB proxy server socket for cluster {0} on {1}: {2}",
                    clusterId, proxyPort, e.getMessage());
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                Thread.ofPlatform().daemon(true).name("docdb-proxy-conn-" + clusterId)
                        .start(() -> relay(client));
            } catch (IOException e) {
                if (running) {
                    LOG.warnv("Accept error for DocDB cluster {0} on {1}: {2}",
                            clusterId, proxyPort, e.getMessage());
                }
            }
        }
    }

    private void relay(Socket client) {
        try {
            client.setTcpNoDelay(true);
            Socket backend = new Socket(backendHost, backendPort);
            backend.setTcpNoDelay(true);
            LOG.infov("Relaying DocDB traffic for cluster {0} via {1} -> {2}:{3}",
                    clusterId, proxyPort, backendHost, backendPort);
            bridge(client, backend);
        } catch (IOException e) {
            LOG.warnv("Failed to connect DocDB backend for cluster {0} via {1} -> {2}:{3}: {4}",
                    clusterId, proxyPort, backendHost, backendPort, e.getMessage());
            closeQuietly(client);
        }
    }

    private void bridge(Socket client, Socket backend) {
        Thread clientToBackend = Thread.ofPlatform().daemon(true)
                .name("docdb-relay-c2b-" + clusterId)
                .start(() -> pipe(client, backend));
        Thread backendToClient = Thread.ofPlatform().daemon(true)
                .name("docdb-relay-b2c-" + clusterId)
                .start(() -> pipe(backend, client));
        try {
            clientToBackend.join();
            backendToClient.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void pipe(Socket from, Socket to) {
        byte[] buffer = new byte[8192];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException ignored) {
            // Normal when either side closes the connection.
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Ignore close failures.
        }
    }
}
