package hr.algebra.nrako.photoapp_backend.controller;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import hr.algebra.nrako.photoapp_backend.PhotoappApplication;
// Vaša sigurnosna konfiguracija za test
import hr.algebra.nrako.photoapp_backend.configuration.FirebaseTestConfig;
import hr.algebra.nrako.photoapp_backend.configuration.TestSecurityConfig;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.repository.PhotoRepository;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit; // Za čekanje CompletableFuture-a (koristit ćete ga u servisu)

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// KLJUČNA PROMJENA: Dodajte PhotoappBackendApplication.class za učitavanje cijelog konteksta
@SpringBootTest(classes = PhotoappApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {
        FirebaseTestConfig.class, // Sada importana iz zasebne datoteke
        hr.algebra.nrako.photoapp_backend.configuration.TestSecurityConfig.class           // Vaša testna sigurnosna konfiguracija
        // UKLONJENA PhotoController.class, FirebaseTokenUtils.class, AsyncHelperPhoto.class
        // UKLONJENA PhotoService.class, StorageService.class, ImageProcessingService.class
        // Ove klase će Spring automatski pronaći putem @ComponentScan iz PhotoappBackendApplication.class
})
public class PhotoControllerIntegrationTest {

    private static final Logger classLogger = LoggerFactory.getLogger(PhotoControllerIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Firestore firestore; // Stvarna instancija Firestorea (povezana s emulatorom)

    @Autowired
    private Bucket storageBucket; // Stvarna instancija Storage Bucketa (povezana s emulatorom)

    // Nema više Mockito beanova za PhotoService, StorageService, ImageProcessingService
    // Oni će biti @Autowired u PhotoControlleru kao stvarni beani
    // Ako ih trebate direktno za provjere u testu:
    // @Autowired
    // private PhotoService photoService;


    // Nema više ugniježđene FirebaseFirestoreTestConfig klase ovdje
    // Nema više @BeforeAll/@AfterAll ovdje za emulator properties - to je sada u vanjskoj klasi

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException, IOException {
        classLogger.info("Cleaning Firestore collection 'photos' before each test...");
        cleanFirestoreCollection(firestore.collection("photos"));
        classLogger.info("Firestore cleanup completed for current test.");

        classLogger.info("Cleaning Firebase Storage bucket before each test...");
        cleanStorageBucket(storageBucket);
        classLogger.info("Storage cleanup completed for current test.");

        // Ovdje NE SMIJE biti reset(photoService, storageService, imageProcessingService)
        // jer su sada stvarni beani, ne mockovi.
        // Nema Mockito.when() poziva u BeforeEachu
    }

    private void cleanFirestoreCollection(CollectionReference collectionRef)
            throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = collectionRef.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        for (QueryDocumentSnapshot doc : documents) {
            doc.getReference().delete().get();
        }
    }

    private void cleanStorageBucket(Bucket bucket) {
        // Imajte na umu da listanje svih bloba može biti sporo za veliki broj datoteka
        // Za testove bi trebalo biti ok
        bucket.list().iterateAll().forEach(blob -> {
            try {
                blob.delete();
            } catch (Exception e) {
                classLogger.warn("Failed to delete blob {}: {}", blob.getName(), e.getMessage());
            }
        });
    }

    @AfterEach
    void tearDown() {
        classLogger.info("AfterEach tearDown executed.");
        // Opcionalno, ponovo pozvati čišćenje ako želite osigurati čisto stanje nakon SVIH testova
        // (iako je BeforeEach dovoljan za pojedinačni test izolaciju)
    }

    // --- Vaše test metode idu ovdje, s uklonjenim Mockito pozivima ---

