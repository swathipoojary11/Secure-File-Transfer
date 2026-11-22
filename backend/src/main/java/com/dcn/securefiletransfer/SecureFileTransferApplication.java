package com.dcn.securefiletransfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

/**
 * Main Spring Boot Application Entrypoint.
 * 
 * In a Data Communication & Networks (DCN) distributed system:
 * - This entry point boots the REST API layer on HTTP port 8080.
 * - It initializes application directories for temporary staging and verified received files.
 * - In subsequent phases, it kicks off background worker threads for the TLS TCP File Server.
 */
@SpringBootApplication
public class SecureFileTransferApplication {

    public static void main(String[] args) {
        // Ensure storage directories exist prior to starting any services
        initializeStorageDirectories();

        SpringApplication.run(SecureFileTransferApplication.class, args);
        System.out.println("==========================================================");
        System.out.println("  SECURE FILE TRANSFER - DCN BACKEND STARTED SUCCESSFULLY ");
        System.out.println("  REST API Base URL: http://localhost:8085/api/health      ");
        System.out.println("==========================================================");
    }

    /**
     * Creates required local filesystem directories for handling chunked file
     * assembly and permanent verified file storage.
     */
    private static void initializeStorageDirectories() {
        String[] dirs = {"./storage/temp", "./storage/received"};
        for (String dirPath : dirs) {
            File dir = new File(dirPath);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (created) {
                    System.out.println("[STORAGE] Initialized directory: " + dir.getAbsolutePath());
                }
            }
        }
    }
}
