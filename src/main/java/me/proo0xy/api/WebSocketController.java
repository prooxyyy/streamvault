package me.proo0xy.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.proo0xy.api.models.WebSocketActionMessage;
import me.proo0xy.api.models.crud.VaultPutMessage;
import me.proo0xy.data.DataEntry;
import me.proo0xy.data.DataStore;
import me.proo0xy.utils.GsonUtil;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocketController handles WebSocket client connections, processes incoming messages
 * to create/update/delete data entries, and broadcasts data changes to subscribed clients.
 */
public class WebSocketController extends WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);
    private final Gson gson;
    private final DataStore dataStore;
    private final Map<String, Set<WebSocket>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Constructs a new WebSocketController.
     *
     * @param address   The address to bind the WebSocket server to.
     * @param dataStore The DataStore instance to interact with for data updates.
     */
    public WebSocketController(InetSocketAddress address, DataStore dataStore) {
        super(address);
        this.gson = GsonUtil.getGson();
        this.dataStore = dataStore;
        // Subscribe to DataStore updates
        this.dataStore.subscribe(this::handleDataEntry);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        log.info("WebSocket client connected: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        log.info("WebSocket client disconnected: {}", conn.getRemoteSocketAddress());
        // Удаляем клиента из всех подписок
        removeClientFromAllSubscriptions(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        log.info("Received message from {}: {}", conn.getRemoteSocketAddress(), message);
        try {
            WebSocketActionMessage actionMessage = gson.fromJson(message, WebSocketActionMessage.class);

            if (actionMessage == null || actionMessage.getAction() == null) {
                sendError(conn, "Invalid message format: 'action' is required.");
                return;
            }

            switch (actionMessage.getAction()) {
                case GET:
                    String getKey = actionMessage.getData().trim();
                    handleGet(conn, getKey);
                    break;
                case PUT:
                    VaultPutMessage vaultPutMessage = gson.fromJson(actionMessage.getData(), VaultPutMessage.class);
                    handlePut(conn, vaultPutMessage);
                    break;
                case REMOVE:
                    String keyToRemove = actionMessage.getData().trim();
                    handleRemove(conn, keyToRemove);
                    break;
                case SUBSCRIBE:
                    String subscribeKey = actionMessage.getData().trim();
                    handleSubscribe(conn, subscribeKey);
                    break;
                case UNSUBSCRIBE:
                    String unsubscribeKey = actionMessage.getData().trim();
                    handleUnsubscribe(conn, unsubscribeKey);
                    break;
                default:
                    sendError(conn, "Unknown action: " + actionMessage.getAction());
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", message, e);
            sendError(conn, "Invalid message format.");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.error("WebSocket error: ", ex);
        if (conn != null) {
            conn.closeConnection(1011, "Internal server error");
        }
    }

    @Override
    public void onStart() {
        log.info("WebSocket server started on port: {}", getPort());
    }

    /**
     * Handles a DataEntry update from DataStore and broadcasts it to subscribed clients.
     *
     * @param entry The DataEntry to handle.
     */
    private void handleDataEntry(DataEntry entry) {
        String key = entry.getKey();
        String message = gson.toJson(entry);

        sendUpdateToSubscribers(key, message);
    }

    /**
     * Processes a GET action to get a data entry.
     *
     * @param conn The WebSocket connection.
     * @param key  The key of the entry to get.
     */
    private void handleGet(WebSocket conn, String key) {
        if (key == null || key.isEmpty()) {
            sendError(conn, "GET action requires a non-empty 'key'.");
            return;
        }

        DataEntry dataEntry = dataStore.get(key);
        if (dataEntry != null) {
            sendSuccess(conn, dataEntry.getValue().toString());
        } else {
            sendError(conn, "Entry not found for key: " + key);
        }
    }

    /**
     * Processes a PUT action to create or update a data entry.
     *
     * @param conn    The WebSocket connection.
     * @param message The VaultPutMessage message.
     */
    private void handlePut(WebSocket conn, VaultPutMessage message) {
        if (message == null || message.getKey() == null || message.getValue() == null) {
            sendError(conn, "PUT action requires 'key' and 'value'.");
            return;
        }

        String key = message.getKey().trim();
        Object value = message.getValue();

        // Дополнительная валидация данных
        if (!isValidValue(value)) {
            sendError(conn, "Unsupported value type.");
            return;
        }

        dataStore.put(key, value);
        sendSuccess(conn, "Entry created/updated successfully.");
    }

    /**
     * Processes a REMOVE action to delete a data entry.
     *
     * @param conn The WebSocket connection.
     * @param key  The key of the entry to remove.
     */
    private void handleRemove(WebSocket conn, String key) {
        if (key == null || key.isEmpty()) {
            sendError(conn, "REMOVE action requires a non-empty 'key'.");
            return;
        }

        DataEntry removed = dataStore.remove(key);
        if (removed != null) {
            sendSuccess(conn, "Entry removed successfully.");
        } else {
            sendError(conn, "Entry not found for key: " + key);
        }
    }

    /**
     * Handles the SUBSCRIBE action to subscribe the client to updates of a particular key.
     *
     * @param conn WebSocket connection.
     * @param key  Record key for subscription.
     */
    private void handleSubscribe(WebSocket conn, String key) {
        if (key == null || key.isEmpty()) {
            sendError(conn, "SUBSCRIBE action requires a non-empty 'key'.");
            return;
        }

        subscriptions.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(conn);
        sendSuccess(conn, "Subscribed to key '" + key + "' successfully.");
        log.info("Client {} subscribed to key: {}", conn.getRemoteSocketAddress(), key);
    }

    /**
     * Handles the UNSUBSCRIBE action to unsubscribe the client from updates to a particular key.
     *
     * @param conn WebSocket connection.
     * @param key  Record key for unsubscription.
     */
    private void handleUnsubscribe(WebSocket conn, String key) {
        if (key == null || key.isEmpty()) {
            sendError(conn, "UNSUBSCRIBE action requires a non-empty 'key'.");
            return;
        }

        Set<WebSocket> subscribersSet = subscriptions.get(key);
        if (subscribersSet != null) {
            subscribersSet.remove(conn);
            if (subscribersSet.isEmpty()) {
                subscriptions.remove(key);
            }
            sendSuccess(conn, "Unsubscribed from key '" + key + "' successfully.");
            log.info("Client {} unsubscribed from key: {}", conn.getRemoteSocketAddress(), key);
        } else {
            sendError(conn, "No active subscriptions for key: " + key);
        }
    }

    /**
     * Sends an update message only to clients subscribed to the specified key.
     *
     * @param key     The key of the data entry.
     * @param message The update message in JSON format.
     */
    private void sendUpdateToSubscribers(String key, String message) {
        Set<WebSocket> subscribersSet = subscriptions.get(key);
        if (subscribersSet != null) {
            for (WebSocket client : subscribersSet) {
                client.send(message);
                log.info("Sent update for key '{}' to client: {}", key, client.getRemoteSocketAddress());
            }
        }
    }

    /**
     * Sends a JSON-formatted error message to the specified client.
     *
     * @param conn  The WebSocket connection.
     * @param error The error message.
     */
    private void sendError(WebSocket conn, String error) {
        JsonObject errorMessage = new JsonObject();
        errorMessage.addProperty("status", "error");
        errorMessage.addProperty("message", error);
        conn.send(gson.toJson(errorMessage));
        log.warn("Sent error to client {}: {}", conn.getRemoteSocketAddress(), error);
    }

    /**
     * Sends a JSON-formatted success message to the specified client.
     *
     * @param conn    The WebSocket connection.
     * @param message The success message.
     */
    private void sendSuccess(WebSocket conn, String message) {
        JsonObject successMessage = new JsonObject();
        successMessage.addProperty("status", "success");
        successMessage.addProperty("message", message);
        conn.send(gson.toJson(successMessage));
        log.info("Sent success message to client {}: {}", conn.getRemoteSocketAddress(), message);
    }

    /**
     * Removes the client from all active subscriptions.
     *
     * @param conn The WebSocket connection of the client.
     */
    private void removeClientFromAllSubscriptions(WebSocket conn) {
        for (Map.Entry<String, Set<WebSocket>> entry : subscriptions.entrySet()) {
            Set<WebSocket> subscribersSet = entry.getValue();
            if (subscribersSet.remove(conn)) {
                log.info("Removed client {} from subscription of key: {}", conn.getRemoteSocketAddress(), entry.getKey());
                if (subscribersSet.isEmpty()) {
                    subscriptions.remove(entry.getKey());
                }
            }
        }
    }

    /**
     * Validates the type of the value being stored.
     *
     * @param value The value to validate.
     * @return true if the value type is supported; false otherwise.
     */
    private boolean isValidValue(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }
}
