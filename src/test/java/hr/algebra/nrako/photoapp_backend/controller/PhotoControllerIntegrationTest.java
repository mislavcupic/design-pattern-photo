package hr.algebra.nrako.photoapp_backend.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Bucket;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.repository.PhotoRepository;
import hr.algebra.nrako.photoapp_backend.repository.UserPackageDataRepository;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
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
        // 1. Kreiraj file, ali pazi na redoslijed parametara
        MockMultipartFile file = new MockMultipartFile(
                "file",           // ime parametra u kontroleru
                "test.jpg",       // original filename
                "image/jpeg",     // content type (MORA BITI STRING "image/jpeg")
                "image content".getBytes()
        );

        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file)
                        // 2. DODAJ OVO RUČNO - prisili multipart/form-data
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .param("description", "D")
                        .param("uid", testUserUid)
                        .param("hashtags", "#test #fun")
                        .param("isPrivate", "false")
                        .with(csrf())
                        .with(user(testUserUid).roles("USER"))) // Koristi testUserUid
                .andExpect(status().isOk());
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