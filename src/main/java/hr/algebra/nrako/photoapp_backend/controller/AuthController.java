package hr.algebra.nrako.photoapp_backend.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import hr.algebra.nrako.photoapp_backend.asynchelper.AsyncHelperAuth;
import hr.algebra.nrako.photoapp_backend.dto.AuthResponse;
import hr.algebra.nrako.photoapp_backend.dto.LoginRequest;
import hr.algebra.nrako.photoapp_backend.dto.RegistrationRequest;
import hr.algebra.nrako.photoapp_backend.dto.UpdateUserRequest;
import hr.algebra.nrako.photoapp_backend.service.FirebaseService;
import hr.algebra.nrako.photoapp_backend.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


@RestController
@RequestMapping("/auth")
@Data
public class AuthController {

    private final UserService userService;
    private final AsyncHelperAuth asyncHelperAuth;
    private final FirebaseService firebaseService;

    public AuthController(UserService userService, AsyncHelperAuth asyncHelperAuth, FirebaseService firebaseService) {
        this.userService = userService;
        this.asyncHelperAuth = asyncHelperAuth;
        this.firebaseService = firebaseService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegistrationRequest request) throws ExecutionException, InterruptedException {
        Optional<AuthResponse> result = asyncHelperAuth.registerUser(request);

        return result
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            firebaseService.verifyIdToken(request.getIdToken());
            Optional<AuthResponse> result = asyncHelperAuth.loginUser(request); // Synchronous version

            return result
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN') or #firebaseUid == authentication.principal")
    public ResponseEntity<String> updateUser(
            @RequestHeader("Authorization") String authorization,
            @RequestBody UpdateUserRequest request) {

        String idToken = authorization.replace("Bearer ", "");

        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String firebaseUid = decodedToken.getUid();

            boolean success = asyncHelperAuth.updateUserTypeAndPackage(firebaseUid, request); // Now synchronous

            if (success) {
                return ResponseEntity.ok("Korisnik ažuriran.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Ažuriranje nije uspjelo.");
            }

        } catch (FirebaseAuthException | ExecutionException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Nevaljan token");
        }
    }

