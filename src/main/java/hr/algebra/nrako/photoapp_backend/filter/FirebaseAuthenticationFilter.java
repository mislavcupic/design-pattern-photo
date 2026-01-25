package hr.algebra.nrako.photoapp_backend.filter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthenticationFilter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. CORS Fix: Pusti OPTIONS zahtjeve bez provjere tokena
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        // DEBUG: Da vidimo pogađa li zahtjev uopće filter
        System.out.println("--- FILTER START: " + request.getRequestURI() + " ---");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = header.substring(7);

        try {
            // 2. Verifikacija tokena preko Firebasea
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();

            System.out.println("DEBUG: Token prepoznat za: " + email);

            // 3. Dohvaćanje uloga
            Map<String, Object> claims = decodedToken.getClaims();
            List<String> rawRoles = new ArrayList<>();

            // Čitamo "roles" (lista) ili "userType" (string) iz tokena
            if (claims.get("roles") instanceof List) {
                rawRoles = (List<String>) claims.get("roles");
            } else if (claims.get("userType") != null) {
                rawRoles.add(claims.get("userType").toString());
            }

            // HITNI FIX: Ako je email admin@admin.hr, FORCE-aj ADMIN ulogu
            if ("admin@admin.hr".equals(email) && !rawRoles.contains("ADMIN")) {
                rawRoles.add("ADMIN");
                System.out.println("DEBUG: Force-ana ADMIN uloga za email: " + email);
            }

            // 4. Mapiranje u ROLE_ prefiks za Spring Security
            List<SimpleGrantedAuthority> authorities = rawRoles.stream()
                    .map(role -> {
                        String r = role.toUpperCase();
                        if (!r.startsWith("ROLE_")) r = "ROLE_" + r;
                        return new SimpleGrantedAuthority(r);
                    })
                    .collect(Collectors.toList());

            System.out.println("DEBUG: Authorities postavljeni: " + authorities);

            // 5. Postavljanje u SecurityContext
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(uid, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            System.out.println("✅ USPIJEH: Korisnik " + email + " je sada autentificiran.");

        } catch (FirebaseAuthException e) {
            System.out.println("❌ GREŠKA: Firebase token nije valjan: " + e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            System.out.println("❌ GREŠKA u filteru: " + e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}