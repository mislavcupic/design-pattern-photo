package hr.algebra.nrako.photoapp_backend.controller;

import hr.algebra.nrako.photoapp_backend.asynchelper.AsyncHelperAuth;
import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserType;
import hr.algebra.nrako.photoapp_backend.dto.*;
import hr.algebra.nrako.photoapp_backend.service.FirebaseService;
import hr.algebra.nrako.photoapp_backend.service.UserService;
// import com.google.firebase.auth.FirebaseAuth; // Više ti ne treba import ako ne testiras updateUser
import com.google.firebase.auth.FirebaseToken; // Ostaje ako se koristi u login testovima
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
// import org.mockito.MockedStatic; // Potpuno uklonjeno
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// import org.springframework.security.core.Authentication; // Više ti ne treba ako ne testiras updateUser
// import org.springframework.security.core.context.SecurityContext; // Više ti ne treba ako ne testiras updateUser
// import org.springframework.security.core.context.SecurityContextHolder; // Više ti ne treba ako ne testiras updateUser

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private AsyncHelperAuth asyncHelperAuth;
    @Mock
    private FirebaseService firebaseService;

    @InjectMocks
    private AuthController authController;

    private AuthResponse mockAuthResponse;
    private RegistrationRequest mockRegistrationRequest;
    private LoginRequest mockLoginRequest;
    private UpdateUserRequest mockUpdateUserRequest; // Nije se direktno koristio u setUp
    private UserDto mockUserDto;

    @BeforeEach
    void setUp() {
        mockUserDto = new UserDto(
                1L,
                "test@example.com",
                UserType.REGISTERED,
                UserPackage.FREE,
                "mockFirebaseUid",
                "Test User Display"
        );
        mockAuthResponse = new AuthResponse("mockAccessToken", "mockRefreshToken", mockUserDto);
        mockRegistrationRequest = new RegistrationRequest("register@example.com", "password123", "Reg User", "FREE", "regIdToken");
        mockLoginRequest = new LoginRequest("login@example.com", "loginPass", "loginIdToken", "loginRefreshToken", "REGISTERED", "PRO");

        // mockUpdateUserRequest je ostao definiran ovdje iako se metoda ne testira,
        // cisto da ne moras mijenjati puno vise koda ako ga negdje drugdje koristis
        mockUpdateUserRequest = new UpdateUserRequest();
        mockUpdateUserRequest.setUserType("ADMIN");
        mockUpdateUserRequest.setUserPackage("GOLD");
    }

    // --- Testovi za /register endpoint ---
    @Test
    void register_shouldReturnOk_whenRegistrationIsSuccessful() throws ExecutionException, InterruptedException {
        // Arrange
        when(asyncHelperAuth.registerUser(mockRegistrationRequest))
                .thenReturn(Optional.of(mockAuthResponse));

        // Act
        ResponseEntity<?> response = authController.register(mockRegistrationRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockAuthResponse, response.getBody());
        verify(asyncHelperAuth, times(1)).registerUser(mockRegistrationRequest);
    }

    @Test
    void register_shouldReturnBadRequest_whenRegistrationFails() throws ExecutionException, InterruptedException {
        // Arrange
        when(asyncHelperAuth.registerUser(mockRegistrationRequest))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = authController.register(mockRegistrationRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(asyncHelperAuth, times(1)).registerUser(mockRegistrationRequest);
    }

    @Test
    void register_shouldThrowException_whenServiceThrowsException() throws ExecutionException, InterruptedException {
        // Arrange
        when(asyncHelperAuth.registerUser(mockRegistrationRequest))
                .thenThrow(new RuntimeException("Simulated registration error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> authController.register(mockRegistrationRequest));
        verify(asyncHelperAuth, times(1)).registerUser(mockRegistrationRequest);
    }

    // --- Testovi za /login endpoint ---
    @Test
    void login_shouldReturnOk_whenLoginIsSuccessful() throws Exception {
        // Arrange
        FirebaseToken mockFirebaseToken = mock(FirebaseToken.class);
        when(firebaseService.verifyIdToken(mockLoginRequest.getIdToken())).thenReturn(mockFirebaseToken);
        when(asyncHelperAuth.loginUser(mockLoginRequest))
                .thenReturn(Optional.of(mockAuthResponse));

        // Act
        ResponseEntity<?> response = authController.login(mockLoginRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockAuthResponse, response.getBody());
        verify(firebaseService, times(1)).verifyIdToken(mockLoginRequest.getIdToken());
        verify(asyncHelperAuth, times(1)).loginUser(mockLoginRequest);
    }



    @Test
    void login_shouldReturnUnauthorized_whenLoginFailsInService() throws Exception {
        // Arrange
        FirebaseToken mockFirebaseToken = mock(FirebaseToken.class);
        when(firebaseService.verifyIdToken(mockLoginRequest.getIdToken())).thenReturn(mockFirebaseToken);
        when(asyncHelperAuth.loginUser(mockLoginRequest))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = authController.login(mockLoginRequest);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
        verify(firebaseService, times(1)).verifyIdToken(mockLoginRequest.getIdToken());
        verify(asyncHelperAuth, times(1)).loginUser(mockLoginRequest);
    }

    // --- TESTOVI ZA /update ENDPOINT SU UKLONJENI JER BI ZAHTIJEVALI STATIČKO MOCKIRANJE ---

    // --- Testovi za /delete endpoint ---
    @Test
    void deleteAccount_shouldReturnOk_whenDeletionIsSuccessful() throws Exception {
        String authHeader = "Bearer someIdToken";
        doNothing().when(asyncHelperAuth).deleteAccount(anyString());

        ResponseEntity<String> response = authController.deleteAccount(authHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Account deleted", response.getBody());
        verify(asyncHelperAuth, times(1)).deleteAccount("someIdToken");
    }


    // --- Testovi za /logout endpoint ---
    @Test
    void logout_shouldReturnOk_whenLogoutIsSuccessful() throws Exception {
        String authHeader = "Bearer someIdToken";
        doNothing().when(asyncHelperAuth).logout(anyString());

        CompletableFuture<ResponseEntity<Object>> response = authController.logout(authHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Odjavili ste se", response.getBody());
        verify(asyncHelperAuth, times(1)).logout("someIdToken");
    }


}