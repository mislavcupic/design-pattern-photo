package hr.algebra.nrako.photoapp_backend.service;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
import com.google.api.core.ApiFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
@Order(1)
public class StorageUserDeletionStrategy implements UserDeletionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(StorageUserDeletionStrategy.class);
    private final Firestore firestore; // Potreban za dohvat fileUrl-a iz 'photos' kolekcije

    // Regularni izraz za ekstrakciju putanje iz Firebase Storage URL-a
    private static final Pattern FIREBASE_STORAGE_URL_PATTERN =
            Pattern.compile("https://firebasestorage\\.googleapis\\.com/v0/b/[^/]+/o/([^\\?]+).*");

    public StorageUserDeletionStrategy(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void delete(String uid) throws Exception {
        logger.info("Starting Firebase Storage data deletion for user: {}", uid);

        Bucket bucket = null;
        try {
            bucket = StorageClient.getInstance().bucket();
        } catch (IllegalStateException e) {
            logger.error("Firebase StorageClient not initialized. Cannot delete storage files for UID {}. Error: {}", uid, e.getMessage());
            // Ovo je kritična greška za ovu strategiju, stoga bacamo iznimku
            throw new IllegalStateException("Firebase StorageClient not initialized. Cannot proceed with Storage deletion.", e);
        }

        // Dohvati SVE 'photos' dokumente za danog korisnika SAMO da bi se dobili fileUrl-ovi
        CollectionReference photosCollection = firestore.collection("photos");
        ApiFuture<QuerySnapshot> photosFuture = photosCollection.whereEqualTo("uploadedBy", uid).get();
        List<QueryDocumentSnapshot> photoDocuments = photosFuture.get().getDocuments();

        if (photoDocuments != null && !photoDocuments.isEmpty()) {
            logger.info("Found {} photo documents in Firestore to get Storage paths for user {}.", photoDocuments.size(), uid);
            for (QueryDocumentSnapshot document : photoDocuments) {
                String fileUrl = document.getString("fileUrl");
                if (fileUrl != null && !fileUrl.isEmpty()) {
                    String storagePath = extractStoragePathFromUrl(fileUrl);
                    if (storagePath != null) {
                        try {
                            BlobId blobId = BlobId.of(bucket.getName(), storagePath);
                            Blob blob = bucket.getStorage().get(blobId);
                            if (blob != null && blob.exists()) {
                                blob.delete();
                                logger.info("Deleted file from Storage: {}", storagePath);
                            } else {
                                logger.warn("File not found in Storage or already deleted: {}", storagePath);
                            }
                        } catch (Exception storageEx) {
                            logger.error("Error deleting file from Storage: {}. Error: {}", storagePath, storageEx.getMessage(), storageEx);
                            // Logiramo grešku, ali nastavljamo s ostalim datotekama kako bi se pokušale obrisati sve
                        }
                    } else {
                        logger.warn("Could not extract storage path from fileUrl: {}", fileUrl);
                    }
                } else {
                    logger.warn("File URL is null or empty for photo document ID {}. Skipping Storage deletion for this document.", document.getId());
                }
            }
        } else {
            logger.info("No photo documents found for user {} in Firestore, thus no files to delete from Storage.", uid);
        }
        logger.info("Firebase Storage data deletion completed for user: {}", uid);
    }

    // Helper metoda za ekstrakciju storage putanje iz URL-a
    private String extractStoragePathFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }
        Matcher matcher = FIREBASE_STORAGE_URL_PATTERN.matcher(fileUrl);
        if (matcher.matches() && matcher.groupCount() > 0) {
            try {
                return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8.name());
            } catch (java.io.UnsupportedEncodingException e) {
                logger.error("Error decoding URL for storage path: {}", fileUrl, e);
                return null;
            }
        }
        return null;
    }
}