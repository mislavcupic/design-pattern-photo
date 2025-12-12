package hr.algebra.nrako.photoapp_backend.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.StorageClient;
import com.google.cloud.storage.Storage;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

@Getter
@Configuration
@Profile("!test")
public class FirebaseConfig {

    private static final Logger logger = Logger.getLogger(FirebaseConfig.class.getName());

    @Value("${firebase.config.path}")
    private Resource firebaseConfigResource;

    @Value("${firebase.bucket.name}")
    private String firebaseBucketName;

    private FirebaseApp firebaseApp;

    @PostConstruct
    public void initialize() {
        if (FirebaseApp.getApps().isEmpty()) {
            try (InputStream serviceAccount = firebaseConfigResource.getInputStream()) {

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setStorageBucket(firebaseBucketName)
                        .build();

                firebaseApp = FirebaseApp.initializeApp(options);
                logger.info("Firebase successfully initialized.");

            } catch (IOException e) {
                logger.severe("Error initializing Firebase: " + e.getMessage());
                throw new RuntimeException("Failed to initialize Firebase. Check file location and permissions.", e);
            }
        } else {
            firebaseApp = FirebaseApp.getInstance();
            logger.info("Firebase App already initialized, reusing.");
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth() {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    @Bean
    public Storage firebaseStorage() {
        return StorageClient.getInstance(firebaseApp).bucket().getStorage();
    }

    @Bean
    public com.google.cloud.storage.Bucket firebaseBucket() {
        return StorageClient.getInstance(firebaseApp).bucket();
    }
}

