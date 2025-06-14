package hr.algebra.nrako.photoapp_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import hr.algebra.nrako.photoapp_backend.domain.User;
import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.domain.UserType;
import hr.algebra.nrako.photoapp_backend.repository.PhotoRepository;
import hr.algebra.nrako.photoapp_backend.repository.UserPackageDataRepository;
import hr.algebra.nrako.photoapp_backend.repository.UserRepository;
import hr.algebra.nrako.photoapp_backend.dto.*;
import com.google.firebase.auth.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.cloud.Timestamp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.concurrent.ExecutionException;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserRepository userRepository;
    private final FirebaseAuth firebaseAuth;
    private final UserPackageDataRepository userPackageDataRepository;
    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final UserPackageService userPackageService;
    @Override
    public CompletableFuture<Optional<AuthResponse>> registerUser(RegistrationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Kreiraj korisnika u Firebase Authentication
                UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                        .setEmail(request.getEmail())
                        .setPassword(request.getPassword())
                        .setDisplayName(request.getDisplayName());

                UserRecord userRecord = firebaseAuth.createUser(createRequest);

                // 2. Spremi korisnika u Firestore (kolekcija 'users')
                User user = new User();
                user.setFirebaseUid(userRecord.getUid());
                user.setEmail(userRecord.getEmail());
                user.setDisplayName(userRecord.getDisplayName());
                user.setUserType(UserType.REGISTERED);
                user.setUserPackage(UserPackage.valueOf(request.getUserPackage()));

                userRepository.save(user);  // sprema u 'users' kolekciju

                //claim role
                Map<String, Object> claims = new HashMap<>();
                claims.put("roles", List.of(user.getUserType().name())); // Koristi ime enuma kao ulogu
                firebaseAuth.setCustomUserClaims(userRecord.getUid(), claims);

                // 3. Generiraj custom token za prijavu
                String token = firebaseAuth.createCustomToken(userRecord.getUid());
                Timestamp timestamp = Timestamp.now();
                UserPackageData packageData = new UserPackageData(
                        userRecord.getUid(),
                        request.getUserPackage(),
                        timestamp
                );
                userRepository.saveUserPackageData(packageData);  // pozivaš metodu iz repozitorija

                // 4. Vrati korisnika i token
                return Optional.of(new AuthResponse(token, null, mapToDto(user)));

            } catch (FirebaseAuthException e) {
                logger.error("FirebaseAuthException prilikom registracije: {}", e.getMessage());
                return Optional.empty();
            } catch (Exception e) {
                logger.error("Greška prilikom registracije: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Optional<AuthResponse>> loginUser(LoginRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(request.getIdToken());
                String uid = decodedToken.getUid();
                logger.info("🔑 Uspješno verificiran ID token, UID: {}", uid);
                return uid;
            } catch (FirebaseAuthException e) {
                logger.error("FirebaseAuthException during login: {}", e.getMessage());
                return null;
            } catch (Exception e) {
                logger.error("Exception during login: {}", e.getMessage());
                return null;
            }
        }).thenCompose(uid -> {
            if (uid == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            return userRepository.findByFirebaseUid(uid)
                    .thenCompose(userOptional -> {
                        logger.info("🔍 Rezultat pretrage korisnika za UID {}: Postoji = {}", uid, userOptional.isPresent());

                        if (userOptional.isPresent()) {
                            User user = userOptional.get();
                            logger.info("✔️ Korisnik pronađen: {}", user.getEmail());
                            try {
                                // Postavi custom claims prilikom logina
                                Map<String, Object> claims = new HashMap<>();
                                claims.put("userType", user.getUserType().name());
                                firebaseAuth.setCustomUserClaims(uid, claims);

                                // Generiraj custom token (ovo može biti nepotrebno ako koristiš ID token za autentikaciju)
                                // Ako koristiš ID token, ne trebaš generirati custom token ovdje.
                                // Samo proslijedi ID token s frontend-a.
                                // Ako ti treba custom token za neke druge svrhe, ostavi ovo.
                                String token = firebaseAuth.createCustomToken(uid);

                                return CompletableFuture.completedFuture(
                                        Optional.of(new AuthResponse(token, request.getRefreshToken(), mapToDto(user)))
                                );
                            } catch (FirebaseAuthException e) {
                                logger.error("Greška kod postavljanja custom claim-a ili generiranja tokena: {}", e.getMessage());
                                return CompletableFuture.completedFuture(Optional.empty());
                            }
                        } else {
                            // ... (ostatak tvoje logike za kreiranje novog korisnika ako ne postoji) ...
                            try {
                                UserRecord firebaseUser = firebaseAuth.getUser(uid);
                                UserPackage userPackageEnum = UserPackage.valueOf(
                                        Optional.ofNullable(request.getUserPackage()).orElse("FREE").toUpperCase()
                                );

                                logger.info("➡️ Pokušavam kreirati UserPackageData za UID: {}, paket: {}", firebaseUser.getUid(), userPackageEnum);
                                userPackageService.createUserPackageData(firebaseUser.getUid(), userPackageEnum);
                                logger.info("⬅️ Završio kreiranje UserPackageData za UID: {}", firebaseUser.getUid());

                                User newUser = new User();
                                newUser.setFirebaseUid(firebaseUser.getUid());
                                newUser.setEmail(firebaseUser.getEmail());
                                newUser.setDisplayName(firebaseUser.getDisplayName());
                                //newUser.setUserPackage(userPackageEnum);
                                newUser.setUserType(UserType.REGISTERED); // Postavi default userType za novog korisnika
                                userRepository.save(newUser);

                                // Postavi custom claims za novog korisnika
                                Map<String, Object> newClaims = new HashMap<>();
                                newClaims.put("userType", newUser.getUserType().name());
                                firebaseAuth.setCustomUserClaims(uid, newClaims);

                                String token = firebaseAuth.createCustomToken(uid);
                                return CompletableFuture.completedFuture(
                                        Optional.of(new AuthResponse(token, request.getRefreshToken(), mapToDto(newUser)))
                                );

                            } catch (FirebaseAuthException e) {
                                logger.error("❌ FirebaseAuthException: {}", e.getMessage());
                            } catch (IllegalArgumentException e) {
                                logger.error("❌ Neispravan userType ili userPackage u loginRequestu: {}", e.getMessage());
                            } catch (Exception e) {
                                logger.error("❌ General Exception kod automatskog spremanja korisnika: {}", e.getMessage());
                            }
                            return CompletableFuture.completedFuture(Optional.empty());
                        }
                    });
        });
    }
