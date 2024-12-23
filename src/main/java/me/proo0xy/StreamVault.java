package me.proo0xy;

import me.proo0xy.api.WebSocketController;
import me.proo0xy.data.DataStore;
import me.proo0xy.env.Environment;
import me.proo0xy.env.EnvironmentVariableKey;

import java.net.InetSocketAddress;

public class StreamVault {

    public static final Environment ENVIRONMENT = new Environment();

    public static void main(String[] args) {
        System.out.println("Starting StreamVault...");

        long autoSaveInterval = 60;

        String storagePath = ENVIRONMENT.getEnv(EnvironmentVariableKey.STORAGE_PATH, EnvironmentVariableKey.STORAGE_PATH.getDefaultValue());
        String backupPath = ENVIRONMENT.getEnv(EnvironmentVariableKey.BACKUP_PATH, EnvironmentVariableKey.BACKUP_PATH.getDefaultValue());

        String websocketHostname = ENVIRONMENT.getEnv(EnvironmentVariableKey.API_HOST, EnvironmentVariableKey.API_HOST.getDefaultValue());
        int webSocketPort = 9000;

        DataStore.initialize(storagePath, backupPath, autoSaveInterval);
        DataStore dataStore = DataStore.getInstance();

        WebSocketController webSocketController = new WebSocketController(new InetSocketAddress(websocketHostname, webSocketPort), dataStore);
        webSocketController.start();

        System.out.println("StreamVault is ready.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down StreamVault...");
            dataStore.shutdown();
            try {
                webSocketController.stop();
            } catch (Exception ignored) {
                // nothing ;0
            }
            System.out.println("StreamVault shut down successfully.");
        }));
    }
}