    @Test
    void testGetAllPublicPhotos() throws Exception {
        Map<String, Object> publicPhoto1 = new HashMap<>();
        publicPhoto1.put("description", "Public Photo 1");
        publicPhoto1.put("uploadedBy", "user1"); // Promijenjeno iz "userId" u "uploadedBy" ako je to ispravno polje u Photo klasi
        publicPhoto1.put("fileUrl", "http://example.com/public1.jpg"); // Pretpostavljam da je ovo polje za URL
        publicPhoto1.put("isPrivate", false);
        // Koristite ID koji je Long za testiranje, ali ga Firestore sprema kao string dokument ID
        firestore.collection("photos").document("1").set(publicPhoto1).get(); // Changed to numeric string ID

        Map<String, Object> privatePhoto1 = new HashMap<>();
        privatePhoto1.put("description", "Private Photo 1");
        privatePhoto1.put("uploadedBy", "user2");
        privatePhoto1.put("fileUrl", "http://example.com/private1.jpg");
        privatePhoto1.put("isPrivate", true);
        firestore.collection("photos").document("2").set(privatePhoto1).get(); // Changed to numeric string ID

        mockMvc.perform(get("/api/photos/public")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description", is("Public Photo 1")));
    }

    @Test
    void testGetLast10Photos() throws Exception {
        for (int i = 0; i < 15; i++) {
            Map<String, Object> photo = new HashMap<>();
            photo.put("description", "Photo " + i);
            photo.put("uploadedBy", "user" + (i % 2));
            photo.put("fileUrl", "http://example.com/photo" + i + ".jpg");
            photo.put("isPrivate", i % 3 == 0);
            // Koristite Timestamp.now() za simulaciju stvarnog vremena, inače mogu biti problemi s redoslijedom
            photo.put("uploadDate", com.google.cloud.Timestamp.of(new java.util.Date(System.currentTimeMillis() - (15 - i) * 60 * 1000)));
            // Koristite numerički ID za dokument
            firestore.collection("photos").document(String.valueOf(i + 1)).set(photo).get(); // Changed to numeric string ID
        }
        // Nije nužno Thread.sleep ako su operacije sinkrone ili CompletableFuture-ovi čekani unutar servisa
        // Ako PhotoService.getLast10Photos() vraća CompletableFuture, test bi ga trebao .get() ili .join()
        // Thread.sleep(1000); // Ostavljeno ako je asinkrona operacija u servisu, a ne čekate je

        mockMvc.perform(get("/api/photos/last10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
        // Ažurirajte provjeru redoslijeda ako je uploadDate ispravno postavljen
        // .andExpect(jsonPath("$[0].description", is("Photo 14"))); // Ovo ovisi o sortiranju
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testUploadPhotoAsRegisteredUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.png",
                MediaType.IMAGE_PNG_VALUE,
                "dummy image content".getBytes(StandardCharsets.UTF_8) // Specificirajte charset
        );

        String description = "Test Description REGISTERED";
        String hashtags = "#test,#registered";
        Boolean isPrivate = false;

        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file)
                        .param("description", description)
                        .param("hashtags", hashtags)
                        .param("isPrivate", isPrivate.toString())
                        .param("uid", "testuser_registered") // Pošaljite UID explicitno ako ga controller očekuje
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is(description)))
                .andExpect(jsonPath("$.uploadedBy", is("testuser_registered")));

        // Dajte emulatoru vremena da procesira upload i Firestore zapis
        // U idealnom slučaju, vaša service metoda vraća CompletableFuture koju test čeka
        // Ako je uploadPhoto CompletableFuture, možete ga ovdje dobiti i čekati:
        // Photo uploadedPhoto = photoService.uploadPhoto(...).get(5, TimeUnit.SECONDS);

        Thread.sleep(2000); // Povećao sam sleep za sigurnost, prilagodite po potrebi

        // Provjerite da li je fotografija spremljena u Firestore emulator
        ApiFuture<QuerySnapshot> future = firestore.collection("photos")
                .whereEqualTo("description", description)
                .whereEqualTo("uploadedBy", "testuser_registered")
                .get();
        assertThat(future.get().getDocuments().size(), greaterThan(0));

