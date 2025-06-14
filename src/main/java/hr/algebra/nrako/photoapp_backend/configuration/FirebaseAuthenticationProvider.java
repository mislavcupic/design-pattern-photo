package hr.algebra.nrako.photoapp_backend.configuration;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class FirebaseAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // Dobijemo ID token iz authentication objekta
        String idToken = (String) authentication.getCredentials();

        try {
            // Verificiramo token sa Firebaseom
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String uid = decodedToken.getUid();  // UID korisnika

            // Kreiramo novi Authentication token za korisnika sa validnim UID-om
            return new FirebaseAuthenticationToken(uid, idToken, null);  // Ovo je trenutna verzija bez uloga
        } catch (Exception e) {
            throw new AuthenticationException("Firebase token is invalid or expired") {};
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        // Provjerava da li je authentication tipa FirebaseAuthenticationToken
        return FirebaseAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
