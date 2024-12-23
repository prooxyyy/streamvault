package me.proo0xy.env;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EnvironmentVariableKey {
    API_HOST("VAULT_HOST", "127.0.0.1"),
    STORAGE_PATH("VAULT_STORAGE_PATH", "/app/storage"),
    BACKUP_PATH("VAULT_BACKUP_PATH", "/app/backup");

    private final String envKey;
    private final String defaultValue;
}