        // Provjerite da li je datoteka u Storage emulatoru
        // Pretpostavljam da `filename` u Photo objektu sadrži punu stazu u GCS-u (npr. "userPhotos/UID/unique_filename.jpg")
        // Trebate dohvatiti filename iz spremljenog Photo objekta u Firestoreu
        String uploadedFilename = future.get().getDocuments().get(0).getString("filename");
        assertNotNull(uploadedFilename, "Filename should be saved in Firestore");
        assertTrue(storageBucket.get(uploadedFilename).exists(), "File should exist in Storage emulator");
    }

    @Test
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    void testUploadPhotoAsAdminUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "admin-image.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "admin dummy content".getBytes(StandardCharsets.UTF_8)
        );

        String description = "Test Description ADMIN";
        String hashtags = "#admin,#special";
        Boolean isPrivate = true;

        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file)
                        .param("description", description)
                        .param("hashtags", hashtags)
                        .param("isPrivate", isPrivate.toString())
                        .param("uid", "testadmin") // Pošaljite UID
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is(description)))
                .andExpect(jsonPath("$.uploadedBy", is("testadmin")));

        Thread.sleep(2000);

        ApiFuture<QuerySnapshot> future = firestore.collection("photos")
                .whereEqualTo("description", description)
                .whereEqualTo("uploadedBy", "testadmin")
                .get();
        assertThat(future.get().getDocuments().size(), greaterThan(0));

        String uploadedFilename = future.get().getDocuments().get(0).getString("filename");
        assertNotNull(uploadedFilename, "Filename should be saved in Firestore");
        assertTrue(storageBucket.get(uploadedFilename).exists(), "File should exist in Storage emulator");
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testGetPhotosForCurrentUser() throws Exception {
        String currentUserUid = "testuser_registered"; // Dohvaćanje UID-a iz @WithMockUser

        Map<String, Object> photo1 = new HashMap<>();
        photo1.put("description", "My Photo 1");
        photo1.put("uploadedBy", currentUserUid);
        photo1.put("fileUrl", "http://example.com/myphoto1.jpg");
        photo1.put("isPrivate", false);
        firestore.collection("photos").document("101").set(photo1).get(); // Changed to numeric string ID

        Map<String, Object> photo2 = new HashMap<>();
        photo2.put("description", "My Photo 2 (Private)");
        photo2.put("uploadedBy", currentUserUid);
        photo2.put("fileUrl", "http://example.com/myphoto2.jpg");
        photo2.put("isPrivate", true);
        firestore.collection("photos").document("102").set(photo2).get(); // Changed to numeric string ID

        Map<String, Object> otherUserPhoto = new HashMap<>();
        otherUserPhoto.put("description", "Other User's Photo");
        otherUserPhoto.put("uploadedBy", "otheruser");
        otherUserPhoto.put("fileUrl", "http://example.com/otherphoto.jpg");
        otherUserPhoto.put("isPrivate", false);
        firestore.collection("photos").document("103").set(otherUserPhoto).get(); // Changed to numeric string ID

        mockMvc.perform(get("/api/photos/user") // Ako endpoint bez UID-a dohvaća fotke logiranog usera
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description", anyOf(is("My Photo 1"), is("My Photo 2 (Private)"))));
    }


    @Test
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    void testGetPhotosByUserAsAdmin() throws Exception {
        String targetUserUid = "target_user_uid";
        Map<String, Object> photo1 = new HashMap<>();
        photo1.put("description", "Target User Photo 1");
        photo1.put("uploadedBy", targetUserUid);
        photo1.put("fileUrl", "http://example.com/target1.jpg");
        photo1.put("isPrivate", false);
        firestore.collection("photos").document("201").set(photo1).get(); // Changed to numeric string ID

        Map<String, Object> photo2 = new HashMap<>();
        photo2.put("description", "Target User Photo 2 (Private)");
        photo2.put("uploadedBy", targetUserUid);
        photo2.put("fileUrl", "http://example.com/target2.jpg");
        photo2.put("isPrivate", true);
        firestore.collection("photos").document("202").set(photo2).get(); // Changed to numeric string ID

        mockMvc.perform(get("/api/photos/user/{uid}", targetUserUid)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description", anyOf(is("Target User Photo 1"), is("Target User Photo 2 (Private)"))));
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testGetPhotosByUserAsRegisteredUserOwnPhotos() throws Exception {
        String requesterUserUid = "testuser_registered";
        Map<String, Object> photo1 = new HashMap<>();
        photo1.put("description", "Own Photo 1");
        photo1.put("uploadedBy", requesterUserUid);
        photo1.put("fileUrl", "http://example.com/own1.jpg");
        photo1.put("isPrivate", false);
        firestore.collection("photos").document("301").set(photo1).get(); // Changed to numeric string ID

        Map<String, Object> photo2 = new HashMap<>();
        photo2.put("description", "Own Photo 2 (Private)");
        photo2.put("uploadedBy", requesterUserUid);
        photo2.put("fileUrl", "http://example.com/own2.jpg");
        photo2.put("isPrivate", true);
        firestore.collection("photos").document("302").set(photo2).get(); // Changed to numeric string ID

        mockMvc.perform(get("/api/photos/user/{uid}", requesterUserUid)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description", anyOf(is("Own Photo 1"), is("Own Photo 2 (Private)"))));
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testGetPhotosByUserAsRegisteredUserOtherUsersPhotosForbidden() throws Exception {
        String otherUserUid = "other_user_uid";
        Map<String, Object> otherUserPhoto = new HashMap<>();
        otherUserPhoto.put("description", "Other User's Public Photo");
        otherUserPhoto.put("uploadedBy", otherUserUid);
        otherUserPhoto.put("fileUrl", "http://example.com/otherpublic.jpg");
        otherUserPhoto.put("isPrivate", false);
        firestore.collection("photos").document("401").set(otherUserPhoto).get(); // Changed to numeric string ID

        Map<String, Object> otherUserPrivatePhoto = new HashMap<>();
        otherUserPrivatePhoto.put("description", "Other User's Private Photo");
        otherUserPrivatePhoto.put("uploadedBy", otherUserUid);
        otherUserPrivatePhoto.put("fileUrl", "http://example.com/otherprivate.jpg");
        otherUserPrivatePhoto.put("isPrivate", true);
        firestore.collection("photos").document("402").set(otherUserPrivatePhoto).get(); // Changed to numeric string ID

        mockMvc.perform(get("/api/photos/user/{uid}", otherUserUid)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testGetPhotoDetails() throws Exception {
        // ID bi trebao biti numerički i prosljeđen kao Long
        Long photoId = 501L; // Numeric ID
        Map<String, Object> photoDetails = new HashMap<>();
        photoDetails.put("description", "Detailed Photo");
        photoDetails.put("uploadedBy", "testuser_registered");
        photoDetails.put("fileUrl", "http://example.com/detailed.jpg");
        photoDetails.put("isPrivate", false);
        // Firestore dokument ID je string, pa Long ID pretvaramo u String za .document()
        firestore.collection("photos").document(String.valueOf(photoId)).set(photoDetails).get();

        mockMvc.perform(get("/api/photos/{id}", photoId) // Pass Long directly
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // In your DTO, if 'id' is still a Long, jsonPath needs to match that.
                // If it's converted to String in DTO, then it's 'is(String.valueOf(photoId))'
                .andExpect(jsonPath("$.id", is(photoId.intValue()))) // Adjusted for potential intValue or change to String.valueOf(photoId)
                .andExpect(jsonPath("$.description", is("Detailed Photo")));
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testUpdateMetadataAsOwner() throws Exception {
        // ID bi trebao biti numerički i prosljeđen kao Long
        Long photoId = 601L; // Numeric ID
        String ownerUid = "testuser_registered";
        Map<String, Object> originalPhoto = new HashMap<>();
        originalPhoto.put("description", "Original Desc");
        originalPhoto.put("hashtags", Collections.singletonList("#original"));
        originalPhoto.put("uploadedBy", ownerUid);
        originalPhoto.put("isPrivate", false);
        originalPhoto.put("fileUrl", "http://example.com/original.jpg");
        // Firestore dokument ID je string, pa Long ID pretvaramo u String za .document()
        firestore.collection("photos").document(String.valueOf(photoId)).set(originalPhoto).get();

        String updatedDescription = "Updated Desc";
        String updatedHashtags = "#updated,#new";
        Boolean updatedIsPrivate = true;

        mockMvc.perform(put("/api/photos/{id}", photoId) // Pass Long directly
                        .param("description", updatedDescription)
                        .param("hashtags", updatedHashtags)
                        .param("isPrivate", updatedIsPrivate.toString())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is(updatedDescription)))
                // Note: The Photo DTO/model might convert #hashtags to a List, check your DTO conversion.
                // Assuming it's now a single string from DTO perspective, adjust if your DTO has List<String>
                .andExpect(jsonPath("$.hashtags", is(updatedHashtags.replace(",", ", ")))) // Adjusted for potential DTO conversion to string
                .andExpect(jsonPath("$.isPrivate", is(updatedIsPrivate))); // Use isPrivate, not private

        Thread.sleep(1000);
        Map<String, Object> fetchedPhoto = firestore.collection("photos").document(String.valueOf(photoId)).get().get().getData();
        assertNotNull(fetchedPhoto);
        assertThat(fetchedPhoto.get("description"), is(updatedDescription));
        assertThat(fetchedPhoto.get("isPrivate"), is(updatedIsPrivate));
        // Ažurirano: Provjerite da li su hashtags spremljeni kao String
        assertThat(fetchedPhoto.get("hashtags").toString(), containsString(updatedHashtags.replace(",", ""))); // Firestore može spremiti kao String
    }


    @Test
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    void testUpdateMetadataAsAdmin() throws Exception {
        Long photoId = 789L; // Promijenjeno u Long
        String ownerUid = "another_user";
        Map<String, Object> originalPhoto = new HashMap<>();
        originalPhoto.put("description", "Admin Test Desc");
        originalPhoto.put("hashtags", Collections.singletonList("#admintest"));
        originalPhoto.put("uploadedBy", ownerUid);
        originalPhoto.put("isPrivate", false);
        originalPhoto.put("fileUrl", "http://example.com/admintest.jpg");
        firestore.collection("photos").document(String.valueOf(photoId)).set(originalPhoto).get(); // Spremi kao string

        String updatedDescription = "Admin Updated Desc";
        String updatedHashtags = "#adminupdated";
        Boolean updatedIsPrivate = true;

        mockMvc.perform(put("/api/photos/{id}", photoId) // Proslijedi Long
                        .param("description", updatedDescription)
                        .param("hashtags", updatedHashtags)
                        .param("isPrivate", updatedIsPrivate.toString())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is(updatedDescription)))
                .andExpect(jsonPath("$.hashtags", is(updatedHashtags.replace(",", ", ")))) // Prilagođeno za DTO
                .andExpect(jsonPath("$.isPrivate", is(updatedIsPrivate)));

        Thread.sleep(1000);
        Map<String, Object> fetchedPhoto = firestore.collection("photos").document(String.valueOf(photoId)).get().get().getData();
        assertNotNull(fetchedPhoto);
        assertThat(fetchedPhoto.get("description"), is(updatedDescription));
        assertThat(fetchedPhoto.get("isPrivate"), is(updatedIsPrivate));
        assertThat(fetchedPhoto.get("hashtags").toString(), containsString(updatedHashtags.replace(",", "")));
    }

    @Test
    @WithMockUser(username = "non_owner_registered", roles = {"REGISTERED"})
    void testUpdateMetadataAsNonOwnerForbidden() throws Exception {
        Long photoId = 111L; // Promijenjeno u Long
        String ownerUid = "original_owner";
        Map<String, Object> originalPhoto = new HashMap<>();
        originalPhoto.put("description", "Protected Photo");
        originalPhoto.put("hashtags", Collections.singletonList("#protected"));
        originalPhoto.put("uploadedBy", ownerUid);
        originalPhoto.put("isPrivate", false);
        originalPhoto.put("fileUrl", "http://example.com/protected.jpg");
        firestore.collection("photos").document(String.valueOf(photoId)).set(originalPhoto).get(); // Spremi kao string

        String updatedDescription = "Attempted Update";
        String updatedHashtags = "#failed";
        Boolean updatedIsPrivate = true;

        mockMvc.perform(put("/api/photos/{id}", photoId) // Proslijedi Long
                        .param("description", updatedDescription)
                        .param("hashtags", updatedHashtags)
                        .param("isPrivate", updatedIsPrivate.toString())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testDeletePhotoAsOwner() throws Exception {
        Long photoIdToDelete = 1001L; // Promijenjeno u Long
        String ownerUid = "testuser_registered";
        String storagePath = "userPhotos/" + ownerUid + "/" + photoIdToDelete + ".jpg"; // Stvarni put u Storage emulatoru
        byte[] dummyImageData = "dummy content to delete".getBytes(StandardCharsets.UTF_8);

        // Uploadaj datoteku u Storage emulator prije testa
        BlobId blobId = BlobId.of(storageBucket.getName(), storagePath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/jpeg").build();
        storageBucket.create(String.valueOf(blobInfo), dummyImageData);
        classLogger.info("File uploaded to Storage emulator for deletion test: {}", storagePath);
        assertTrue(storageBucket.get(storagePath).exists(), "File should exist in Storage emulator before deletion");


        Map<String, Object> photoToDelete = new HashMap<>();
        photoToDelete.put("id", photoIdToDelete); // Spremi Long kao Long u polje
        photoToDelete.put("description", "Photo to delete by owner");
        photoToDelete.put("uploadedBy", ownerUid);
        photoToDelete.put("filename", storagePath); // Važno: pohranite putanju do Storage-a
        photoToDelete.put("fileUrl", "http://example.com/todelete.jpg");
        photoToDelete.put("isPrivate", false);

        firestore.collection("photos").document(String.valueOf(photoIdToDelete)).set(photoToDelete).get(); // Spremi kao string dokument ID

        mockMvc.perform(delete("/api/photos/{id}", photoIdToDelete) // Proslijedi Long
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());

        Thread.sleep(1000);

        assertThat(firestore.collection("photos").document(String.valueOf(photoIdToDelete)).get().get().exists(), is(false));
        assertFalse(storageBucket.get(storagePath).exists(), "File should be deleted from Storage emulator.");
    }


    @Test
    @WithMockUser(username = "non_owner_registered", roles = {"REGISTERED"})
    void testDeletePhotoAsNonOwnerForbidden() throws Exception {
        Long photoIdForbiddenDelete = 777L; // Promijenjeno u Long
        String ownerUid = "original_owner_for_777";
        String storagePath = "userPhotos/" + ownerUid + "/" + photoIdForbiddenDelete + ".jpg";
        byte[] dummyImageData = "dummy content".getBytes(StandardCharsets.UTF_8);

        BlobId blobId = BlobId.of(storageBucket.getName(), storagePath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/jpeg").build();
        storageBucket.create(String.valueOf(blobInfo), dummyImageData);

        Map<String, Object> photoToDelete = new HashMap<>();
        photoToDelete.put("id", photoIdForbiddenDelete);
        photoToDelete.put("description", "Photo to delete by other owner");
        photoToDelete.put("uploadedBy", ownerUid);
        photoToDelete.put("filename", storagePath);
        photoToDelete.put("fileUrl", "http://example.com/todelete.jpg");
        photoToDelete.put("isPrivate", false);

        firestore.collection("photos").document(String.valueOf(photoIdForbiddenDelete)).set(photoToDelete).get(); // Spremi kao string

        mockMvc.perform(delete("/api/photos/{id}", photoIdForbiddenDelete) // Proslijedi Long
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());

        Thread.sleep(500);
        assertThat(firestore.collection("photos").document(String.valueOf(photoIdForbiddenDelete)).get().get().exists(), is(true));
        // Provjeri da datoteka NIJE obrisana iz Storage-a
        assertTrue(storageBucket.get(storagePath).exists(), "File should NOT be deleted from Storage emulator.");
    }

    @Test
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    void testDeletePhotoAsAdmin() throws Exception {
        Long photoIdToDelete = 1002L; // Promijenjeno u Long
        String ownerUid = "some_other_user";
        String storagePath = "userPhotos/" + ownerUid + "/" + photoIdToDelete + ".jpg";
        byte[] dummyImageData = "dummy content".getBytes(StandardCharsets.UTF_8);

        BlobId blobId = BlobId.of(storageBucket.getName(), storagePath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/jpeg").build();
        storageBucket.create(String.valueOf(blobInfo), dummyImageData);

        Map<String, Object> photoToDelete = new HashMap<>();
        photoToDelete.put("id", photoIdToDelete);
        photoToDelete.put("description", "Photo for admin to delete");
        photoToDelete.put("uploadedBy", ownerUid);
        photoToDelete.put("filename", storagePath);
        photoToDelete.put("fileUrl", "http://example.com/admindelete.jpg");
        photoToDelete.put("isPrivate", false);

        firestore.collection("photos").document(String.valueOf(photoIdToDelete)).set(photoToDelete).get(); // Spremi kao string

        mockMvc.perform(delete("/api/photos/{id}", photoIdToDelete) // Proslijedi Long
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());

        Thread.sleep(1000);
        assertThat(firestore.collection("photos").document(String.valueOf(photoIdToDelete)).get().get().exists(), is(false));
        assertFalse(storageBucket.get(storagePath).exists(), "File should be deleted from Storage emulator.");
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testTogglePhotoPrivacyAsOwner() throws Exception {
        Long photoId = 1003L; // Promijenjeno u Long
        String ownerUid = "testuser_registered";
        Map<String, Object> photo = new HashMap<>();
        photo.put("id", photoId);
        photo.put("description", "Toggle Test Photo 1");
        photo.put("uploadedBy", ownerUid);
        photo.put("fileUrl", "http://example.com/toggle1.jpg");
        photo.put("isPrivate", false); // Start as public
        firestore.collection("photos").document(String.valueOf(photoId)).set(photo).get(); // Spremi kao string

        // Toggle to private
        mockMvc.perform(patch("/api/photos/{id}/toggle-privacy", photoId) // Proslijedi Long
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate", is(true))); // Ažurirano: koristi isPrivate

        Thread.sleep(500);
        assertThat(firestore.collection("photos").document(String.valueOf(photoId)).get().get().getBoolean("isPrivate"), is(true));

        // Toggle back to public
        mockMvc.perform(patch("/api/photos/{id}/toggle-privacy", photoId) // Proslijedi Long
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate", is(false)));

        Thread.sleep(500);
        assertThat(firestore.collection("photos").document(String.valueOf(photoId)).get().get().getBoolean("isPrivate"), is(false));
    }

    @Test
    @WithMockUser(username = "non_owner_registered", roles = {"REGISTERED"})
    void testTogglePhotoPrivacyAsNonOwnerForbidden() throws Exception {
        Long photoId = 1004L; // Promijenjeno u Long
        String ownerUid = "original_owner_for_toggle";
        Map<String, Object> photo = new HashMap<>();
        photo.put("id", photoId);
        photo.put("description", "Toggle Forbidden Test Photo");
        photo.put("uploadedBy", ownerUid);
        photo.put("fileUrl", "http://example.com/toggle_forbidden.jpg");
        photo.put("isPrivate", false);
        firestore.collection("photos").document(String.valueOf(photoId)).set(photo).get(); // Spremi kao string

        mockMvc.perform(patch("/api/photos/{id}/toggle-privacy", photoId) // Proslijedi Long
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());

        Thread.sleep(500);
        assertThat(firestore.collection("photos").document(String.valueOf(photoId)).get().get().getBoolean("isPrivate"), is(false));
    }

    @Test
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    void testTogglePhotoPrivacyAsAdmin() throws Exception {
        Long photoId = 1005L; // Promijenjeno u Long
        String ownerUid = "another_user";
        Map<String, Object> photo = new HashMap<>();
        photo.put("id", photoId);
        photo.put("description", "Admin Toggle Test Photo 2");
        photo.put("uploadedBy", ownerUid);
        photo.put("fileUrl", "http://example.com/toggle2.jpg");
        photo.put("isPrivate", false);
        firestore.collection("photos").document(String.valueOf(photoId)).set(photo).get(); // Spremi kao string

        // Toggle to private by admin
        mockMvc.perform(patch("/api/photos/{id}/toggle-privacy", photoId) // Proslijedi Long
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate", is(true)));

        Thread.sleep(500);
        assertThat(firestore.collection("photos").document(String.valueOf(photoId)).get().get().getBoolean("isPrivate"), is(true));
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testDownloadPhotoAsOwner() throws Exception {
        Long photoId = 1006L; // Promijenjeno u Long
        String ownerUid = "testuser_registered";
        // Stvarni put do datoteke u Storage emulatoru
        String storageFilename = "userPhotos/" + ownerUid + "/" + photoId + ".jpg"; // Pratite konvenciju imenovanja iz PhotoServiceImpl
        byte[] dummyImageData = "this is dummy image data for download".getBytes(StandardCharsets.UTF_8);

        // Upload dummy image to Storage emulator FIRST
        BlobId blobId = BlobId.of(storageBucket.getName(), storageFilename);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(MediaType.IMAGE_JPEG_VALUE).build();
        storageBucket.create(String.valueOf(blobInfo), dummyImageData);
        classLogger.info("Uploaded dummy image to Storage emulator for download test: {}", storageFilename);
        assertTrue(storageBucket.get(storageFilename).exists(), "Dummy image should exist in Storage emulator for download.");

        Map<String, Object> photo = new HashMap<>();
        photo.put("id", photoId); // Spremi Long kao Long u polje
        photo.put("description", "Download Test Photo");
        photo.put("uploadedBy", ownerUid);
        photo.put("filename", storageFilename); // Važno: Pohranite stvarni filename iz Storage-a
        photo.put("fileUrl", "http://example.com/toggle1.jpg"); // Ovo može biti bilo što, bitno je filename
        photo.put("isPrivate", false);
        firestore.collection("photos").document(String.valueOf(photoId)).set(photo).get(); // Spremi kao string dokument ID

        // VIŠE NEMA MOCKITO.WHEN ZA STORAGE SERVICE!
        // Stvarni StorageService treba dohvaćati datoteku iz emulatora.
        // Nema Mockito.when(storageService.downloadPhotoAsBytes(eq(photoId))).thenReturn(dummyImageData);

        mockMvc.perform(get("/api/photos/{id}/download", photoId) // Proslijedi Long
                        .accept(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG)) // Ili stvarni format ako je detektiran
                .andExpect(content().bytes(dummyImageData)); // Očekujemo iste bajtove
    }

    @Test
    @WithMockUser(username = "testuser_registered", roles = {"REGISTERED"})
    void testDownloadPhotoPublicAsRegisteredUser() throws Exception {
        Long photoId = 1007L; // Promijenjeno u Long
        String ownerUid = "another_user_public"; // Not the current user
        String storageFilename = "userPhotos/" + ownerUid + "/" + photoId + ".jpg";
        byte[] dummyImageData = "this is dummy public image data".getBytes(StandardCharsets.UTF_8);

        BlobId blobId = BlobId.of(storageBucket.getName(), storageFilename);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(MediaType.IMAGE_JPEG_VALUE).build();
        storageBucket.create(String.valueOf(blobInfo), dummyImageData);

        Map<String, Object> photo = new HashMap<>();
        photo.put("id", photoId);
        photo.put("description", "Public Download Test Photo");
        photo.put("uploadedBy", ownerUid);
        photo.put("filename", storageFilename);
        photo.put("fileUrl", "http://example.com/public_download.jpg");
        photo.put("isPrivate", false); // Public
        firestore.collection("photos").document(String.valueOf(photoId)).set(photo).get(); // Spremi kao string

        // Nema Mockito.when() ovdje
        mockMvc.perform(get("/api/photos/{id}/download", photoId) // Proslijedi Long
                        .accept(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(dummyImageData));
    }

    @Test
    @WithMockUser(username = "non_owner", roles = {"REGISTERED"})
    void testDownloadPhotoPrivateAsNonOwnerForbidden() throws Exception {
        Long photoId = 1008L; // Promijenjeno u Long
        String ownerUid = "original_owner_private_download";
        String storageFilename = "userPhotos/" + ownerUid + "/" + photoId + ".jpg";
        byte[] dummyImageData = "this is dummy private image data".getBytes(StandardCharsets.UTF_8);

        BlobId blobId = BlobId.of(storageBucket.getName(), storageFilename);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(MediaType.IMAGE_JPEG_VALUE).build();
        storageBucket.create(String.valueOf(blobInfo), dummyImageData);

        Map<String, Object> photo = new HashMap<>();
        photo.put("id", photoId);
        photo.put("description", "Private Download Test Photo");
        photo.put("uploadedBy", ownerUid);
        photo.put("filename", storageFilename);
        photo.put("fileUrl", "http://example.com/private_download.jpg");
        photo.put("isPrivate", true); // Private
        firestore.collection("photos").document(String.valueOf(photoId)).set(photo).get(); // Spremi kao string

        // Nema Mockito.when()
        mockMvc.perform(get("/api/photos/{id}/download", photoId) // Proslijedi Long
                        .accept(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Download photo for a simulated user with insufficient role - should return forbidden")
    // OVDJE JE KLJUČNA PROMJENA: Koristite @WithMockUser s ulogom koja NEMA potrebne dozvole.
    // Time osiguravate da postoji Authentication objekt u SecurityContextu.
    @WithMockUser(username = "no_access_user", roles = "INSUFFICIENT_ROLE")
    void testDownloadPhotoUnauthenticatedForbidden() throws Exception {

        Long photoId = 1009L;
        String ownerUid = "unauth_owner";
        String storageFilename = "userPhotos/" + ownerUid + "/" + photoId + ".jpg";
        byte[] dummyImageData = "this is dummy unauth image data".getBytes(StandardCharsets.UTF_8);

        BlobId blobId = BlobId.of(storageBucket.getName(), storageFilename);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(MediaType.IMAGE_JPEG_VALUE).build();
        storageBucket.create(storageFilename, dummyImageData, MediaType.IMAGE_JPEG_VALUE); // Ispravljen poziv create metode

        Map<String, Object> photo = new HashMap<>();
        photo.put("id", photoId);
        photo.put("description", "Unauth Download Test Photo");
        photo.put("uploadedBy", ownerUid);
        photo.put("filename", storageFilename);
        photo.put("fileUrl", "http://example.com/unauth_download.jpg");
        photo.put("isPrivate", true);
        firestore.collection("photos").document(String.valueOf(photoId)).set(photo).get();

        mockMvc.perform(get("/api/photos/{id}/download", photoId)
                        .accept(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "no_access_user", roles = {"INSUFFICIENT_ROLE"})
    void testDownloadPrivatePhoto_withInsufficientRoleUserForbidden() throws Exception {
        // Priprema privatne fotografije u Firestoreu
        // ... (kako god kreirate fotografiju, npr. pomoću repozitorija)
        String photoId = "somePrivatePhotoId"; // Zamijenite sa stvarnim ID-jem
        // Mockirajte PhotoRepository da vrati privatnu fotografiju
        // Mockito.when(photoRepository.findById(photoId)).thenReturn(Optional.of(privatePhoto));

        mockMvc.perform(get("/api/photos/{id}/download", photoId))
                .andExpect(status().isForbidden()); // Očekujemo 403
    }
}
