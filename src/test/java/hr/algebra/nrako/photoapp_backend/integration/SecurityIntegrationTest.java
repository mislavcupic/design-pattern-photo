package hr.algebra.nrako.photoapp_backend.integration;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.repository.PhotoRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Security Integration Test - Autentifikacija i Autorizacija (STVARNI Storage)")
class SecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PhotoRepository photoRepository;
    @Autowired private Firestore firestore;

    // ✅ STVARNI Storage komponente - NE mockovi!
    @Autowired private Storage googleCloudStorage;
    @Autowired private Bucket storageBucket;

    // ❌ Samo FirebaseAuth je mock jer ne možemo lako generirati stvarne tokene
    @MockitoBean private FirebaseAuth firebaseAuth;

    @BeforeEach
    void setup() throws Exception {
        reset(firebaseAuth);

        System.out.println("🧹 Čišćenje prije testa...");

        // ✅ Očisti Firestore prije svakog testa
        try {
            firestore.collection("photos").get().get(5, TimeUnit.SECONDS).getDocuments()
                    .forEach(doc -> {
                        try {
                            doc.getReference().delete().get();
                        } catch (Exception e) {
                            System.err.println("⚠️ Firestore cleanup error: " + e.getMessage());
                        }
                    });
            System.out.println("✅ Firestore očišćen");
        } catch (Exception e) {
            System.err.println("❌ Firestore cleanup failed: " + e.getMessage());
            throw new RuntimeException("Firestore emulator nije dostupan! Pokreni: firebase emulators:start", e);
        }

        // ✅ Očisti Storage emulator prije svakog testa
        try {
            if (storageBucket.exists()) {
                var page = storageBucket.list();
                if (page != null) {
                    page.iterateAll().forEach(blob -> {
                        try {
                            blob.delete();
                            System.out.println("🗑️ Obrisan blob: " + blob.getName());
                        } catch (Exception e) {
                            System.err.println("⚠️ Greška pri brisanju blob-a: " + e.getMessage());
                        }
                    });
                }
                System.out.println("✅ Storage očišćen");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Storage cleanup skipped: " + e.getMessage());
        }
    }

    private void mockFirebaseToken(String uid, String userType, String tokenValue) throws FirebaseAuthException {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn(uid);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", userType);
        when(mockToken.getClaims()).thenReturn(claims);

        when(firebaseAuth.verifyIdToken(tokenValue)).thenReturn(mockToken);
    }

    @Test
    @Order(1)
    @DisplayName("GET /api/photos/public - Javni pristup bez tokena (200)")
    void whenPublicRoute_thenOk() throws Exception {
        mockMvc.perform(get("/api/photos/public"))
                .andExpect(status().isOk());

        System.out.println("✅ Test 1: Public route - PROŠAO");
    }

    @Test
    @Order(2)
    @DisplayName("PUT /api/photos/{id}/toggle-privacy - Bez tokena vraća 403")
    void whenNoToken_thenForbidden() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setUploadedBy("some-owner");
        photoRepository.savePhoto(photo);

        mockMvc.perform(put("/api/photos/1/toggle-privacy")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        System.out.println("✅ Test 2: Protected route without token - PROŠAO");
    }

    @Test
    @Order(3)
    @DisplayName("DELETE /api/photos/{id} - Admin može obrisati fotografiju (204)")
    void whenAdminDeletes_thenNoContent() throws Exception {
        Photo photo = new Photo();
        photo.setId(777L);
        photo.setUploadedBy("user-uid");
        photo.setFilename("test-photo-777.jpg");
        photoRepository.savePhoto(photo);

        mockFirebaseToken("admin-uid", "ADMIN", "admin-token");

        mockMvc.perform(delete("/api/photos/777")
                        .header("Authorization", "Bearer admin-token")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        System.out.println("✅ Test 3: Admin delete - PROŠAO");
    }

    @Test
    @Order(4)
    @DisplayName("DELETE /api/photos/{id} - Korisnik ne može brisati tuđe fotografije")
    void whenUserDeletesOthersPhoto_thenError() throws Exception {
        Photo photo = new Photo();
        photo.setId(888L);
        photo.setUploadedBy("other-user");  // ✅ Vlasnik je "other-user"
        photo.setFilename("test-photo-888.jpg");
        photoRepository.savePhoto(photo);  // ✅ Čekaj da se save završi

        mockFirebaseToken("user-uid", "REGISTERED", "user-token");  // ✅ Request od "user-uid"

        // ✅ Test OČEKUJE exception, ali MockMvc ga hvata kao ServletException
        // Umjesto status().is5xxServerError() koristi try-catch
        try {
            mockMvc.perform(delete("/api/photos/888")
                            .header("Authorization", "Bearer user-token")
                            .with(csrf()))
                    .andDo(print());

            // Ako stigne ovdje - test PADA jer nije bilo exception-a
            Assertions.fail("Očekivao sam exception, ali nije bačen!");

        } catch (Exception e) {
            // ✅ Očekujemo ServletException sa porukom "Delete failed: Unauthorized or Not Found"
            Assertions.assertTrue(
                    e.getMessage().contains("Delete failed: Unauthorized or Not Found"),
                    "Exception message treba sadržavati 'Delete failed: Unauthorized or Not Found'"
            );
            System.out.println("✅ Test 4: User cannot delete other's photo - PROŠAO");
        }
    }
    @Test
    @Order(5)
    @DisplayName("POST /api/photos/upload - Uspješan upload fotografije (200)")
    void whenUploadCorrect_thenOk() throws Exception {
        mockFirebaseToken("user-uid", "REGISTERED", "valid-token");

        // ✅ Realni JPEG header
        byte[] jpegContent = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                jpegContent);

        // ✅ Upload ide na STVARNI Storage emulator
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/photos/upload")
                        .file(file)
                        .param("description", "Test description")
                        .param("hashtags", "#test")
                        .param("isPrivate", "false")
                        .header("Authorization", "Bearer valid-token")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value(containsString("test.jpg")))
                .andExpect(jsonPath("$.uploadedBy").value("user-uid"))
                .andExpect(jsonPath("$.description").value("Test description"))
                .andExpect(jsonPath("$.hashtags").value("#test"))
                .andExpect(jsonPath("$.fileUrl").exists())
                .andExpect(jsonPath("$.isPrivate").value(false));

        // ✅ Log potvrđuje uspješan upload:
        // "✅ File uploaded successfully: userPhotos/user-uid/1767481763867_test.jpg"
        System.out.println("✅ Test 5: Successful upload - PROŠAO");
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/photos/upload - Nedostaju obavezni parametri (400)")
    void whenUploadMissingParams_thenBadRequest() throws Exception {
        mockFirebaseToken("user-uid", "REGISTERED", "valid-token");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test-content".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/photos/upload")
                        .file(file)
                        .param("hashtags", "#test")
                        .param("isPrivate", "false")
                        // ❌ Nedostaje 'description' parametar
                        .header("Authorization", "Bearer valid-token")
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        System.out.println("✅ Test 6: Missing params - PROŠAO");
    }

    @Test
    @Order(7)
    @DisplayName("DELETE /api/photos/{id} - Korisnik može obrisati svoju fotografiju (204)")
    void whenUserDeletesOwnPhoto_thenNoContent() throws Exception {
        Photo photo = new Photo();
        photo.setId(999L);
        photo.setUploadedBy("user-uid");
        photo.setFilename("user-photo-999.jpg");
        photoRepository.savePhoto(photo);

        mockFirebaseToken("user-uid", "REGISTERED", "user-token");

        mockMvc.perform(delete("/api/photos/999")
                        .header("Authorization", "Bearer user-token")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        System.out.println("✅ Test 7: User delete own photo - PROŠAO");
    }

    @Test
    @Order(8)
    @DisplayName("GET /api/photos/last10 - Dohvaća zadnjih 10 fotografija (200)")
    void whenGetLast10Photos_thenOk() throws Exception {
        mockMvc.perform(get("/api/photos/last10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        System.out.println("✅ Test 8: Last 10 photos - PROŠAO");
    }

    @Test
    @Order(9)
    @DisplayName("PUT /api/photos/{id}/toggle-privacy - Korisnik može promijeniti privatnost svoje fotografije (200)")
    void whenToggleOwnPhotoPrivacy_thenOk() throws Exception {
        Photo photo = new Photo();
        photo.setId(555L);
        photo.setUploadedBy("user-uid");
        photo.setIsPrivate(false);
        photoRepository.savePhoto(photo);

        mockFirebaseToken("user-uid", "REGISTERED", "user-token");

        mockMvc.perform(put("/api/photos/555/toggle-privacy")
                        .header("Authorization", "Bearer user-token")
                        .with(csrf()))
                .andExpect(status().isOk());

        System.out.println("✅ Test 9: Toggle own photo privacy - PROŠAO");
    }

    @Test
    @Order(10)
    @DisplayName("PUT /api/photos/{id}/toggle-privacy - Korisnik ne može promijeniti privatnost tuđe fotografije (403)")
    void whenToggleOthersPhotoPrivacy_thenForbidden() throws Exception {
        Photo photo = new Photo();
        photo.setId(666L);
        photo.setUploadedBy("other-user");
        photo.setIsPrivate(false);
        photoRepository.savePhoto(photo);

        mockFirebaseToken("user-uid", "REGISTERED", "user-token");

        mockMvc.perform(put("/api/photos/666/toggle-privacy")
                        .header("Authorization", "Bearer user-token")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        System.out.println("✅ Test 10: Cannot toggle other's photo privacy - PROŠAO");
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/photos/upload - Prazan file (očekuje grešku)")
    void whenUploadEmptyFile_thenError() throws Exception {
        mockFirebaseToken("user-uid", "REGISTERED", "valid-token");

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[0]);  // Prazan file

        // ✅ Request je SYNC - ukloni asyncStarted()
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/photos/upload")
                        .file(emptyFile)
                        .param("description", "Empty file test")
                        .param("hashtags", "#test")
                        .param("isPrivate", "false")
                        .header("Authorization", "Bearer valid-token")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().is4xxClientError());  // Očekuj 400

        System.out.println("✅ Test 11: Empty file rejected - PROŠAO");
    }
}