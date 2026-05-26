package io.github.hectorvent.floci.services.docdb.proxy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocDbTcpProxyTest {

    @Test
    void relaysRawBytesAndStopsListener() throws Exception {
        int backendPort = freePort();
        int proxyPort = freePort();

        AtomicReference<Throwable> backendFailure = new AtomicReference<>();
        ServerSocket backendServer = new ServerSocket(backendPort);
        Thread backendThread = Thread.ofPlatform().daemon(true).start(() -> {
            try (ServerSocket server = backendServer;
                 Socket socket = server.accept();
                 InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                byte[] buffer = new byte[256];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (Throwable t) {
                backendFailure.set(t);
            }
        });

        DocDbTcpProxy proxy = new DocDbTcpProxy("cluster-1", "127.0.0.1", backendPort);
        try {
            proxy.start(proxyPort);

            byte[] payload = new byte[]{0x00, 0x01, (byte) 0xFF, 0x7F, 0x10};
            try (Socket client = new Socket("127.0.0.1", proxyPort);
                 InputStream in = client.getInputStream();
                 OutputStream out = client.getOutputStream()) {
                out.write(payload);
                out.flush();

                byte[] response = in.readNBytes(payload.length);
                assertArrayEquals(payload, response);
            }
        } finally {
            proxy.stop();
        }

        assertThrows(IOException.class, () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", proxyPort), 500);
            }
        });

        backendThread.join(2000);
        assertTrue(!backendThread.isAlive(), "backend echo server should exit after client disconnects");
        assertTrue(backendFailure.get() == null, () -> "backend server failed: " + backendFailure.get());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
