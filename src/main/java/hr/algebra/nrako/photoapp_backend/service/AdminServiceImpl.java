package hr.algebra.nrako.photoapp_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import hr.algebra.nrako.photoapp_backend.domain.User;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.exceptions.FailedToGetUsersException;
import jakarta.persistence.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
public class AdminServiceImpl implements AdminService {

    private final Firestore firestore;

    public AdminServiceImpl(Firestore firestore) {
        this.firestore = firestore;
    }

    //funkcionalno
    @Override
    public CompletableFuture<List<User>> getAllUsers() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                QuerySnapshot documents = firestore.collection("users").get().get();

                // FUNKCIONALNI STIL:
                return documents.getDocuments().stream()      // 1. Primjer: Stream API
                        .map(doc -> doc.toObject(User.class)) // 2. Primjer: Mapiranje (Method reference)
                        .filter(Objects::nonNull)             // 3. Primjer: Filtriranje
                        .toList();                            // 4. Primjer: Terminalna operacija

            } catch (InterruptedException e) {
                // 1. Obnovi interrupt status niti
                Thread.currentThread().interrupt();

                // 2. Baci iznimku dalje (ili je zamotaj u RuntimeException ako potpis metode ne dopušta checked exception)
                throw new FailedToGetUsersException("Thread was interrupted", e);
            } catch (ExecutionException e) {
                throw new FailedToGetUsersException(e.getCause().getMessage(), e.getCause());
            }
        });
    }


    @Override
    public CompletableFuture<User> getUserByUid(String uid) {
        return CompletableFuture.supplyAsync(() -> {
            ApiFuture<DocumentSnapshot> future = firestore.collection("users").document(uid).get();
            try {
                DocumentSnapshot document = future.get();
                return document.exists() ? document.toObject(User.class) : null;
            } catch (InterruptedException e) {
                // 1. Obnovi interrupt status niti
                Thread.currentThread().interrupt();

                // 2. Baci iznimku dalje (ili je zamotaj u RuntimeException ako potpis metode ne dopušta checked exception)
                throw new FailedToGetUsersException("Thread was interrupted", e);
            } catch (ExecutionException e) {
                throw new FailedToGetUsersException(e.getCause().getMessage(), e.getCause());
            }
        });
    }

    @Override
    public CompletableFuture<List<UserPackageData>> getAllUserPackages() {
        return CompletableFuture.supplyAsync(() -> {
            List<UserPackageData> userPackages = new ArrayList<>();
            CollectionReference userPackagesCollection = firestore.collection("user-package-data");
            ApiFuture<QuerySnapshot> future = userPackagesCollection.get();
            try {
                QuerySnapshot documents = future.get();
                for (DocumentSnapshot document : documents) {
                    UserPackageData userPackage = document.toObject(UserPackageData.class);
                    userPackages.add(userPackage);
                }
                return userPackages;
            } catch (InterruptedException | ExecutionException e) {
                // 1. Obnovi interrupt status niti
                Thread.currentThread().interrupt();

                // 2. Baci iznimku dalje (ili je zamotaj u RuntimeException ako potpis metode ne dopušta checked exception)
                throw new FailedToGetUsersException("Thread was interrupted", e);
            }
        });
    }

    @Override
    public CompletableFuture<UserPackageData> getUserPackageByUid(String uid) {
        return CompletableFuture.supplyAsync(() -> {
            ApiFuture<DocumentSnapshot> future = firestore.collection("user-package-data").document(uid).get();
            try {
                DocumentSnapshot document = future.get();
                return document.exists() ? document.toObject(UserPackageData.class) : null;
            } catch (InterruptedException | ExecutionException e) {
                // 1. Obnovi interrupt status niti
                Thread.currentThread().interrupt();

                // 2. Baci iznimku dalje (ili je zamotaj u RuntimeException ako potpis metode ne dopušta checked exception)
                throw new FailedToGetUsersException("Thread was interrupted", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Object>> updateUserRole(String uid, String newRole) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Dohvati Firestore instancu i referencu na korisnika
                // Napomena: Firestore objekt mora biti injektiran u konstruktoru servisa
                firestore.collection("users").document(uid)
                        .update("userType", newRole.toUpperCase())
                        .get(); // Čekamo da Firestore završi

                System.out.println("✅ Uloga ažurirana u Firestore za UID: " + uid);

                // 2. VAŽNO: Ažuriraj Firebase Custom Claims
                // Bez ovoga će korisnik morati čekati sat vremena da mu se token osvježi
                Map<String, Object> claims = new HashMap<>();
                claims.put("roles", Collections.singletonList(newRole.toUpperCase()));
                FirebaseAuth.getInstance().setCustomUserClaims(uid, claims);

                return Collections.singletonList("Uspješno ažurirana uloga");
            } catch (Exception e) {
                System.err.println("❌ Greška u updateUserRole: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}

