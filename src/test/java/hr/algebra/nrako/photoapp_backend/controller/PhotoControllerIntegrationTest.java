package hr.algebra.nrako.photoapp_backend.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.BlobInfo;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PhotoControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private FirebaseAuth firebaseAuth;
    @Autowired private Firestore firestore;
    @Autowired private PhotoRepository photoRepository;
    @Autowired private UserPackageDataRepository userPackageDataRepository;
    @Autowired private Bucket storageBucket;
    @MockitoBean
    private Storage googleCloudStorage;
    private final String testUserUid = "test_user_123";

    @BeforeEach
    void setUp() throws Exception {
        firestore.collection("photos").get().get().getDocuments()
                .forEach(doc -> doc.getReference().delete());
        Mockito.reset(firebaseAuth);
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
        TimeUnit.MILLISECONDS.sleep(300);
        MvcResult result = mockMvc.perform(get("/api/photos/public")).andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$[0].description").value("Javna"));
    }

    @Test @Order(2)
    void testGetLast10Photos() throws Exception {
        for (long i = 1; i <= 11; i++) savePhotoToEmulator(i, "S"+i, testUserUid, false);
        TimeUnit.MILLISECONDS.sleep(300);
        mockMvc.perform(get("/api/photos/last10")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    @Order(3)
    void testUploadPhoto() throws Exception {
        String testUserUid = "test_user_123";

        // Priprema mockova
        Mockito.when(storageBucket.getName()).thenReturn("test-bucket");
        mockAuth(testUserUid, "REGISTERED");

        // Kreiranje datoteke s eksplicitnim MIME tipom "image/jpeg"
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-photo.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes()
        );

        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file)
                        .param("description", "Integration test description")
                        .param("hashtags", "#test")
                        .param("isPrivate", "false")
                        .with(csrf())
                        // Koristimo .authorities jer tvoj filter puni SecurityContext s "REGISTERED"
                        .with(user(testUserUid).authorities(new SimpleGrantedAuthority("REGISTERED"))))
                .andExpect(status().isOk());

        // Provjera da je servis stvarno pozvan (opcionalno, ali dobro za test)
        Mockito.verify(googleCloudStorage, Mockito.atLeastOnce()).create(
                Mockito.any(BlobInfo.class),
                Mockito.any(byte[].class)
        );
    }
    @Test @Order(4)
    void testTogglePrivacy() throws Exception {
        String testUid = "test_user_123"; // UID koji koristiš
        savePhotoToEmulator(555L, "D", testUid, false); // Spremi s tim UID-om

        mockAuth(testUid, "REGISTERED"); // Mockaj Firebase s tim UID-om

        mockMvc.perform(put("/api/photos/555/toggle-privacy")
                        .header("Authorization", "Bearer token")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test @Order(5)
    @WithMockUser(roles = "ADMIN")
    void testDeletePhoto() throws Exception {
        savePhotoToEmulator(777L, "D", testUserUid, false);
        mockAuth("admin", "ADMIN");
        mockMvc.perform(delete("/api/photos/777")
                .header("Authorization", "Bearer token")
                .with(csrf())).andExpect(status().isNoContent());
    }

    private void savePhotoToEmulator(Long id, String desc, String uid, boolean isPrivate) {
        Photo p = new Photo();
        p.setId(id); p.setDescription(desc); p.setUploadedBy(uid);
        p.setIsPrivate(isPrivate); p.setFilename(id + ".jpg");
        p.setUploadDate(Timestamp.now());
        photoRepository.savePhoto(p);
    }
}