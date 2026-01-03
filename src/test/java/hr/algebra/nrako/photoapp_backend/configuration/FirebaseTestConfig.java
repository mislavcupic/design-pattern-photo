package hr.algebra.nrako.photoapp_backend.configuration;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
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

    private static final String PROJECT_ID = "demo-project";
    private static final String BUCKET_NAME = "demo-project.appspot.com";

    static {
        System.setProperty("FIRESTORE_EMULATOR_HOST", "localhost:8085");
        System.setProperty("FIREBASE_STORAGE_EMULATOR_HOST", "localhost:8083");
        System.setProperty("GCLOUD_PROJECT", PROJECT_ID);
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
                .setProjectId(PROJECT_ID)
                .setHost("localhost:8085")
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
    }

    @Bean
    @Primary
    public Storage googleCloudStorage() {
        return StorageOptions.newBuilder()
                .setProjectId(PROJECT_ID)
                .setHost("http://localhost:8083") // ✅ http:// prefiks obavezan
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
    }

    @Bean
    @Primary
    public Bucket storageBucket(Storage storage) {
        try {
            // ✅ Pokušaj kreirati bucket ako ne postoji
            Bucket bucket = storage.get(BUCKET_NAME);

            if (bucket == null || !bucket.exists()) {
                System.out.println("📦 Creating test bucket: " + BUCKET_NAME);
                bucket = storage.create(BucketInfo.of(BUCKET_NAME));
            }

            System.out.println("✅ Storage bucket initialized: " + BUCKET_NAME);
            return bucket;

        } catch (Exception e) {
            // ✅ Ako emulator nije spreman, vrati mock bucket
            System.err.println("⚠️ Storage emulator not ready, using mock bucket: " + e.getMessage());
            Bucket mockBucket = Mockito.mock(Bucket.class);
            Mockito.when(mockBucket.getName()).thenReturn(BUCKET_NAME);
            Mockito.when(mockBucket.exists()).thenReturn(true);
            return mockBucket;
        }
    }
}