    @DeleteMapping("/delete")
    @PreAuthorize("#firebaseUid == authentication.principal")
    public ResponseEntity<String> deleteAccount(@RequestHeader("Authorization") String authHeader) {
        String idToken = authHeader.replace("Bearer ", "");

        try {
            asyncHelperAuth.deleteAccount(idToken); // Now synchronous (void or boolean)
            return ResponseEntity.ok("Account deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            String idToken = authHeader.replace("Bearer ", "");
            asyncHelperAuth.logout(idToken); // Now synchronous
            return ResponseEntity.ok("Odjavili ste se");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }
}


//package hr.algebra.nrako.photoapp_backend.controller;
//
//
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseAuthException;
//import com.google.firebase.auth.FirebaseToken;
//import hr.algebra.nrako.photoapp_backend.dto.LoginRequest;
//import hr.algebra.nrako.photoapp_backend.dto.RegistrationRequest;
//import hr.algebra.nrako.photoapp_backend.dto.UpdateUserRequest;
//import hr.algebra.nrako.photoapp_backend.service.FirebaseService;  // Dodaj import za FirebaseService
//import hr.algebra.nrako.photoapp_backend.service.UserService;
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.Optional;
//import java.util.concurrent.CompletableFuture;
//
//@CrossOrigin(origins = "http://localhost:3000")
//@RestController
//@RequestMapping("/auth")
//@Data
//@AllArgsConstructor
//public class AuthController {
//
//    private final UserService userService;
//    private final FirebaseService firebaseService;  // Dodaj FirebaseService instancu
//
//    // Register endpoint
//
//
//    @PostMapping("/register")
//    public CompletableFuture<ResponseEntity<?>> register(@RequestBody RegistrationRequest request) {
//        return userService.registerUser(request)
//                .thenApply(result -> result
//                        .map(ResponseEntity::ok)
//                        .orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build()));
//    }
//
//    // Login endpoint s provjerom Firebase ID tokena
//    @PostMapping("/login")
//    public CompletableFuture<ResponseEntity<?>> login(@RequestBody LoginRequest request) {
//        System.out.println("📥 Primljen loginRequest: " + request);
//        System.out.println("🔑 ID Token: " + request.getIdToken());
//
//        try {
//            // Provjeri Firebase ID token
//            firebaseService.verifyIdToken(request.getIdToken());
//            System.out.println("✅ Firebase ID token is valid!");
//
//            // Nastavi s login procesom
//            return userService.loginUser(request)
//                    .thenApply(result -> result
//                            .map(ResponseEntity::ok)
//                            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
//        } catch (Exception e) {
//            // Ako je token neispravan, vrati grešku
//            System.out.println("❌ Firebase ID token verification failed: " + e.getMessage());
//            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token"));
//        }
//    }
//
//
////    @PostMapping("/logout")
////    public CompletableFuture<ResponseEntity<String>> logout(@RequestHeader("Authorization") String authHeader) {
////        String idToken = authHeader.replace("Bearer ", "");
////        return userService.logout(idToken)
////                .thenApply(msg -> ResponseEntity.ok("Logged out"))
////                .exceptionally(e -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage()));
////    }
//
//
//    // Testni login bez ID tokena
//    @PostMapping("/login-test")
//    public CompletableFuture<ResponseEntity<?>> loginWithoutToken(@RequestBody LoginRequest request) {
//        return userService.loginWithEmailAndPassword(request.getEmail(), request.getPassword())
//                .thenApply(result -> result
//                        .map(ResponseEntity::ok)
//                        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
//    }
//
//    // Endpoint za obnavljanje ID tokena koristeći refresh token
//    @PostMapping("/refresh-token")
//    public ResponseEntity<?> refreshToken(@RequestBody String refreshToken) {
//        try {
//            // Ovdje šaljemo refresh token na backend i pozivamo FirebaseService za obnavljanje ID tokena
//            String newIdToken = firebaseService.refreshIdToken(refreshToken);
//            return ResponseEntity.ok(newIdToken);  // Vraćamo novi ID token klijentu
//        } catch (Exception e) {
//            return ResponseEntity.status(400).body("Error refreshing token: " + e.getMessage());
//        }
//    }
//
//    @PutMapping("/update")
//    public CompletableFuture<ResponseEntity<String>> updateUser(
//            @RequestHeader("Authorization") String authorization,
//            @RequestBody UpdateUserRequest request) {
//
//        String idToken = authorization.replace("Bearer ", "");
//
//        try {
//            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
//            String firebaseUid = decodedToken.getUid();
//
//            return userService.updateUserTypeAndPackage(firebaseUid, request)
//                    .thenApply(success -> {
//                        if (success) {
//                            return ResponseEntity.ok("Korisnik ažuriran.");
//                        } else {
//                            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                                    .body("Ažuriranje nije uspjelo.");
//                        }
//                    });
//        } catch (FirebaseAuthException e) {
//            return CompletableFuture.completedFuture(
//                    ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Nevaljan token"));
//        }
//    }
//    @DeleteMapping("/delete")
//    public CompletableFuture<ResponseEntity<String>> deleteAccount(@RequestHeader("Authorization") String authHeader) {
//        String idToken = authHeader.replace("Bearer ", "");
//
//        // Proslijediti token do servisne metode za provjeru i brisanje
//        return userService.deleteAccount(idToken)
//                .thenApply(v -> ResponseEntity.ok("Account deleted"))
//                .exceptionally(e -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: " + e.getMessage()));
//    }
//
//    @PostMapping("/logout")
//    public CompletableFuture<ResponseEntity<String>> logout(@RequestHeader("Authorization") String authHeader) {
//        // Ukloni "Bearer " prefiks
//        String idToken = authHeader.replace("Bearer ", "");
//
//        return userService.logout(idToken)
//                .thenApply(msg -> ResponseEntity.ok("Odjavili ste se"))
//                .exceptionally(e -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage()));
//    }
//
//}
