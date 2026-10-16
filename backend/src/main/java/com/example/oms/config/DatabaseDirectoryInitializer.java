package com.example.oms.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SQLite does not create missing parent folders. This runs after configuration
 * (including .env) is loaded but before the database is opened, and creates the
 * folder for app.db-path, failing fast with a clear message if it is not writable.
 */
public class DatabaseDirectoryInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String dbPath = event.getEnvironment().getProperty("app.db-path", "data/shop.db");
        Path directory = Path.of(dbPath).toAbsolutePath().getParent();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create database folder '" + directory
                    + "'. Check DB_PATH and folder permissions.", e);
        }
        if (!Files.isWritable(directory)) {
            throw new IllegalStateException("Database folder '" + directory + "' is not writable. Check DB_PATH.");
        }
    }
}