package hr.algebra.nrako.photoapp_backend.filter;

import com.google.firebase.auth.FirebaseAuth; // DODANO
import com.google.firebase.auth.FirebaseAuthException; // DODANO
import com.google.firebase.auth.FirebaseToken; // DODANO
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // DODANO
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority; // DODANO
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List; // DODANO
import java.util.stream.Collectors; // DODANO

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth; // DODANO POLJE

    // DODAN KONSTRUKTOR ZA INJEKCIJU (Spring će ga pronaći)
    public FirebaseAuthenticationFilter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            logger.debug("Existing authenticated user found. Skipping Firebase token verification.");
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");
        logger.info("Authorization Header: " + authorizationHeader);

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            logger.debug("Anoniman zahtjev ili nedostaje 'Bearer' token.");
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = authorizationHeader.substring(7);

        try {
            // 1. VALIDACIJA TOKENA
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            String uid = decodedToken.getUid();

            // 2. DOHVAĆANJE ULOGA IZ CUSTOM CLAIMS-A
            // Koristi se "roles" claim, kako je postavljeno u UserServiceImpl.java
            @SuppressWarnings("unchecked")
            List<String> rolesFromClaims = (List<String>) decodedToken.getClaims().getOrDefault("roles", null);

            List<String> roles;
            if (rolesFromClaims != null && !rolesFromClaims.isEmpty()) {
                roles = rolesFromClaims;
            } else {
                // Fallback: Ako "roles" nije tu, koristi stari "userType" claim (ako postoji)
                String userType = (String) decodedToken.getClaims().getOrDefault("userType", "REGISTERED");
                roles = List.of(userType);
            }

            // 3. KREIRANJE SPRING SECURITY AUTHORITY OBJEKATA
            // Uloge u Spring Securityju MORAJU početi s "ROLE_" za ispravnu provjeru s hasAnyRole()
            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());

            // 4. KREIRANJE I POSTAVLJANJE AUTENTIFIKACIJSKOG OBJEKTA
            Authentication tokenAuthentication = new UsernamePasswordAuthenticationToken(
                    uid, // Principal: Korisnikov Firebase UID
                    idToken, // Credentials: ID Token
                    authorities // Authorities: Uloge (npr. ROLE_REGISTERED)
            );

            SecurityContextHolder.getContext().setAuthentication(tokenAuthentication);
            logger.info("✅ Korisnik UID {} autentificiran i postavljen u kontekst s ulogama: {}");

        } catch (FirebaseAuthException e) {
            // Token nije ispravan, istekao, nevažeći potpis, itd.
            logger.warn("❌ Neuspjela validacija Firebase tokena: {}");
            // Ostavljamo kontekst praznim. Spring Security AuthenticationEntryPoint će uhvatiti 401.
        } catch (Exception e) {
            logger.error("❌ Opća greška pri obradi tokena: {}", e);
        }

        // Nastavi na sljedeći filter (ili kontroler ako je ovo zadnji filter)
        filterChain.doFilter(request, response);
    }
}