package com.example.oms;

import com.example.oms.config.DatabaseDirectoryInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OmsApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(OmsApplication.class);
        // Creates the SQLite folder (from DB_PATH) before the database connection is opened.
        app.addListeners(new DatabaseDirectoryInitializer());
        app.run(args);
    }
}