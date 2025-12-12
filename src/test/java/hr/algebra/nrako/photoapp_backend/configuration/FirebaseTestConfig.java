package hr.algebra.nrako.photoapp_backend.configuration;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
// import com.google.firebase.cloud.StorageClient; // Ne treba nam direktno za Bucket bean

import jakarta.annotation.PostConstruct;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;

// Dodaj Mockito za mockiranje
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Configuration
@Profile("test")
public class FirebaseTestConfig {

    private static final Logger configLogger = LoggerFactory.getLogger(FirebaseTestConfig.class);
    private static FirebaseApp testFirebaseApp;

    static {
        System.setProperty("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8085");
        System.setProperty("FIREBASE_AUTH_EMULATOR_HOST", "127.0.0.1:9099");
        System.setProperty("FIREBASE_STORAGE_EMULATOR_HOST", "127.0.0.1:9199"); // Potvrditi port!
        System.setProperty("GCLOUD_PROJECT", "demo-test");
        System.setProperty("GOOGLE_CLOUD_PROJECT", "demo-test");
        configLogger.info("Static: Firebase Emulator Hosts i Project ID postavljeni za testove.");
    }

    @PostConstruct
    public void initializeFirebaseApp() {
        if (testFirebaseApp == null || FirebaseApp.getApps().isEmpty()) {
            configLogger.info("PostConstruct: FirebaseApp nije inicijaliziran ili prazan. Pokušavam inicijalizaciju.");

            AccessToken dummyToken = new AccessToken("dummy-token-for-emulator", new Date(Long.MAX_VALUE));
            GoogleCredentials credentialsForEmulator = GoogleCredentials.create(dummyToken);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentialsForEmulator)
                    .setProjectId("demo-test")
                    .setStorageBucket("demo-test.appspot.com") // I dalje ostavljamo da FirebaseApp ima bucket, ali ne koristimo ga za Storage.get()
                    .build();

            String appName = "photoapp-test";
            if (FirebaseApp.getApps().stream().noneMatch(app -> app.getName().equals(appName))) {
                testFirebaseApp = FirebaseApp.initializeApp(options, appName);
            } else {
                testFirebaseApp = FirebaseApp.getInstance(appName);
            }

            configLogger.info("PostConstruct: FirebaseApp uspješno inicijaliziran.");
        } else {
            configLogger.info("PostConstruct: FirebaseApp je već inicijaliziran, preskačem inicijalizaciju.");
            testFirebaseApp = FirebaseApp.getInstance("photoapp-test");
        }
    }

    @Bean
    @Primary
    public Firestore firestore() {
        configLogger.info("DEBUG: Pružam Firestore bean.");
        return FirestoreClient.getFirestore(testFirebaseApp);
    }

    @Bean
    @Primary
    public FirebaseAuth firebaseAuth() {
        configLogger.info("DEBUG: Pružam FirebaseAuth bean.");
        return FirebaseAuth.getInstance(testFirebaseApp);
    }

    // Bean za Google Cloud Storage API (com.google.cloud.storage.Storage)
    // Ovdje ćemo MOCKIRATI Storage instancu
    @Bean
    @Primary
    public Storage googleCloudStorage() {
        configLogger.info("DEBUG: Pružam MOCK Google Cloud Storage API (Storage) bean.");
        // Kreiramo mock za Storage objekt. Ne treba nam stvarna veza na emulator direktno ovdje
        // jer će PhotoServiceImpl koristiti Bucket objekt koji ćemo također mockati.
        return mock(Storage.class);
    }

    @Bean
    public Bucket storageBucket() {
        Bucket mockBucket = mock(Bucket.class);
        when(mockBucket.getName()).thenReturn("demo-test.appspot.com");

        // Mock the list method to return an empty page of blobs for cleanup
        Page<Blob> mockPage = mock(Page.class);
        when(mockPage.iterateAll()).thenReturn(Collections.emptyList());
        when(mockBucket.list(Mockito.any(Storage.BlobListOption[].class))).thenReturn(mockPage);

        // --- CORRECTED MOCKING FOR Blob.exists() ---

        // Create a mock Blob object
        Blob mockBlob = mock(Blob.class);

        // When get() is called with ANY string, return the mockBlob
        when(mockBucket.get(Mockito.anyString(), Mockito.any(Storage.BlobGetOption[].class))).thenReturn(mockBlob); // For bucket.get() with options
        when(mockBucket.get(Mockito.anyString())).thenReturn(mockBlob); // For bucket.get() without options

        // When exists() is called on the mockBlob, return true (assuming you want it to exist for deletion)
        when(mockBlob.exists()).thenReturn(true);
        // Corrected: Use BlobSourceOption.class for mocking exists with options
        when(mockBlob.exists(Mockito.any(Blob.BlobSourceOption[].class))).thenReturn(true);


        System.out.println("DEBUG: Pružam MOCK Storage Bucket bean.");
        return mockBucket;
    }


}