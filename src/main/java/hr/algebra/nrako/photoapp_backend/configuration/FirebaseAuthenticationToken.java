package hr.algebra.nrako.photoapp_backend.configuration;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private String credentials;

    // Konstruktor za neautentificirane korisnike, s Firebase ID tokenom kao kredencijalima
    public FirebaseAuthenticationToken(String credentials) {
        super(null);
        this.principal = null;
        this.credentials = credentials;
        setAuthenticated(false);
    }

    // Konstruktor za autentificirane korisnike, s UID-om kao principal i Firebase ID tokenom kao kredencijalima
    public FirebaseAuthenticationToken(Object principal, String credentials, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return this.credentials;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    // Erase credentials after authentication for security reasons
    public void eraseCredentials() {
        this.credentials = null;
    }
}
