package hr.algebra.nrako.photoapp_backend.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Repository
public class UserPackageDataRepository {

    private final Firestore db = FirestoreClient.getFirestore();

    public CompletableFuture<Optional<UserPackageData>> findByFirebaseUid(String firebaseUid) {
        ApiFuture<DocumentSnapshot> future = db.collection("user_package_data").document(firebaseUid).get();
        return CompletableFuture.supplyAsync(() -> {
            try {
                DocumentSnapshot document = future.get();
                if (document.exists()) {
                    // Firestore automatski deserializira podatke u UserPackageData
                    UserPackageData data = document.toObject(UserPackageData.class);
                    return Optional.ofNullable(data);
                } else {
                    return Optional.empty();
                }
            } catch (Exception e) {
                e.printStackTrace(); // Ili zamijeni s logiranjem
                return Optional.empty();
            }
        });
    }

    public CompletableFuture<Void> save(UserPackageData data) {
        db.collection("user_package_data").document(data.getFirebaseUid()).set(data);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<UserPackageData> getUserPackageData(String uid) {
        ApiFuture<DocumentSnapshot> future = db.collection("user_package_data").document(uid).get();
        return CompletableFuture.supplyAsync(() -> {
            try {
                DocumentSnapshot doc = future.get();
                if (doc.exists()) {
                    return doc.toObject(UserPackageData.class);
                } else {
                    return null;
                }
            } catch (Exception e) {
                throw new RuntimeException("Error fetching data", e);
            }
        });
    }
}
