package hr.algebra.nrako.photoapp_backend.integration;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.repository.PhotoRepository;
import hr.algebra.nrako.photoapp_backend.repository.UserPackageDataRepository;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // Osiguraj da application-test.properties ima portove 8085 i 8083
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PhotoControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private Firestore firestore; // Firestore Emulator (Port 8085)
    @Autowired private PhotoRepository photoRepository;
    @Autowired private UserPackageDataRepository userPackageDataRepository;

    // KORISTIMO STVARNE BEANOVE (bez @MockitoBean)
    @Autowired private Storage googleCloudStorage; // Storage Emulator (Port 8083)
    @Autowired private Bucket storageBucket;

    // FirebaseAuth ostavljamo kao mock jer je komplicirano generirati ispravne tokene za Auth emulator u testu
    @MockitoBean private FirebaseAuth firebaseAuth;

    private final String testUserUid = "test_user_123";

    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(firebaseAuth);

        // 1. Čišćenje FIRESTORE kolekcije (Ovo potvrđuje da je Firestore emulator aktivan)
        firestore.collection("photos").get().get().getDocuments()
                .forEach(doc -> doc.getReference().delete());

        // 2. Čišćenje STORAGE emulatora
        // Koristimo try-catch jer emulator baca 501 ako bucket ne postoji ili nije inicijaliziran
        try {
            Bucket bucket = googleCloudStorage.get(storageBucket.getName());
            if (bucket != null) {
                var blobs = bucket.list();
                if (blobs != null) {
                    blobs.iterateAll().forEach(Blob::delete);
                }
            }
        } catch (Exception e) {
            // Ignoriramo ako bucket još ne postoji u emulatoru
            System.out.println("Storage cleanup skipped: " + e.getMessage());
        }
    }

    private void mockAuth(String uid, String role) throws Exception {
        FirebaseToken token = Mockito.mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getClaims()).thenReturn(Map.of("userType", role));
        when(firebaseAuth.verifyIdToken(anyString())).thenReturn(token);
    }

    @Test @Order(1)
    void testGetAllPublicPhotos() throws Exception {
        savePhotoToEmulator(100L, "Javna", testUserUid, false);
        TimeUnit.MILLISECONDS.sleep(300); // Kratka pauza za Firestore asinkronost
        MvcResult result = mockMvc.perform(get("/api/photos/public")).andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Javna"));
    }

    @Test @Order(2)
    void testGetLast10Photos() throws Exception {
        for (long i = 1; i <= 11; i++) {
            savePhotoToEmulator(i, "S" + i, testUserUid, false);
        }
        TimeUnit.MILLISECONDS.sleep(300);
        mockMvc.perform(get("/api/photos/last10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }
    @Test
    @Order(3)
    @DisplayName("POST /api/photos/upload - Upload nove fotografije")
    void testUploadPhoto() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-upload.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test-image-content".getBytes()
        );

        // ✅ Koristi .with(user()) direktno u request-u
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file)
                        .param("description", "Test upload iz integracijskog testa")
                        .param("hashtags", "#test #integration")
                        .param("isPrivate", "false")
                        .with(csrf())
                        .with(user(testUserUid).authorities(new SimpleGrantedAuthority("REGISTERED"))))  // ✅ OVO!
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value(containsString("test-upload.jpg")))
                .andExpect(jsonPath("$.description").value("Test upload iz integracijskog testa"))
                .andExpect(jsonPath("$.hashtags").value("#test #integration"))
                .andExpect(jsonPath("$.uploadedBy").value(testUserUid))  // ✅ Sad će biti test_user_123
                .andExpect(jsonPath("$.fileUrl").exists())
                .andExpect(jsonPath("$.isPrivate").value(false));

        System.out.println("✅ Test 3: Upload fotografije - PROŠAO");
    }
    @Test @Order(4)
    void testTogglePrivacy() throws Exception {
        savePhotoToEmulator(555L, "D", testUserUid, false);
        mockAuth(testUserUid, "REGISTERED");

        mockMvc.perform(put("/api/photos/555/toggle-privacy")
                        .header("Authorization", "Bearer token")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test @Order(5)
    void testDeletePhoto() throws Exception {
        savePhotoToEmulator(777L, "D", testUserUid, false);
        mockAuth("admin", "ADMIN");

        mockMvc.perform(delete("/api/photos/777")
                        .header("Authorization", "Bearer token")
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        // Provjera da je obrisano iz Firestorea
        var doc = firestore.collection("photos").document("777").get().get();
        Assertions.assertFalse(doc.exists(), "Dokument je trebao biti obrisan iz Firestorea!");
    }

    private void savePhotoToEmulator(Long id, String desc, String uid, boolean isPrivate) {
        Photo p = new Photo();
        p.setId(id);
        p.setDescription(desc);
        p.setUploadedBy(uid);
        p.setIsPrivate(isPrivate);
        p.setFilename(id + ".jpg");
        p.setUploadDate(Timestamp.now());
        photoRepository.savePhoto(p);
    }
}