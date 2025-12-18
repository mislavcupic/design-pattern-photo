package hr.algebra.nrako.photoapp_backend.configuration;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class FirebaseTestConfig {

    static {
        System.setProperty("FIRESTORE_EMULATOR_HOST", "localhost:8085");
        System.setProperty("FIREBASE_STORAGE_EMULATOR_HOST", "localhost:8083");
        System.setProperty("GCLOUD_PROJECT", "demo-project");
    }

    @Bean
    @Primary
    public FirebaseAuth firebaseAuth() {
        return Mockito.mock(FirebaseAuth.class);
    }

    @Bean
    @Primary
    public Firestore firestore() {
        return FirestoreOptions.newBuilder()
                .setProjectId("demo-project")
                .setHost("localhost:8085")
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
    }

    @Bean
    @Primary
    public Storage googleCloudStorage() {
        return StorageOptions.newBuilder()
                .setProjectId("demo-project")
                .setHost("http://localhost:8083")
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
    }

    @Bean
    @Primary
    public Bucket storageBucket(Storage storage) {
        String bucketName = "demo-test.appspot.com";
        try {
            // Pokušaj dohvatiti bucket
            Bucket bucket = storage.get(bucketName);
            if (bucket != null) return bucket;

            // Ako bucket ne postoji, a emulator ne dopušta create (501),
            // vraćamo mock objekt kako se Spring Context ne bi srušio
            Bucket mockBucket = Mockito.mock(Bucket.class);
            Mockito.when(mockBucket.getName()).thenReturn(bucketName);
            return mockBucket;
        } catch (Exception e) {
            // U slučaju "501 Not Implemented", vraćamo mock
            return Mockito.mock(Bucket.class);
        }
    }
}