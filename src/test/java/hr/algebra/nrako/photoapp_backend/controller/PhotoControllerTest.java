package hr.algebra.nrako.photoapp_backend.controller;

import hr.algebra.nrako.photoapp_backend.asynchelper.AsyncHelperPhoto;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.dto.PhotoDto;
import hr.algebra.nrako.photoapp_backend.service.PhotoService; // Potrebno za @PreAuthorize u testovima, iako mockirano
import hr.algebra.nrako.photoapp_backend.service.StorageService; // Potrebno za injekciju, iako mockirano
import hr.algebra.nrako.photoapp_backend.util.FirebaseTokenUtils; // Potrebno za injekciju, iako mockirano
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // ADD THIS LINE
class PhotoControllerTest {

    @Mock
    private FirebaseTokenUtils firebaseTokenUtils;
    @Mock
    private AsyncHelperPhoto asyncHelperPhoto;
    @Mock
    private StorageService storageService;
    @Mock
    private PhotoService photoService; // Mockiran, iako je njegov isOwner metoda korištena u @PreAuthorize


    @InjectMocks
    private PhotoController photoController;

    private Photo mockPhoto;
    private PhotoDto mockPhotoDto;
    private MockMultipartFile mockFile;
    private String testUid = "testUid123";

    @BeforeEach
    void setUp() {
        // Inicijalizacija mock Photo i PhotoDto objekata
        mockPhoto = new Photo(1L, "test_image.jpg", "Test description", "tag1,tag2", testUid,
                com.google.cloud.Timestamp.of(Timestamp.from(Instant.now())), "http://example.com/test_image.jpg", false);
        mockPhotoDto = new PhotoDto(1L, "test_image.jpg", "Test description", "tag1,tag2", testUid,
                mockPhoto.getUploadDate().toString(), "http://example.com/test_image.jpg", false);

        // Inicijalizacija mock MultipartFile
        mockFile = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "some image content".getBytes()
        );

