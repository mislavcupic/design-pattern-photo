package hr.algebra.nrako.photoapp_backend.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.repository.PhotoRepository;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private FirebaseAuth firebaseAuth;
    @Autowired private PhotoRepository photoRepository;

    @BeforeEach
    void setup() {
        Mockito.reset(firebaseAuth);
        photoRepository.deleteAll();
    }

    private void mockFirebaseToken(String uid, String role, String tokenValue) throws Exception {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn(uid);

        // VAŽNO: Provjeri točan naziv ključa u FirebaseAuthenticationFilteru
        // Ako tamo piše "userType", ovdje mora biti "userType"
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("userType", role);

        when(mockToken.getClaims()).thenReturn(claims);
        when(firebaseAuth.verifyIdToken(tokenValue)).thenReturn(mockToken);
    }

    @Test
    @Order(1)
    @DisplayName("Javne rute - Dozvoljen pristup bez tokena")
    void whenPublicRoute_thenOk() throws Exception {
        mockMvc.perform(get("/api/photos/public")).andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("Zaštićena ruta - Bez tokena vraća 403 ili 500 ovisno o NPE")
    void whenNoToken_thenIsUnauthorized() throws Exception {
        // Dodajemo sliku s vlasnikom da izbjegnemo NullPointerException iz tvog loga
        Photo p = new Photo();
        p.setId(1L);
        p.setUploadedBy("neki-vlasnik");
        photoRepository.savePhoto(p);

        // Prema tvom logu, anonimni user dobiva Forbidden (403) jer nije vlasnik
        mockMvc.perform(put("/api/photos/1/toggle-privacy")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("DELETE - Admin ima pristup (204)")
    void whenAdminDeletes_thenOk() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setUploadedBy("user-uid"); // Mora postojati vlasnik zbog tvog servisa
        photoRepository.savePhoto(photo);

        mockFirebaseToken("admin-uid", "ADMIN", "admin-token");

        mockMvc.perform(delete("/api/photos/1")
                        .header("Authorization", "Bearer admin-token")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(4)
    @DisplayName("DELETE - Običan korisnik nema pristup (403)")
    void whenUserDeletesPhoto_thenForbidden() throws Exception {
        // Postavljamo korisnika koji NIJE admin (npr. samo REGISTERED)
        mockFirebaseToken("user-uid", "REGISTERED", "user-token");

        mockMvc.perform(delete("/api/photos/1")
                        .header("Authorization", "Bearer user-token")
                        .with(csrf()))
                .andExpect(status().isForbidden()); // Ovdje mora biti 403
    }

    @Test
    @Order(5)
    @DisplayName("POST Upload - Ispravan multipart zahtjev (200)")
    void whenUploadCorrect_thenOk() throws Exception {
        mockFirebaseToken("user-uid", "REGISTERED", "valid-token");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE, // Postavlja content-type unutar multipart-a
                "test-content".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/photos/upload")
                        .file(file)
                        .param("description", "Ovo je opis")
                        .param("hashtags", "#tag")
                        .param("isPrivate", "false")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.MULTIPART_FORM_DATA) // Eksplicitno navedi multipart
                        .with(csrf()))
                .andExpect(status().isOk());
    }
    @Test
    @Order(6)
    @DisplayName("POST Upload - Nedostaje parametar (400)")
    void whenUploadMissingParam_thenBadRequest() throws Exception {
        mockFirebaseToken("user-uid", "REGISTERED", "valid-token");

        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/photos/upload")
                        .file(file)
                        .header("Authorization", "Bearer valid-token")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}