package hr.algebra.nrako.photoapp_backend.service;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot; // Dodan import za QuerySnapshot
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
import com.google.api.core.ApiFuture;
import hr.algebra.nrako.photoapp_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLDecoder; // Dodan import za URLDecoder
import java.nio.charset.StandardCharsets; // Dodan import za StandardCharsets

@Component
@Order(2)
public class FirestoreUserDeletionStrategy implements UserDeletionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(FirestoreUserDeletionStrategy.class);
    private final Firestore firestore;
    private final UserRepository userRepository;

    // Regularni izraz za ekstrakciju putanje iz Firebase Storage URL-a
    private static final Pattern FIREBASE_STORAGE_URL_PATTERN =
            Pattern.compile("https://firebasestorage\\.googleapis\\.com/v0/b/[^/]+/o/([^\\?]+).*");

    public FirestoreUserDeletionStrategy(Firestore firestore, UserRepository userRepository) {
        this.firestore = firestore;
        this.userRepository = userRepository;
    }

    @Override
    public void delete(String uid) throws Exception {
        logger.info("Starting Firestore and Storage data deletion for user: {}", uid);

        Bucket bucket = StorageClient.getInstance().bucket();

        // 1. BRIŠI PODATKE IZ FIRESTORE 'photos' KOLEKCIJE I KORESPONDENTNE STORAGE DATOTEKE
        CollectionReference photosCollection = firestore.collection("photos");
        ApiFuture<QuerySnapshot> photosFuture = photosCollection.whereEqualTo("uploadedBy", uid).get(); // Ispravak ovdje
        List<QueryDocumentSnapshot> photoDocuments = photosFuture.get().getDocuments(); // I ovdje

        if (photoDocuments != null && !photoDocuments.isEmpty()) {
            logger.info("Found {} photo documents for user {}", photoDocuments.size(), uid);
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
                        }
                    } else {
                        logger.warn("Could not extract storage path from fileUrl: {}", fileUrl);
                    }
                } else {
                    logger.warn("File URL is null or empty for photo document ID: {}", document.getId());
                }
                document.getReference().delete().get();
                logger.info("Deleted document from Firestore 'photos' collection: {}", document.getId());
            }
        } else {
            logger.info("No photo documents found for user {}.", uid);
        }

        // 2. BRIŠI PODATKE IZ FIRESTORE 'user_package_data' KOLEKCIJE
        CollectionReference userPackageCollection = firestore.collection("user_package_data");
        ApiFuture<QuerySnapshot> userPackageFuture = userPackageCollection.whereEqualTo("firebaseUid", uid).get(); // Ispravak ovdje
        List<QueryDocumentSnapshot> userPackageDocuments = userPackageFuture.get().getDocuments(); // I ovdje

        if (userPackageDocuments != null && !userPackageDocuments.isEmpty()) {
            logger.info("Found {} user package documents for user {}", userPackageDocuments.size(), uid);
            for (QueryDocumentSnapshot document : userPackageDocuments) {
                document.getReference().delete().get();
                logger.info("Deleted document from Firestore 'user_package_data' collection: {}", document.getId());
            }
        } else {
            logger.info("No documents found in 'user_package_data' for user {} or already deleted.", uid);
        }

        // 3. BRIŠI GLAVNI KORISNIČKI DOKUMENT IZ 'users' KOLEKCIJE
        userRepository.deleteUserData(uid);
        logger.info("User data deleted from Firestore 'users' collection for UID: {}", uid);

        logger.info("Firestore and Storage data deletion completed for user: {}", uid);
    }

    private String extractStoragePathFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }
        Matcher matcher = FIREBASE_STORAGE_URL_PATTERN.matcher(fileUrl);
        if (matcher.matches() && matcher.groupCount() > 0) {
            try {
                // Koristi StandardCharsets.UTF_8 umjesto String za encoding
                return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8.name());
            } catch (java.io.UnsupportedEncodingException e) { // Iako je StandardCharsets.UTF_8 uvijek podržan, ipak je dobra praksa uhvatiti.
                logger.error("Error decoding URL for storage path: {}", fileUrl, e);
                return null;
            }
        }
        return null;
    }
}