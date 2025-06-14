package hr.algebra.nrako.photoapp_backend.service;

import com.google.cloud.storage.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import hr.algebra.nrako.photoapp_backend.service.StorageService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource; // <-- DODAJ OVAJ IMPORT
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream; // Koristit ćemo InputStream umjesto FileInputStream direktno
import java.nio.channels.Channels;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class StorageServiceImpl implements StorageService {

    @Value("${firebase.bucket.name}")
    private String bucketName;

    // <-- Ostavljamo ovaj naziv, ali ga moraš uskladiti u application.properties
    @Value("${firebase.config.path}")
    private String privateKeyPath; // Očekuje "classpath:serviceAccount.json"

    private Storage storage;
    private static final Logger logger = LoggerFactory.getLogger(StorageServiceImpl.class);

    @PostConstruct
    public void init() {
        try {
            // Učitaj service account datoteku iz classpatha koristeći Springov ClassPathResource
            // Varijabla privateKeyPath sada sadrži "classpath:serviceAccount.json"
            // Uklanjamo "classpath:" prefiks jer ClassPathResource to rješava interno
            String resourceName = privateKeyPath.startsWith("classpath:") ?
                    privateKeyPath.substring("classpath:".length()) : privateKeyPath;

            ClassPathResource classPathResource = new ClassPathResource(resourceName);
            InputStream serviceAccountStream = classPathResource.getInputStream(); // Koristi InputStream

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                    .setStorageBucket(bucketName)
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                logger.info("Firebase app initialized successfully.");
            } else {
                logger.info("Firebase app already initialized.");
            }

            // VAŽNO: Ponovno otvori stream ili kreiraj novi jer je prvi već konzumiran od FirebaseOptions.
            // Sigurnije je uvijek dobiti novi stream za svaku upotrebu.
            InputStream storageCredentialsStream = new ClassPathResource(resourceName).getInputStream();
            this.storage = StorageOptions.newBuilder()
                    .setCredentials(GoogleCredentials.fromStream(storageCredentialsStream))
                    .build()
                    .getService();

            logger.info("Google Cloud Storage client initialized successfully for bucket: {}", bucketName);

        } catch (IOException e) {
            logger.error("Error initializing Firebase or Google Cloud Storage: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Firebase or Google Cloud Storage", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (this.storage != null) {
            try {
                this.storage.close();
                logger.info("Google Cloud Storage client closed successfully.");
            } catch (Exception e) {
                logger.warn("Error closing Google Cloud Storage client: {}", e.getMessage(), e);
            }
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseApp.getInstance().delete();
                logger.info("FirebaseApp successfully deleted during shutdown.");
            } catch (IllegalStateException e) {
                logger.warn("FirebaseApp already deleted or in an invalid state during shutdown: {}", e.getMessage());
            } catch (Exception e) {
                logger.error("Error during FirebaseApp shutdown: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public String uploadPhoto(MultipartFile file, String filename) {
        try {
            BlobId blobId = BlobId.of(bucketName, filename);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();
            storage.create(blobInfo, file.getBytes());

            logger.info("Uploaded {} to GCS.", filename);
            return filename;
        } catch (IOException e) {
            logger.error("Error uploading file {}: {}", filename, e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to Google Cloud Storage", e);
        }
    }

    @Override
    public void deletePhoto(String filename) {
        BlobId blobId = BlobId.of(bucketName, filename);
        boolean deleted = storage.delete(blobId);
        if (deleted) {
            logger.info("Deleted {} from GCS.", filename);
        } else {
            logger.warn("Failed to delete {} from GCS or file not found.", filename);
        }
    }

    @Override
    public InputStream downloadPhotoAsStream(String filename) {
        Blob blob = storage.get(BlobId.of(bucketName, filename));
        if (blob == null) {
            logger.warn("Attempted to download stream for non-existent photo: {}", filename);
            return null;
        }
        return Channels.newInputStream(blob.reader());
    }

    @Override
    public byte[] downloadPhotoAsBytes(String filename) {
        try {
            BlobId blobId = BlobId.of(bucketName, filename);
            Blob blob = storage.get(blobId);
            if (blob == null) {
                logger.warn("Attempted to download bytes for non-existent photo: {}", filename);
                throw new RuntimeException("Photo not found: " + filename);
            }
            logger.info("Downloaded bytes for photo: {}", filename);
            return blob.getContent();
        } catch (StorageException e) {
            logger.error("Storage error downloading bytes for {}: {}", filename, e.getMessage(), e);
            throw new RuntimeException("Storage error downloading photo: " + filename, e);
        } catch (Exception e) {
            logger.error("Unexpected error downloading bytes for {}: {}", filename, e.getMessage(), e);
            throw new RuntimeException("Unexpected error downloading photo: " + filename, e);
        }
    }
}