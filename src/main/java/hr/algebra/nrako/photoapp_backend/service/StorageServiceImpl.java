package hr.algebra.nrako.photoapp_backend.service;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

@Service
public class StorageServiceImpl implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(StorageServiceImpl.class);

    // NE TREBAJU @Value anotacije ovdje ako će se Bucket i Storage injektirati
    // @Value("${firebase.bucket.name}")
    // private String bucketName;
    // @Value("${firebase.config.path}")
    // private String privateKeyPath;

    // Treba nam referenca na Bucket, koju će Spring injektirati
    private final Bucket storageBucket;
    // Ponekad je potrebna i direktna Storage instanca, ovisno o operacijama.
    // Ako ti treba samo Bucket, Storage ti možda niti ne treba kao polje klase.
    // Međutim, tvoj kod koristi 'storage.create', pa je bolje da i 'storage' bude injektiran.
    private final Storage googleCloudStorage; // Dodaj polje za Google Cloud Storage API

    // Konstruktor za Dependency Injection
    // Spring će automatski pronaći i injektirati beane tipa Bucket i Storage
    // Koji bean će se injektirati ovisi o aktivnom profilu (test vs produkcija)
//    public StorageServiceImpl(Bucket storageBucket, Storage googleCloudStorage) {
//        this.storageBucket = storageBucket;
//        this.googleCloudStorage = googleCloudStorage;
//        logger.info("StorageServiceImpl: Bucket i Google Cloud Storage klijent injektirani putem konstruktora.");
//        logger.info("Injected Bucket Name (if available from Bucket object): {}", storageBucket.getName());
//    }
    @Autowired // Spring će ovdje ubaciti @Primary Bean iz tvoje konfiguracije
    public StorageServiceImpl(Storage googleCloudStorage, Bucket storageBucket) {
        this.googleCloudStorage = googleCloudStorage;
        this.storageBucket = storageBucket;

        // DEBUG: Provjeri je li bucket stvarno stigao
        if (this.storageBucket == null) {
            System.out.println("DEBUG ERROR: storageBucket je NULL u servisu!");
        } else {
            System.out.println("DEBUG SUCCESS: Injektiran bucket: " + storageBucket.getName());
        }
    }

    // Ukloni @PostConstruct metodu jer se inicijalizacija sada radi preko DI
    // @PostConstruct
    // public void init() { ... }

    // @PreDestroy također neće trebati ako Spring automatski upravlja lifecycleom beanova
    // ali možeš ga ostaviti ako imaš specifičnu logiku čišćenja koja nije automatska.
    // U slučaju da su Bucket i Storage dobiveni putem DI, Spring će ih sam zatvoriti.
    @PreDestroy
    public void destroy() {
        // Logika zatvaranja je sada uglavnom upravljana od strane Springa i SDK-a.
        // Nema potrebe za ručnim zatvaranjem 'this.storage' jer ga je Spring injektirao.
        // Također, FirebaseApp se upravlja u FirebaseTestConfig (za test) ili u glavnoj konf (za prod).
        logger.info("StorageServiceImpl: PreDestroy called. Resources managed by Spring context.");
    }



    @Override
    public String uploadPhoto(MultipartFile file, String filename) {
        try {
            // Stara metoda samo pretvori file u bajtove i zove novu metodu
            return uploadPhoto(file.getBytes(), filename, file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("Greška pri čitanju datoteke", e);
        }
    }

    @Override
    public String uploadPhoto(byte[] content, String filename, String contentType) {
        try {
            String bucketName = storageBucket.getName();

            // Emulator zahtijeva validan Content-Type
            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = "image/jpeg";
            }

            BlobId blobId = BlobId.of(bucketName, filename);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .build();

            // ✅ RJEŠENJE: Koristi WriteChannel API umjesto create()
            // Firebase Storage emulator ima bug sa storage.create() ali RADI sa writer()
            try (WriteChannel writer = googleCloudStorage.writer(blobInfo)) {
                writer.write(ByteBuffer.wrap(content));
            }

            logger.info("✅ File uploaded successfully: {}", filename);
            return filename;

        } catch (Exception e) {
            logger.error("❌ GCS Upload Error: {}", e.getMessage());
            throw new RuntimeException("GCS Upload failed", e);
        }
    }

    @Override
    public void deletePhoto(String filename) {
        BlobId blobId = BlobId.of(storageBucket.getName(), filename);
        boolean deleted = googleCloudStorage.delete(blobId); // Koristi injektirani googleCloudStorage
        if (deleted) {
            logger.info("Deleted {} from GCS.", filename);
        } else {
            logger.warn("Failed to delete {} from GCS or file not found.", filename);
        }
    }

    @Override
    public InputStream downloadPhotoAsStream(String filename) {
        Blob blob = googleCloudStorage.get(BlobId.of(storageBucket.getName(), filename)); // Koristi injektirani googleCloudStorage
        if (blob == null) {
            logger.warn("Attempted to download stream for non-existent photo: {}", filename);
            return null;
        }
        return Channels.newInputStream(blob.reader());
    }

    @Override
    public byte[] downloadPhotoAsBytes(String filename) {
        try {
            BlobId blobId = BlobId.of(storageBucket.getName(), filename);
            Blob blob = googleCloudStorage.get(blobId); // Koristi injektirani googleCloudStorage
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
//package hr.algebra.nrako.photoapp_backend.service;
//
//import com.google.cloud.storage.*;
//import com.google.auth.oauth2.GoogleCredentials;
//import com.google.firebase.FirebaseApp;
//import com.google.firebase.FirebaseOptions;
//import hr.algebra.nrako.photoapp_backend.service.StorageService;
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.io.ClassPathResource; // <-- DODAJ OVAJ IMPORT
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//import java.io.IOException;
//import java.io.InputStream; // Koristit ćemo InputStream umjesto FileInputStream direktno
//import java.nio.channels.Channels;
//import java.util.concurrent.TimeUnit;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//@Service
//public class StorageServiceImpl implements StorageService {
//
//    @Value("${firebase.bucket.name}")
//    private String bucketName;
//
//    // <-- Ostavljamo ovaj naziv, ali ga moraš uskladiti u application.properties
//    @Value("${firebase.config.path}")
//    private String privateKeyPath; // Očekuje "classpath:serviceAccount.json"
//
//    private Storage storage;
//    private static final Logger logger = LoggerFactory.getLogger(StorageServiceImpl.class);
//
//    @PostConstruct
//    public void init() {
//        try {
//            // Učitaj service account datoteku iz classpatha koristeći Springov ClassPathResource
//            // Varijabla privateKeyPath sada sadrži "classpath:serviceAccount.json"
//            // Uklanjamo "classpath:" prefiks jer ClassPathResource to rješava interno
//            String resourceName = privateKeyPath.startsWith("classpath:") ?
//                    privateKeyPath.substring("classpath:".length()) : privateKeyPath;
//
//            ClassPathResource classPathResource = new ClassPathResource(resourceName);
//            InputStream serviceAccountStream = classPathResource.getInputStream(); // Koristi InputStream
//
//            FirebaseOptions options = FirebaseOptions.builder()
//                    .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
//                    .setStorageBucket(bucketName)
//                    .build();
//
//            if (FirebaseApp.getApps().isEmpty()) {
//                FirebaseApp.initializeApp(options);
//                logger.info("Firebase app initialized successfully.");
//            } else {
//                logger.info("Firebase app already initialized.");
//            }
//
//            // VAŽNO: Ponovno otvori stream ili kreiraj novi jer je prvi već konzumiran od FirebaseOptions.
//            // Sigurnije je uvijek dobiti novi stream za svaku upotrebu.
//            InputStream storageCredentialsStream = new ClassPathResource(resourceName).getInputStream();
//            this.storage = StorageOptions.newBuilder()
//                    .setCredentials(GoogleCredentials.fromStream(storageCredentialsStream))
//                    .build()
//                    .getService();
//
//            logger.info("Google Cloud Storage client initialized successfully for bucket: {}", bucketName);
//
//        } catch (IOException e) {
//            logger.error("Error initializing Firebase or Google Cloud Storage: {}", e.getMessage(), e);
//            throw new RuntimeException("Failed to initialize Firebase or Google Cloud Storage", e);
//        }
//    }
//
//    @PreDestroy
//    public void destroy() {
//        if (this.storage != null) {
//            try {
//                this.storage.close();
//                logger.info("Google Cloud Storage client closed successfully.");
//            } catch (Exception e) {
//                logger.warn("Error closing Google Cloud Storage client: {}", e.getMessage(), e);
//            }
//        }
//
//        if (!FirebaseApp.getApps().isEmpty()) {
//            try {
//                FirebaseApp.getInstance().delete();
//                logger.info("FirebaseApp successfully deleted during shutdown.");
//            } catch (IllegalStateException e) {
//                logger.warn("FirebaseApp already deleted or in an invalid state during shutdown: {}", e.getMessage());
//            } catch (Exception e) {
//                logger.error("Error during FirebaseApp shutdown: {}", e.getMessage(), e);
//            }
//        }
//    }
//
//    @Override
//    public String uploadPhoto(MultipartFile file, String filename) {
//        try {
//            BlobId blobId = BlobId.of(bucketName, filename);
//            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();
//            storage.create(blobInfo, file.getBytes());
//
//            logger.info("Uploaded {} to GCS.", filename);
//            return filename;
//        } catch (IOException e) {
//            logger.error("Error uploading file {}: {}", filename, e.getMessage(), e);
//            throw new RuntimeException("Failed to upload file to Google Cloud Storage", e);
//        }
//    }
//
//    @Override
//    public void deletePhoto(String filename) {
//        BlobId blobId = BlobId.of(bucketName, filename);
//        boolean deleted = storage.delete(blobId);
//        if (deleted) {
//            logger.info("Deleted {} from GCS.", filename);
//        } else {
//            logger.warn("Failed to delete {} from GCS or file not found.", filename);
//        }
//    }
//
//    @Override
//    public InputStream downloadPhotoAsStream(String filename) {
//        Blob blob = storage.get(BlobId.of(bucketName, filename));
//        if (blob == null) {
//            logger.warn("Attempted to download stream for non-existent photo: {}", filename);
//            return null;
//        }
//        return Channels.newInputStream(blob.reader());
//    }
//
//    @Override
//    public byte[] downloadPhotoAsBytes(String filename) {
//        try {
//            BlobId blobId = BlobId.of(bucketName, filename);
//            Blob blob = storage.get(blobId);
//            if (blob == null) {
//                logger.warn("Attempted to download bytes for non-existent photo: {}", filename);
//                throw new RuntimeException("Photo not found: " + filename);
//            }
//            logger.info("Downloaded bytes for photo: {}", filename);
//            return blob.getContent();
//        } catch (StorageException e) {
//            logger.error("Storage error downloading bytes for {}: {}", filename, e.getMessage(), e);
//            throw new RuntimeException("Storage error downloading photo: " + filename, e);
//        } catch (Exception e) {
//            logger.error("Unexpected error downloading bytes for {}: {}", filename, e.getMessage(), e);
//            throw new RuntimeException("Unexpected error downloading photo: " + filename, e);
//        }
//    }
//}