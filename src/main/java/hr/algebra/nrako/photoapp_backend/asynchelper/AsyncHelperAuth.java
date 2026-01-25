package hr.algebra.nrako.photoapp_backend.asynchelper;

import hr.algebra.nrako.photoapp_backend.dto.*;
import hr.algebra.nrako.photoapp_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class AsyncHelperAuth {

    private final UserService userService;

    public Optional<AuthResponse> registerUser(RegistrationRequest request) throws ExecutionException, InterruptedException {
        return userService.registerUser(request).get();
    }

    public Optional<AuthResponse> loginUser(LoginRequest request) throws ExecutionException, InterruptedException {
        return userService.loginUser(request).get();
    }

    public Optional<UserDto> getUserByFirebaseUid(String uid) throws ExecutionException, InterruptedException {
        return userService.getUserByFirebaseUid(uid).get();
    }

    public Optional<AuthResponse> loginWithEmailAndPassword(String email, String password) throws ExecutionException, InterruptedException {
        return userService.loginWithEmailAndPassword(email, password).get();
    }// DODANO

    public Boolean updateUserTypeAndPackage(String uidFromToken, UpdateUserRequest request) throws ExecutionException, InterruptedException {
        return userService.updateUserTypeAndPackage(uidFromToken, request).get();
    }

    public Void logout(String idToken) throws ExecutionException, InterruptedException {
        return userService.logout(idToken).get();
    }

    public Void deleteAccount(String idToken) throws ExecutionException, InterruptedException {
        return userService.deleteAccount(idToken).get();
    }


}
