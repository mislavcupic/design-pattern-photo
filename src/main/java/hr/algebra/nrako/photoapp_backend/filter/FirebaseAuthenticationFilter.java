package hr.algebra.nrako.photoapp_backend.filter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);
    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthenticationFilter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        logger.info("Authorization Header: {}", authorizationHeader);

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String idToken = authorizationHeader.substring(7);
            try {
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
                final String uid = decodedToken.getUid(); // Označeno kao final
                logger.info("Token uspješno verificiran. UID: {}", uid);
                logger.info("Claims iz tokena: {}", decodedToken.getClaims());

                Object userTypeClaim = decodedToken.getClaims().get("userType");
                List<SimpleGrantedAuthority> authorities = null;

                if (userTypeClaim instanceof String) {
                    String role = (String) userTypeClaim;
                    authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
                } else if (userTypeClaim instanceof List) {
                    List<String> roles = (List<String>) userTypeClaim;
                    authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toList());
                }

                logger.info("Vrijednost uid neposredno prije provjere: {}", uid);
                if (uid != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(uid, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.info("Autentikacija uspješno postavljena za korisnika: {}, s ulogama: {}", uid, authorities);
                } else {
                    logger.warn("UID je null.");
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception e) {
                logger.error("Greška prilikom verifikacije tokena: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else {
            logger.warn("Authorization header nije prisutan ili ne počinje s 'Bearer '.");
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/auth/login") || path.equals("/auth/register");
    }
}

/*package hr.algebra.nrako.photoapp_backend.filter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class FirebaseAuthenticationFilter extends OncePerRequestFilter {



    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);

                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        decodedToken.getUid(), null, null);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // Token invalid or missing, no authentication set
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
*/