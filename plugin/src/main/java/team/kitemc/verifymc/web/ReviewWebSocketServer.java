package team.kitemc.verifymc.web;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReviewWebSocketServer extends WebSocketServer {
    private final Set<WebSocket> clients = Collections.synchronizedSet(new HashSet<>());
    private final boolean debug;
    private final org.bukkit.plugin.Plugin plugin;

    public ReviewWebSocketServer(int port, org.bukkit.plugin.Plugin plugin) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    /**
     * Compatible with old constructor
     */
    public ReviewWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        this.plugin = null;
        this.debug = false;
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] ReviewWebSocketServer: " + msg);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        debugLog("WebSocket connection opened: " + conn.getRemoteSocketAddress());
        clients.add(conn);
        debugLog("Total clients connected: " + clients.size());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        debugLog("WebSocket connection closed: " + conn.getRemoteSocketAddress() + ", code: " + code + ", reason: " + reason);
        clients.remove(conn);
        debugLog("Remaining clients: " + clients.size());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        debugLog("Received message from " + conn.getRemoteSocketAddress() + ": " + message);
        // Can handle messages from frontend as needed
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        debugLog("WebSocket error: " + ex.getMessage());
        if (conn != null) {
            debugLog("Error on connection: " + conn.getRemoteSocketAddress());
        }
    }

    @Override
    public void onStart() {
        debugLog("WebSocket server started on port: " + getPort());
    }

    public void broadcastMessage(String message) {
        debugLog("Broadcasting message to " + clients.size() + " clients: " + message);
        synchronized (clients) {
            int sentCount = 0;
            for (WebSocket ws : clients) {
                if (ws.isOpen()) {
                    ws.send(message);
                    sentCount++;
                }
            }
            debugLog("Message sent to " + sentCount + " clients");
        }
    }
} 