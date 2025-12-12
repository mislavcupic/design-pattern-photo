package hr.algebra.nrako.photoapp_backend.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
// Uklanjamo ovaj import jer više nećemo koristiti FirestoreClient.getFirestore()
// import com.google.firebase.cloud.FirestoreClient;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Repository
public class UserPackageDataRepository {

    // 1. Promjena: Firestore instanca se više ne inicijalizira direktno ovdje.
    // Spring će je injektirati putem konstruktora.
    private final Firestore db;
    public static final String COLLECTION_NAME = "user_package_data";

    /**
     * 2. Promjena: Dodan konstruktor za injektiranje Firestore instance.
     * Spring će automatski osigurati pravilno konfiguriranu Firestore instancu (npr. s emulator postavkama)
     * definiranu kao Bean u FirebaseTestConfig.
     * @param firestore Instanca Firestore klijenta koju injektira Spring.
     */
    public UserPackageDataRepository(Firestore firestore) {
        this.db = firestore; // Inicijalizacija 'db' kroz injektirani parametar
    }

    /**
     * Pronalazi UserPackageData za dani Firebase UID.
     * Vraća CompletableFuture<Optional<UserPackageData>>.
     */
    public CompletableFuture<Optional<UserPackageData>> findByFirebaseUid(String firebaseUid) {
        ApiFuture<DocumentSnapshot> future = db.collection(COLLECTION_NAME).document(firebaseUid).get();
        return CompletableFuture.supplyAsync(() -> {
            try {
                DocumentSnapshot document = future.get();
                if (document.exists()) {
                    UserPackageData data = document.toObject(UserPackageData.class);
                    return Optional.ofNullable(data); // Optional.ofNullable handles null 'data' case
                } else {
                    return Optional.empty();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Prekini nit ako je prekinuta
                throw new RuntimeException("Firestore operation interrupted", e);
            } catch (ExecutionException e) {
                // Uhvati stvarni uzrok iz ExecutionException
                throw new RuntimeException("Error fetching user package data for UID: " + firebaseUid, e.getCause());
            } catch (Exception e) {
                // Općenita greška (npr. deserializacija)
                System.err.println("Unexpected error in findByFirebaseUid for UID " + firebaseUid + ": " + e.getMessage());
                e.printStackTrace();
                // Bolje je baciti RuntimeException ovdje ako je greška kritična za daljnje izvršavanje
                throw new RuntimeException("Failed to fetch user package data due to unexpected error.", e);
            }
        });
    }

    /**
     * Sprema ili ažurira UserPackageData objekt u Firestoreu.
     * Vraća CompletableFuture<UserPackageData> kako bi se mogao koristiti u lancu poziva
     * i potvrdilo da je objekt uspješno spremljen.
     */
    public CompletableFuture<UserPackageData> save(UserPackageData data) {
        if (data.getFirebaseUid() == null || data.getFirebaseUid().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Firebase UID must not be null or empty for saving UserPackageData."));
        }

        // Postavi ID dokumenta u Firestoreu da bude Firebase UID
        ApiFuture<WriteResult> future = db.collection(COLLECTION_NAME).document(data.getFirebaseUid()).set(data);

        return CompletableFuture.supplyAsync(() -> {
            try {
                future.get(); // Čekaj da se operacija spremanja završi
                return data; // Vrati objekt koji je upravo spremljen
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Firestore save operation interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Error saving user package data for UID: " + data.getFirebaseUid(), e.getCause());
            } catch (Exception e) {
                System.err.println("Unexpected error in save for UID " + data.getFirebaseUid() + ": " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to save user package data.", e);
            }
        });
    }
}