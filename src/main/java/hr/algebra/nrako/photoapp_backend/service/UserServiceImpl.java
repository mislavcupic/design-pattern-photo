package hr.algebra.nrako.photoapp_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
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
import org.springframework.core.annotation.Order;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserRepository userRepository;
    private final FirebaseAuth firebaseAuth;

    private final UserPackageService userPackageService;

    private final List<UserDeletionStrategy> deletionStrategies;


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
            String uid = null;
            try {
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
                uid = decodedToken.getUid();
                logger.info("Attempting to delete account for UID: {}", uid);

                // Izvršavamo sve strategije u sortiranom redoslijedu
                for (UserDeletionStrategy strategy : deletionStrategies) {
                    try {
                        logger.debug("Executing deletion strategy: {}", strategy.getClass().getSimpleName());
                        strategy.delete(uid);
                    } catch (Exception strategyEx) {
                        logger.error("Error executing deletion strategy {} for UID {}: {}",
                                strategy.getClass().getSimpleName(), uid, strategyEx.getMessage(), strategyEx);
                        // Ako se dogodi greška u bilo kojoj strategiji, zaustavljamo cijeli proces.
                        throw new RuntimeException("Failed to complete a deletion step for user " + uid + ": " + strategyEx.getMessage(), strategyEx);
                    }
                }

                logger.info("Account deletion process completed for UID: {}", uid);
                return null;

            } catch (Exception e) {
                logger.error("FATAL Error during account deletion for UID {}: {}", uid, e.getMessage(), e);
                throw new RuntimeException("General error during account deletion: " + e.getMessage(), e);
            }
        });
    }


//
//@Override
//public CompletableFuture<Void> deleteAccount(String idToken) {
//    return CompletableFuture.supplyAsync(() -> {
//        try {
//            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
//            String uid = decodedToken.getUid();
//
//            // DOHVATI STORAGE BUCKET
//            StorageClient storageClient = StorageClient.getInstance();
//            Bucket bucket = storageClient.bucket();
//
//            // 1. BRIŠI PODATKE IZ FIRESTORE 'photos' KOLEKCIJE I CORRESPONDING STORAGE DATOTEKE
//            CollectionReference photosCollection = firestore.collection("photos");
//            ApiFuture<QuerySnapshot> photosFuture = photosCollection.whereEqualTo("uploadedBy", uid).get();
//            List<QueryDocumentSnapshot> photoDocuments = photosFuture.get().getDocuments();
//
//            if (photoDocuments != null && !photoDocuments.isEmpty()) {
//                for (QueryDocumentSnapshot document : photoDocuments) {
//                    String fileUrl = document.getString("fileUrl");
//                    if (fileUrl != null && !fileUrl.isEmpty()) {
//                        String storagePath = extractStoragePathFromUrl(fileUrl);
//                        if (storagePath != null) {
//                            try {
//                                BlobId blobId = BlobId.of(bucket.getName(), storagePath);
//                                Blob blob = bucket.getStorage().get(blobId);
//                                if (blob != null && blob.exists()) {
//                                    blob.delete();
//                                    System.out.println("Obrisana datoteka iz Storagea: " + storagePath);
//                                } else {
//                                    System.out.println("Datoteka ne postoji u Storageu ili već obrisana: " + storagePath);
//                                }
//                            } catch (Exception storageEx) {
//                                System.err.println("Greška prilikom brisanja datoteke iz Storagea: " + storagePath + ". Greška: " + storageEx.getMessage());
//                            }
//                        }
//                    }
//                    // Nakon pokušaja brisanja iz Storagea, obriši dokument iz Firestore 'photos' kolekcije
//                    document.getReference().delete();
//                    System.out.println("Obrisan dokument iz Firestore 'photos' kolekcije: " + document.getId());
//                }
//            }
//
//            // 2. BRIŠI PODATKE IZ FIRESTORE 'user_package_data' KOLEKCIJE
//            CollectionReference userPackageCollection = firestore.collection("user_package_data");
//            ApiFuture<QuerySnapshot> userPackageFuture = userPackageCollection.whereEqualTo("firebaseUid", uid).get(); // Pretpostavka da imaš 'userId' polje
//            List<QueryDocumentSnapshot> userPackageDocuments = userPackageFuture.get().getDocuments();
//
//            if (userPackageDocuments != null && !userPackageDocuments.isEmpty()) {
//                for (QueryDocumentSnapshot document : userPackageDocuments) {
//                    document.getReference().delete();
//                    System.out.println("Obrisan dokument iz Firestore 'user_package_data' kolekcije: " + document.getId());
//                }
//            } else {
//                // Ako 'user_package_data' ima dokument ID koji je isti kao UID, možeš i direktno
//                // firestore.collection("user_package_data").document(uid).delete();
//                // Provjeri kako ti je strukturiran user_package_data
//                System.out.println("Nema dokumenata u 'user_package_data' za brisanje ili već obrisani.");
//            }
//
//
//            // 3. BRIŠI GLAVNI KORISNIČKI DOKUMENT IZ 'users' KOLEKCIJE
//            // Ovo je vjerojatno ono što tvoj userRepository.deleteUserData(uid) radi
//            // Ako tvoj UserRepository sadrži više složenu logiku, ostavi ga.
//            // Ako ne, možeš ga zamijeniti direktnim pozivom:
//            // firestore.collection("users").document(uid).delete();
//            userRepository.deleteUserData(uid); // Ovo je za glavni users dokument
//            System.out.println("Korisnički podaci obrisani iz Firestore 'users' kolekcije za UID: " + uid);
//
//
//            // 4. BRIŠI KORISNIKA IZ FIREBASE AUTHENTICATION-A
//            firebaseAuth.deleteUser(uid);
//            System.out.println("Korisnik obrisan iz Firebase Authentikacije: " + uid);
//
//            return null;
//
//        } catch (ExecutionException | InterruptedException e) {
//            Thread.currentThread().interrupt();
//            System.err.println("Greška prilikom dohvata ili obrade podataka za brisanje (ExecutionException/InterruptedException): " + e.getMessage());
//            throw new RuntimeException("Greška prilikom dohvata podataka za brisanje: " + e.getMessage(), e);
//        } catch (Exception e) {
//            System.err.println("FATALNA Greška prilikom brisanja računa za UID. Provjerite dozvole ili strukturu baze: " + e.getMessage());
//            e.printStackTrace(); // Ispiši cijeli stack trace za debug
//            throw new RuntimeException("Opća greška prilikom brisanja računa: " + e.getMessage(), e);
//        }
//    });
//}
//
//    // Pomoćna funkcija ostaje ista
//    private String extractStoragePathFromUrl(String fileUrl) {
//        if (fileUrl == null || fileUrl.isEmpty()) {
//            return null;
//        }
//        try {
//            int oIndex = fileUrl.indexOf("/o/");
//            if (oIndex == -1) {
//                return null;
//            }
//
//            String pathWithQueryParams = fileUrl.substring(oIndex + 3);
//
//            int qIndex = pathWithQueryParams.indexOf("?");
//            String encodedPath = (qIndex == -1) ? pathWithQueryParams : pathWithQueryParams.substring(0, qIndex);
//
//            String decodedPath = java.net.URLDecoder.decode(encodedPath, "UTF-8");
//
//            return decodedPath;
//        } catch (Exception e) {
//            System.err.println("Greška prilikom parsiranja Storage URL-a: " + fileUrl + ". Greška: " + e.getMessage());
//            return null;
//        }
//    }
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
