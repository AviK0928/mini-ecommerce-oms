package com.example.oms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class OmsApplication {

    private static final Path DATA_DIR = Path.of("data");

    public static void main(String[] args) {
        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            System.err.println("Cannot create database folder '" + DATA_DIR.toAbsolutePath()
                    + "'. Check that this location is writable. Cause: " + e.getMessage());
            System.exit(1);
        }
        if (!Files.isWritable(DATA_DIR)) {
            System.err.println("Database folder '" + DATA_DIR.toAbsolutePath() + "' is not writable.");
            System.exit(1);
        }
        SpringApplication.run(OmsApplication.class, args);
    }
}