//     @Override
//    public CompletableFuture<Optional<AuthResponse>> loginUser(LoginRequest request) {
//        return CompletableFuture.supplyAsync(() -> {
//            try {
//                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(request.getIdToken());
//                return decodedToken.getUid();
//            } catch (FirebaseAuthException e) {
//                logger.error("FirebaseAuthException during login: {}", e.getMessage());
//                return null;
//            } catch (Exception e) {
//                logger.error("Exception during login: {}", e.getMessage());
//                return null;
//            }
//        }).thenCompose(uid -> {
//            if (uid == null) {
//                return CompletableFuture.completedFuture(Optional.empty());
//            }
//
//            return userRepository.findByFirebaseUid(uid)
//                    .thenCompose(userOptional -> {
//                        if (userOptional.isPresent()) {
//                            User user = userOptional.get();
//                            logger.info("✔️ Korisnik pronađen: {}", user.getEmail());
//
//                            try {
//                                String token = firebaseAuth.createCustomToken(uid);
//                                return CompletableFuture.completedFuture(
//                                        Optional.of(new AuthResponse(token, request.getRefreshToken(), mapToDto(user)))
//                                );
//                            } catch (FirebaseAuthException e) {
//                                logger.error("Greška kod generiranja tokena: {}", e.getMessage());
//                                return CompletableFuture.completedFuture(Optional.empty());
//                            }
//
//                        } else {
//                            logger.warn("❌ Korisnik s UID-om {} nije u bazi, dohvaćam iz Firebasea...", uid);
//
//                            try {
//                                UserRecord firebaseUser = firebaseAuth.getUser(uid);
//                                UserPackage userPackage = UserPackage.valueOf(
//                                        Optional.ofNullable(request.getUserPackage()).orElse("FREE").toUpperCase()
//                                );
//
//                                // Kreiraj i spremi korisnika
//                                User newUser = new User();
//                                newUser.setFirebaseUid(firebaseUser.getUid());
//                                newUser.setEmail(firebaseUser.getEmail());
//                                newUser.setDisplayName(firebaseUser.getDisplayName());
//                                newUser.setUserPackage(userPackage);
//                                userRepository.save(newUser);
//
//                                // Kreiraj UserPackageData za novog korisnika
//                                userPackageService.createUserPackageData(firebaseUser.getUid(), userPackage);
//
//                                String token = firebaseAuth.createCustomToken(uid);
//                                return CompletableFuture.completedFuture(
//                                        Optional.of(new AuthResponse(token, request.getRefreshToken(), mapToDto(newUser)))
//                                );
//
//                            } catch (FirebaseAuthException e) {
//                                logger.error("❌ FirebaseAuthException: {}", e.getMessage());
//                            } catch (IllegalArgumentException e) {
//                                logger.error("❌ Neispravan userType ili userPackage u loginRequestu: {}", e.getMessage());
//                            } catch (Exception e) {
//                                logger.error("❌ General Exception kod automatskog spremanja korisnika: {}", e.getMessage());
//                            }
//
//                            return CompletableFuture.completedFuture(Optional.empty());
//                        }
//                    });
//        });
//    }

    @Override
    public CompletableFuture<Optional<UserDto>> getUserByFirebaseUid(String uid) {
        return userRepository.findByFirebaseUid(uid)
                .thenApply(optionalUser -> optionalUser.map(this::mapToDto));
    }

    //testna metoda dok nemam frontend
    public CompletableFuture<Optional<AuthResponse>> loginWithEmailAndPassword(String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Firebase Admin SDK ne podržava password login!
                // Ovo bi inače morao raditi na klijentu (JS, Android, itd.)
                // Ovdje možeš simulirati prijavu tako da pronađeš korisnika u bazi i vratiš custom token

                Optional<User> userOpt = userRepository.findByEmail(email);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    String token = firebaseAuth.createCustomToken(user.getFirebaseUid());
                    return Optional.of(new AuthResponse(token, null, mapToDto(user)));
                }
            } catch (Exception e) {
                logger.error("Error in loginWithEmailAndPassword", e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Boolean> updateUserTypeAndPackage(String uidFromToken, UpdateUserRequest request) {
        return userRepository.findByFirebaseUid(uidFromToken)
                .thenApply(optionalUser -> {
                    if (optionalUser.isPresent()) {
                        User user = optionalUser.get();
                        try {
                            user.setUserType(UserType.valueOf(request.getUserType().toUpperCase()));
                            user.setUserPackage(UserPackage.valueOf(request.getUserPackage().toUpperCase()));

                            userRepository.saveAndFlush(user);

                            logger.info("✅ Ažuriran korisnik ({}): type={}, package={}",
                                    user.getEmail(), user.getUserType(), user.getUserPackage());

                            return true;
                        } catch (IllegalArgumentException e) {
                            logger.warn("❌ Neispravan userType ili userPackage: {}", e.getMessage());
                        }
                    } else {
                        logger.warn("❌ Korisnik s UID-om {} nije pronađen u bazi", uidFromToken);
                    }

                    return false;
                });
    }

//    @Override
//    public CompletableFuture<Void> logout(String idToken) {
//        return CompletableFuture.runAsync(() -> {
//            try {
//                // Ukloni "Bearer " prefiks ako je prisutan
//                if (idToken.startsWith("Bearer ")) {
//                    idToken = idToken.substring(7); // Uklanja "Bearer "
//                }
//
//                // Verifikacija idToken-a pomoću Firebase Admin SDK
//                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(request.getIdToken());
//                return decodedToken.getUid();  // Ovdje uzimamo UID korisnika iz tokena
//                System.out.println("Odjava korisnika s UID-om: " + uid);
//
//                // Revokacija refresh tokena za korisnika s UID-om
//                FirebaseAuth.getInstance().revokeRefreshTokens(uid);
//                System.out.println("Refresh tokeni za UID " + uid + " su revokirani.");
//
//            } catch (FirebaseAuthException e) {
//                // Logiraj grešku i baci odgovarajuću iznimku
//                throw new RuntimeException("Neispravan Firebase token: " + e.getMessage());
//            }
//        });
//    }

    @Override
    public CompletableFuture<Void> logout(String idToken) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Verificiraj Firebase ID token
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
                String uid = decodedToken.getUid();  // Iz tokena izvući UID korisnika

                // Logiraj UID koji je odjavljen
                logger.info("🚪 Korisnik se odjavljuje s UID-om: {}", uid);

                // Revokacija refresh tokena za korisnika
                firebaseAuth.revokeRefreshTokens(uid);
                logger.info("✅ Refresh tokeni za UID {} su revokirani.", uid);

            } catch (FirebaseAuthException e) {
                logger.error("❌ FirebaseAuthException: {}", e.getMessage());
                throw new RuntimeException("Neispravan Firebase token");
            } catch (Exception e) {
                logger.error("❌ Greška pri logoutu: {}", e.getMessage());
                throw new RuntimeException("Nešto je pošlo po zlu prilikom odjave");
            }
        });
    }


    @Override
    public CompletableFuture<Void> deleteAccount(String idToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Verifikacija ID tokena i ekstrakcija UID-a
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
                String uid = decodedToken.getUid();  // UID korisnika

                // Brisanje korisničkih podataka iz Firestore-a
                userRepository.deleteUserData(uid);



                // Opcionalno: brisanje korisnika iz Firebase Authentication-a
                firebaseAuth.deleteUser(uid);

                return null;  // Brisanje je uspješno

            } catch (Exception e) {
                // Logiranje i obraditi greške
                throw new RuntimeException("Greška prilikom brisanja računa: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void setAdminUserType(String firebaseUid) {
        try {
            FirebaseAuth.getInstance().setCustomUserClaims(firebaseUid, Map.of("userType", "ADMIN"));
            System.out.println("User type 'ADMIN' postavljen za korisnika: " + firebaseUid);
        } catch (Exception e) {
            System.err.println("Greška pri postavljanju user type: " + e.getMessage());
        }
    }

    @Override
    public void setUserType(String firebaseUid, String userType) {
        try {
            FirebaseAuth.getInstance().setCustomUserClaims(firebaseUid, Map.of("userType", userType));
            System.out.println("User type '" + userType + "' postavljen za korisnika: " + firebaseUid);
        } catch (Exception e) {
            System.err.println("Greška pri postavljanju user type: " + e.getMessage());
        }
    }

    @Override
    public void grantAdminRole(String firebaseUid) {
        userRepository.findByFirebaseUid(firebaseUid)
                .thenAccept(userOptional -> {
                    userOptional.ifPresent(user -> {
                        // Ažuriraj ulogu u User objektu
                        user.setUserType(UserType.ADMIN);
                        // Spremi ažurirani User natrag u Firestore
                        userRepository.save(user);
                        // Ažuriraj Custom Claim
                        setAdminUserType(firebaseUid);
                    });
                })
                .exceptionally(e -> {
                    System.err.println("Greška pri dodjeli admin uloge: " + e.getMessage());
                    return null; // Potrebno za Function
                });
    }

    @Override
    public void revokeAdminRole(String firebaseUid) {
        userRepository.findByFirebaseUid(firebaseUid)
                .thenAccept(userOptional -> {
                    userOptional.ifPresent(user -> {
                        // Ažuriraj ulogu u User objektu
                        user.setUserType(UserType.REGISTERED);
                        // Spremi ažurirani User natrag u Firestore
                        userRepository.save(user);
                        // Ažuriraj Custom Claim
                        setUserType(firebaseUid, "REGISTERED");
                    });
                })
                .exceptionally(e -> {
                    System.err.println("Greška pri oduzimanju admin uloge: " + e.getMessage());
                    return null; // Potrebno za Function
                });
    }




    private UserDto mapToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setDisplayName(user.getDisplayName());
        dto.setUserType(user.getUserType());
        dto.setUserPackage(user.getUserPackage());
        dto.setFirebaseUid(user.getFirebaseUid()); // Postavi firebaseUid
        return dto;
    }
}
