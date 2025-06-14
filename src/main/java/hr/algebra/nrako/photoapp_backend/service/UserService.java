package hr.algebra.nrako.photoapp_backend.service;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.dto.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface UserService {

    CompletableFuture<Optional<AuthResponse>> registerUser(RegistrationRequest request);

    CompletableFuture<Optional<AuthResponse>> loginUser(LoginRequest request);

    CompletableFuture<Optional<UserDto>> getUserByFirebaseUid(String uid);

    CompletableFuture<Optional<AuthResponse>> loginWithEmailAndPassword(String email, String password); // DODANO

    CompletableFuture<Boolean> updateUserTypeAndPackage(String uidFromToken, UpdateUserRequest request);

    CompletableFuture<Void> logout(String idToken);

    CompletableFuture<Void> deleteAccount(String idToken);

    void setAdminUserType(String firebaseUid);
    void setUserType(String firebaseUid, String userType);
    void grantAdminRole(String userId);
    void revokeAdminRole(String userId);
}
