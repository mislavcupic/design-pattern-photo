package hr.algebra.nrako.photoapp_backend.service;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

@Service
public class FirebaseService {

    public FirebaseToken verifyIdToken(String idToken) throws Exception {
        try {
            // Verificiraj token pomoću Firebase SDK-a
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            if (idToken == null || idToken.isEmpty()) {
                throw new IllegalArgumentException("ID token is empty or null");
            }
            return decodedToken; // Vrati dekodirani token
        } catch (Exception e) {
            throw new Exception("Failed to verify Firebase ID Token", e); // Ako verifikacija ne uspije
        }
    }

    // Metoda za obnavljanje ID tokena korištenjem refresh tokena

    // Metoda za obnavljanje ID tokena koristeći refresh token
    public String refreshIdToken(String refreshToken) throws Exception {
        try {
            // Firebase ne koristi refresh token direktno za obnavljanje ID tokena
            // Ali možemo koristiti FirebaseAuth za provjeru refresh tokena
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(refreshToken);

            // Možeš koristiti decodedToken za dobivanje novih podataka o korisniku
            String uid = decodedToken.getUid();  // Korisnički ID (UID)

            // Ako refresh token je valjan, Firebase automatski izgenerira novi ID token
            return FirebaseAuth.getInstance().createCustomToken(uid);

        } catch (Exception e) {
            throw new Exception("Failed to refresh ID token using refresh token", e);
        }
    }
}