        // Resetiranje svih mockova prije svakog testa
        reset(firebaseTokenUtils, asyncHelperPhoto, storageService, photoService);
    }

    // --- Pomoćna metoda za mockiranje SecurityContextHolder-a ---
    // NAPOMENA: Za unit testiranje Spring Security konteksta, Mockito.mockStatic je standardna praksa.
    // Bez ovoga, metode koje dohvaćaju Authentication object (gotovo sve u PhotoControlleru)
    // ne bi se mogle testirati u izolaciji.
    private MockedStatic<SecurityContextHolder> mockSecurityContext(String uid, String... roles) {
        MockedStatic<SecurityContextHolder> mockedStatic = mockStatic(SecurityContextHolder.class);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(uid);

        // --- CRITICAL CHANGE: Aggressive Raw Type Cast ---
        List<GrantedAuthority> authoritiesList = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // This line uses a raw cast to force compilation.
        // The compiler will issue an 'unchecked cast' warning here, but it should compile.
        when(authentication.getAuthorities()).thenReturn((Collection) authoritiesList);
        // ----------------------------------------------------

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        return mockedStatic;
    }
    // --- Testovi za /public endpoint ---
    @Test
    void getAllPublicPhotos_shouldReturnListOfPhotoDtos() throws ExecutionException, InterruptedException {
        // Arrange
        List<Photo> publicPhotos = Collections.singletonList(mockPhoto);
        when(photoService.getAllPhotos()).thenReturn(CompletableFuture.completedFuture(publicPhotos));

        // Act
        ResponseEntity<List<PhotoDto>> response = photoController.getAllPublicPhotos().get(); // .get() jer vraća CompletableFuture

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
        assertEquals(1, response.getBody().size());
        assertEquals(mockPhotoDto.getId(), response.getBody().get(0).getId());
        verify(photoService, times(1)).getAllPhotos();
    }

    @Test
    void getAllPublicPhotos_shouldReturnInternalServerError_whenServiceFails() throws ExecutionException, InterruptedException {
        // Arrange
        when(photoService.getAllPhotos()).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Database error")));

        // Act
        ResponseEntity<List<PhotoDto>> response = photoController.getAllPublicPhotos().get();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getBody());
        verify(photoService, times(1)).getAllPhotos();
    }

    // --- Testovi za /upload endpoint ---
    @Test
    void uploadPhoto_shouldReturnOk_whenUploadIsSuccessful() throws ExecutionException, InterruptedException {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedStatic = mockSecurityContext(testUid, "ROLE_REGISTERED")) {
            when(asyncHelperPhoto.uploadPhoto(any(MultipartFile.class), anyString(), anyString(), anyString(), any(), anyBoolean()))
                    .thenReturn(mockPhoto);

            // Act
            ResponseEntity<PhotoDto> response = photoController.uploadPhoto(mockFile, "New Description", "newtag", false);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(mockPhotoDto.getId(), response.getBody().getId());
            verify(asyncHelperPhoto, times(1)).uploadPhoto(mockFile, "New Description", "newtag", testUid, null, false);
        }
    }

    @Test
    void uploadPhoto_shouldThrowException_whenAsyncHelperThrowsException() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedStatic = mockSecurityContext(testUid, "ROLE_REGISTERED")) {
            try {
                when(asyncHelperPhoto.uploadPhoto(any(), anyString(), anyString(), anyString(), any(), anyBoolean()))
                        .thenThrow(new RuntimeException("Simulated upload error"));
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Act & Assert
            assertThrows(RuntimeException.class, () -> photoController.uploadPhoto(mockFile, "Desc", "tag", false));
            try {
                verify(asyncHelperPhoto, times(1)).uploadPhoto(any(), anyString(), anyString(), anyString(), any(), anyBoolean());
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // --- Testovi za /user endpoint (getPhotosForCurrentUser) ---
    @Test
    void getPhotosForCurrentUser_shouldReturnListOfUserPhotos() throws ExecutionException, InterruptedException {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedStatic = mockSecurityContext(testUid, "ROLE_REGISTERED")) {
            List<Photo> userPhotos = Collections.singletonList(mockPhoto);
            when(asyncHelperPhoto.getPhotosByUser(testUid)).thenReturn(userPhotos);

            // Act
            List<PhotoDto> response = photoController.getPhotosForCurrentUser();

            // Assert
            assertNotNull(response);
            assertFalse(response.isEmpty());
            assertEquals(1, response.size());
            assertEquals(mockPhotoDto.getId(), response.get(0).getId());
            verify(asyncHelperPhoto, times(1)).getPhotosByUser(testUid);
        }
    }

    @Test
    void getPhotosForCurrentUser_shouldReturnEmptyList_whenNoPhotosFound() throws ExecutionException, InterruptedException {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedStatic = mockSecurityContext(testUid, "ROLE_REGISTERED")) {
            when(asyncHelperPhoto.getPhotosByUser(testUid)).thenReturn(Collections.emptyList());

            // Act
            List<PhotoDto> response = photoController.getPhotosForCurrentUser();

            // Assert
            assertNotNull(response);
            assertTrue(response.isEmpty());
            verify(asyncHelperPhoto, times(1)).getPhotosByUser(testUid);
        }
    }

    // --- Testovi za /{id} endpoint (getPhotoDetails) ---
    @Test
    void getPhotoDetails_shouldReturnPhotoDto_whenPhotoExists() throws ExecutionException, InterruptedException {
        // Arrange
        when(asyncHelperPhoto.getPhotoDetails(anyLong())).thenReturn(mockPhoto);

        // Act
        ResponseEntity<PhotoDto> response = photoController.getPhotoDetails(1L);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockPhotoDto.getId(), response.getBody().getId());
        verify(asyncHelperPhoto, times(1)).getPhotoDetails(1L);
    }

    @Test
    void getPhotoDetails_shouldThrowException_whenPhotoNotFound() throws ExecutionException, InterruptedException {
        // Arrange
        when(asyncHelperPhoto.getPhotoDetails(anyLong())).thenThrow(new RuntimeException("Photo not found"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> photoController.getPhotoDetails(999L));
        verify(asyncHelperPhoto, times(1)).getPhotoDetails(999L);
    }

    // --- Testovi za PUT /{id} (updateMetadata) ---
    @Test
    void updateMetadata_shouldReturnOk_whenUpdateSuccessful() throws ExecutionException, InterruptedException {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedStatic = mockSecurityContext(testUid, "ROLE_REGISTERED")) {
            when(asyncHelperPhoto.updatePhotoMetadata(anyLong(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn(mockPhoto);

            // Act
            ResponseEntity<PhotoDto> response = photoController.updateMetadata(1L, "Updated Desc", "tag3", true);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(mockPhotoDto.getId(), response.getBody().getId());
            verify(asyncHelperPhoto, times(1)).updatePhotoMetadata(1L, "Updated Desc", "tag3", testUid, true, false); // false za isAdmin
        }
    }

    @Test
    void updateMetadata_shouldReturnForbidden_whenUnauthorizedExceptionOccurs() throws ExecutionException, InterruptedException {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedStatic = mockSecurityContext(testUid, "ROLE_REGISTERED")) {
            when(asyncHelperPhoto.updatePhotoMetadata(anyLong(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenThrow(new ExecutionException("Unauthorized", new RuntimeException("Unauthorized: User not allowed")));

            // Act
            ResponseEntity<PhotoDto> response = photoController.updateMetadata(1L, "Desc", "tag", true);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertNull(response.getBody()); // Tijelo bi trebalo biti null jer je build() pozvan
            verify(asyncHelperPhoto, times(1)).updatePhotoMetadata(anyLong(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean());
        }
    }
}