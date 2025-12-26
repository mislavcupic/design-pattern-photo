package hr.algebra.nrako.photoapp_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import hr.algebra.nrako.photoapp_backend.domain.User;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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

        } catch (Exception e) {
            throw new RuntimeException("Greška", e);
        }
    });
}
//inicijalno postavljena metoda, bez functional programming priče
//    @Override
//    public CompletableFuture<List<User>> getAllUsers() {
//        return CompletableFuture.supplyAsync(() -> {
//            List<User> users = new ArrayList<>();
//            CollectionReference usersCollection = firestore.collection("users");
//            ApiFuture<QuerySnapshot> future = usersCollection.get();
//            try {
//                QuerySnapshot documents = future.get();
//                for (DocumentSnapshot document : documents) {
//                    User user = document.toObject(User.class);
//                    users.add(user);
//                }
//                return users;
//            } catch (Exception e) {
//                e.printStackTrace();
//                return new ArrayList<>();
//            }
//        });
//    }

    @Override
    public CompletableFuture<User> getUserByUid(String uid) {
        return CompletableFuture.supplyAsync(() -> {
            ApiFuture<DocumentSnapshot> future = firestore.collection("users").document(uid).get();
            try {
                DocumentSnapshot document = future.get();
                return document.exists() ? document.toObject(User.class) : null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
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
            } catch (Exception e) {
                e.printStackTrace();
                return new ArrayList<>();
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
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }
}
