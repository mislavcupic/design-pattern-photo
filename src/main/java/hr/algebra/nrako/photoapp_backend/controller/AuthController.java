package hr.algebra.nrako.photoapp_backend.controller;

import com.google.firebase.auth.FirebaseAuth;
import hr.algebra.nrako.photoapp_backend.dto.LoginRequest;
import hr.algebra.nrako.photoapp_backend.dto.RegistrationRequest;
import hr.algebra.nrako.photoapp_backend.dto.UpdateUserRequest;
import hr.algebra.nrako.photoapp_backend.service.FirebaseService;
import hr.algebra.nrako.photoapp_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final String BEARER = "Bearer ";
    private final UserService userService;
    private final FirebaseService firebaseService;

    // 1. REGISTRACIJA
    @PostMapping("/register")
    public CompletableFuture<ResponseEntity<?>> register(@RequestBody RegistrationRequest request) {
        return userService.registerUser(request)
                .thenApply(result -> {
                    // Eksplicitno definiramo tip povratne vrijednosti za Optional mapiranje
                    ResponseEntity<?> response = result
                            .map(authRes -> ResponseEntity.ok((Object) authRes))
                            .orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                    return response;
                });
    }

    // 2. LOGIN (S asinkronom verifikacijom tokena)
    @PostMapping("/login")
    public CompletableFuture<ResponseEntity<Object>> login(@RequestBody LoginRequest request) {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        firebaseService.verifyIdToken(request.getIdToken());
                        return true;
                    } catch (Exception e) {
                        throw new RuntimeException("Invalid Firebase token");
                    }
                }).thenCompose(isValid -> userService.loginUser(request))
                .thenApply(result -> {
                    // Rješavanje "capture of ?" problema: castamo u ResponseEntity<?>
                    return result.map(authRes -> ResponseEntity.ok((Object) authRes))
                            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                })
                .exceptionally(ex -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Greška pri prijavi: " + ex.getMessage()));
    }

    // 3. UPDATE KORISNIKA
    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
    public CompletableFuture<ResponseEntity<?>> updateUser(
            @RequestHeader("Authorization") String authorization,
            @RequestBody UpdateUserRequest request) {

        String idToken = authorization.replace(BEARER, "");

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Verificiramo token asinkrono
                return FirebaseAuth.getInstance().verifyIdToken(idToken);
            } catch (Exception e) {
                throw new RuntimeException("Neautoriziran pristup");
            }
        }).thenCompose(decodedToken -> {
            String firebaseUid = decodedToken.getUid();
            return userService.updateUserTypeAndPackage(firebaseUid, request);
        }).thenApply(success -> {
            if (Boolean.TRUE.equals(success)) {
                return ResponseEntity.ok((Object) "Korisnik uspješno ažuriran.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Ažuriranje baze nije uspjelo.");
            }
        }).exceptionally(ex -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Nevaljan token ili sesija."));
    }

    // 4. BRISANJE RAČUNA
    @DeleteMapping("/delete")
    @PreAuthorize("isAuthenticated()")
    public CompletableFuture<ResponseEntity<Object>> deleteAccount(@RequestHeader("Authorization") String authHeader) {
        String idToken = authHeader.replace(BEARER, "");

        return userService.deleteAccount(idToken)
                .thenApply(v -> ResponseEntity.ok((Object) "Račun je uspješno obrisan."))
                .exceptionally(e -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Greška pri brisanju: " + e.getMessage()));
    }

    // 5. LOGOUT
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public CompletableFuture<ResponseEntity<Object>> logout(@RequestHeader("Authorization") String authHeader) {
        String idToken = authHeader.replace(BEARER, "");

        return userService.logout(idToken)
                .thenApply(msg -> ResponseEntity.ok((Object) "Odjava uspješna."))
                .exceptionally(e -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Greška pri odjavi."));
    }
}