package com.rgu.clicklogger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;

@SpringBootApplication
public class ClickloggerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClickloggerApplication.class, args);
        
        // --- Firebase Initialization Block ---
        try {
            InputStream serviceAccount = null;
            
            // Enterprise Standard: Check for Environment Variable first (For Render Deployment)
            String secretFilePath = System.getenv("FIREBASE_KEY_PATH");
            
            if (secretFilePath != null && new File(secretFilePath).exists()) {
                // Production Mode (Render)
                serviceAccount = new FileInputStream(secretFilePath);
                System.out.println("SECURE MODE: Loading Firebase credentials from Render Secret File...");
            } else {
                // Local Development Mode (Oyage laptop eke)
                serviceAccount = ClickloggerApplication.class.getClassLoader()
                        .getResourceAsStream("serviceAccountKey.json");
                System.out.println("LOCAL MODE: Loading Firebase credentials from resources...");
            }

            if (serviceAccount == null) {
                System.err.println("CRITICAL ERROR: Firebase credentials not found!");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("SUCCESS: Firebase initialized successfully. Ready to receive taps!");
            }
        } catch (Exception e) {
            System.err.println("Firebase initialization failed:");
            e.printStackTrace();
        }
    }
}