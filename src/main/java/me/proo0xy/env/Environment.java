package me.proo0xy.env;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Environment {
    private final Map<EnvironmentVariableKey, String> envParameters = new ConcurrentHashMap<>();

    public Environment() {
        loadFromSystemEnv();
    }

    // Load environment variables from the system
    private void loadFromSystemEnv() {
        Map<String, String> systemEnv = System.getenv();
        EnvironmentVariableKey[] envKeys = EnvironmentVariableKey.values();

        for (EnvironmentVariableKey envKey : envKeys) {
            // If exists environment variable, put it into envParameters, otherwise put default value
            if (systemEnv.containsKey(envKey.name())) {
                envParameters.put(envKey, systemEnv.get(envKey.name()));
            } else {
                envParameters.put(envKey, envKey.getDefaultValue());
            }
        }
    }

    // Add or override an environment variable
    public void setEnv(EnvironmentVariableKey key, String value) {
        envParameters.put(key, value);
    }

    // Retrieve an environment variable
    public String getEnv(EnvironmentVariableKey key) {
        return envParameters.get(key);
    }

    // Retrieve an environment variable with a default value
    public String getEnv(EnvironmentVariableKey key, String defaultValue) {
        return envParameters.getOrDefault(key, defaultValue);
    }

    // Check if an environment variable exists
    public boolean hasEnv(EnvironmentVariableKey key) {
        return envParameters.containsKey(key);
    }

    // Remove an environment variable
    public void removeEnv(EnvironmentVariableKey key) {
        envParameters.remove(key);
    }

    // Clear all environment variables (except system env)
    public void clearCustomEnv() {
        envParameters.clear();
        loadFromSystemEnv();
    }
}
