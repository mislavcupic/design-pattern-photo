package hr.algebra.nrako.photoapp_backend.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import hr.algebra.nrako.photoapp_backend.domain.User;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Repository
public class UserRepository {

    private final Firestore db; // Deklaracija Firestore instance

    /**
     * Konstruktor za injektiranje Firestore instance putem Springa.
     * Spring će automatski osigurati pravilno konfiguriranu Firestore instancu (npr. s emulator postavkama)
     * definiranu kao Bean u FirebaseTestConfig.
     * @param firestore Instanca Firestore klijenta koju injektira Spring.
     */
    public UserRepository(Firestore firestore) {
        this.db = firestore; // Inicijalizacija 'db' kroz injektirani parametar
    }

    /**
     * Sprema korisnika u Firestore kolekciju "users".
     * Koristi Firebase UID korisnika kao ID dokumenta.
     * @param user Objekt korisnika za spremanje.
     */
    public void save(User user) {
        try {
            db.collection("users").document(user.getFirebaseUid()).set(user);
        } catch (Exception e) {
            System.out.println("Error saving user: " + e);
            // Ovdje bi se trebala koristiti prava loggin instanca (npr. Slf4j Logger)
            // i baciti konkretnija iznimka ili je handlati na prikladan način.
        }
    }

    /**
     * Asinkrono pronalazi korisnika po njegovom Firebase UID-u.
     * @param firebaseUid Jedinstveni Firebase UID korisnika.
     * @return CompletableFuture koji sadrži Optional s pronađenim korisnikom, ili prazan Optional ako nije pronađen.
     */
    public CompletableFuture<Optional<User>> findByFirebaseUid(String firebaseUid) {
        ApiFuture<QuerySnapshot> future = db.collection("users")
                .whereEqualTo("firebaseUid", firebaseUid)
                .get();

        return CompletableFuture.supplyAsync(() -> {
            try {
                QuerySnapshot querySnapshot = future.get();
                if (!querySnapshot.isEmpty()) {
                    DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                    User user = document.toObject(User.class);
                    return Optional.of(user);
                }
                return Optional.empty();
            } catch (InterruptedException | ExecutionException e) {
                // Bacanje RuntimeException za asinkrone operacije koje se prekidaju
                throw new RuntimeException("Error finding user by firebaseUid", e);
            }
        });
    }

    /**
     * Pronalazi korisnika po njegovoj email adresi.
     * @param email Email adresa korisnika.
     * @return Optional s pronađenim korisnikom, ili prazan Optional ako nije pronađen.
     * @throws ExecutionException Ako dođe do pogreške tijekom izvršavanja asinkronog upita.
     * @throws InterruptedException Ako je trenutna nit prekinuta dok čeka na rezultat.
     */
    public Optional<User> findByEmail(String email) throws ExecutionException, InterruptedException {
        CollectionReference usersCollection = db.collection("users");
        Query query = usersCollection.whereEqualTo("email", email);
        QuerySnapshot querySnapshot = query.get().get(); // Čekanje na rezultat

        if (!querySnapshot.isEmpty()) {
            DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
            User user = userDoc.toObject(User.class);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * Ova metoda je obično prisutna u JPA repozitorijima. Za Firestore, 'save' metoda
     * obično ima ekvivalentnu funkcionalnost "spremi i odmah primijeni".
     * Trenutno je prazna, ali se može implementirati ako postoji specifična potreba za Firestoreom.
     * @param user Objekt korisnika za spremanje.
     */
    public void saveAndFlush(User user) {
        // Implementiraj logiku ako je potrebna za Firestore, npr. izravno pozvati save()
        save(user); // Primjer, ako je saveAndFlush sinonim za save u ovom kontekstu
    }

    /**
     * Sprema paket podataka korisnika u kolekciju "user_package_data".
     * Koristi Firebase UID korisnika kao ID dokumenta.
     * @param packageData Objekt UserPackageData za spremanje.
     */
    public void saveUserPackageData(UserPackageData packageData) {
        db.collection("user_package_data")
                .document(packageData.getFirebaseUid())
                .set(packageData);
    }

    /**
     * Briše korisničke podatke iz kolekcije "users" na temelju Firebase UID-a.
     * @param uid Firebase UID korisnika čiji podaci se brišu.
     */
    public void deleteUserData(String uid) {
        try {
            db.collection("users").document(uid).delete();
            // Ovdje možeš dodati logiku za brisanje povezanih podataka iz drugih kolekcija
            // npr. db.collection("user_package_data").document(uid).delete();
            // npr. db.collection("photos").whereEqualTo("userId", uid).get().get().getDocuments().forEach(doc -> doc.getReference().delete());
        } catch (Exception e) {
            throw new RuntimeException("Greška prilikom brisanja korisničkih podataka: " + e.getMessage(), e);
            // Uvijek proslijedi originalni izuzetak u RuntimeException
        }
    }
}