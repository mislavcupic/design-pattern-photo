package hr.algebra.nrako.photoapp_backend.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import hr.algebra.nrako.photoapp_backend.domain.User;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

@Repository
public class UserRepository {

    private final Firestore db = FirestoreClient.getFirestore();

    public void save(User user) {
        try {
            db.collection("users").document(user.getFirebaseUid()).set(user);
        }
        catch (Exception e) {
           System.out.println("Error saving user: " + e);
        }


    }


    public CompletableFuture<Optional<User>> findByFirebaseUid(String firebaseUid) {
        ApiFuture<QuerySnapshot> future = db.collection("users")
                .whereEqualTo("firebaseUid", firebaseUid)
                .get();

        return CompletableFuture.supplyAsync(() -> {
            try {
                QuerySnapshot querySnapshot = future.get();  // Ovdje se čeka rezultat
                if (!querySnapshot.isEmpty()) {
                    DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                    User user = document.toObject(User.class);
                    return Optional.of(user);
                }
                return Optional.empty();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error finding user by firebaseUid", e);
            }
        });
    }


    public Optional<User> findByEmail(String email) throws ExecutionException, InterruptedException {
        CollectionReference usersCollection = db.collection("users");  // Pretpostavljamo da je kolekcija korisnika "users"

        // U Firestore, napravi upit za pronalazak korisnika prema emailu
        Query query = usersCollection.whereEqualTo("email", email);
        var querySnapshot = query.get().get(); // Pokreni upit, čekaj rezultat

        if (!querySnapshot.isEmpty()) {
            // Ako je korisnik pronađen, dohvatimo ga
            var userDoc = querySnapshot.getDocuments().get(0); // Uzimamo prvi rezultat (ako ih ima više)
            User user = userDoc.toObject(User.class);
            return Optional.of(user);
        }

        return Optional.empty(); // Ako korisnik nije pronađen
    }


    public void saveAndFlush(User user) {
    }


    public void saveUserPackageData(UserPackageData packageData) {
        db.collection("user_package_data")
                .document(packageData.getFirebaseUid())  // koristi UID kao ID dokumenta
                .set(packageData);
    }

    public void deleteUserData(String uid) {
        try {
            // Briši korisničke podatke iz Firestore-a
            db.collection("users").document(uid).delete();
//            // Briši paket podataka (ako postoji povezan u kolekciji)
//            db.collection("user_package_data").document(uid).delete();
//            //brišem korisničke iz photo
//            db.collection("photos").document(uid).delete();

            // Možemo dodati i druge kolekcije povezane s korisnikom, ako je potrebno
        } catch (Exception e) {
            throw new RuntimeException("Greška prilikom brisanja korisničkih podataka: " + e.getMessage());
        }
    }


}
