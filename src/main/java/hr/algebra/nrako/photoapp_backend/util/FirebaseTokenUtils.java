package hr.algebra.nrako.photoapp_backend.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class FirebaseTokenUtils {

    public String extractUidFromRequest(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof String) {
            return (String) authentication.getPrincipal();
        }
        return null; // Ili baci iznimku ako očekuješ da UID uvijek bude prisutan
    }

    public String extractUid(String idToken) {
        // Ova metoda ostaje ista jer se koristi za druge svrhe (npr., validacija tokena izvan SecurityContexta)
        try {
            if (idToken == null || idToken.isBlank()) {
                throw new IllegalArgumentException("ID token is null or blank");
            }
            System.out.println("Extracting UID from token: " + idToken);
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            System.out.println("Decoded UID: " + decodedToken.getUid());
            return decodedToken.getUid();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to verify ID token or extract UID", e);
        }
    }
}//package hr.algebra.nrako.photoapp_backend.util;
//
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseToken;
//import jakarta.servlet.http.HttpServletRequest;
//import org.springframework.stereotype.Component;
//
//
//@Component
//public class FirebaseTokenUtils {
//
//    public String extractUidFromRequest(HttpServletRequest request) {
//        try {
//            String authHeader = request.getHeader("Authorization");
//
//            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//                throw new RuntimeException("Missing or invalid Authorization header");
//            }
//
//            String idToken = authHeader.substring(7);
//            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
//            System.out.println("ExtraUidFromReq: "+decodedToken.getUid());
//            return decodedToken.getUid();
//
//        } catch (Exception e) {
//            throw new RuntimeException("Invalid Firebase token", e);
//        }
//    }
//    public String extractUid(String idToken) {
//        try {
//            if (idToken == null || idToken.isBlank()) {
//                throw new IllegalArgumentException("ID token is null or blank");
//            }
//
//            // Dodaj logiranje da provjeriš vrijednost tokena
//            System.out.println("Extracting UID from token: " + idToken);
//
//            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
//
//            // Dodaj logiranje nakon verifikacije tokena
//            System.out.println("Decoded UID: " + decodedToken.getUid());
//
//            return decodedToken.getUid();
//        } catch (Exception e) {
//            e.printStackTrace();  // Logiraj točno što je uzrokovalo grešku
//            throw new RuntimeException("Failed to verify ID token or extract UID", e);
//        }
//    }
//
//}
