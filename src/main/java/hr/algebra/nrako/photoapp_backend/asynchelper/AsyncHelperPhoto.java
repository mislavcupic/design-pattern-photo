package hr.algebra.nrako.photoapp_backend.asynchelper;

import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.service.ImageProcessingService;
import hr.algebra.nrako.photoapp_backend.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class AsyncHelperPhoto {

    private final ImageProcessingService imageProcessingService;
    private final PhotoService photoService;
    private final Logger log = LoggerFactory.getLogger(AsyncHelperPhoto.class); // Mora biti final prema tvojoj slici

    public Photo uploadPhoto(MultipartFile file, String description, String hashtags, String uid, String fileUrl, Boolean isPrivate) throws ExecutionException, InterruptedException {
        return photoService.uploadPhoto(file, description, hashtags, uid, fileUrl, isPrivate).get();
    }

    public List<Photo> getPhotosByUser(String uid) throws ExecutionException, InterruptedException {
        return photoService.getPhotosByUser(uid).get();
    }

    public Photo getPhotoDetails(Long id) throws ExecutionException, InterruptedException {
        return photoService.getPhotoDetails(id).get();
    }

    public List<Photo> getLast10Photos() throws ExecutionException, InterruptedException {
        return photoService.getLast10Photos().get();
    }

    public Photo updatePhotoMetadata(Long photoId, String newDescription, String newHashtags, String uid, Boolean isPrivate, boolean isAdmin) throws ExecutionException, InterruptedException {
        return photoService.updatePhotoMetadata(photoId, newDescription, newHashtags, uid, isPrivate, isAdmin).get();
    }

    public void deletePhoto(Long photoId, String requesterUid, boolean isAdmin) throws ExecutionException, InterruptedException {
        photoService.deletePhoto(photoId, requesterUid, isAdmin).get();
    }

    public String getFormatFromBytes(byte[] imageBytes) throws IOException {
        return photoService.getFormatFromBytes(imageBytes);
    }

    public List<Photo> getAllPhotos() throws ExecutionException, InterruptedException {
        return photoService.getAllPhotos().get();
    }

    public Boolean togglePhotoPrivacy(String photoId, String authenticatedFirebaseUid) throws ExecutionException, InterruptedException {
        return photoService.togglePhotoPrivacy(photoId, authenticatedFirebaseUid).get();
    }

    // --- RJEŠAVA GREŠKU IZ KONTROLERA (Slika image_cdecb8.jpg) ---
    public CompletableFuture<List<Photo>> searchPhotos(String searchTerm, String uploadedBy, String requesterUid, boolean isAdmin) {
        return photoService.searchPhotos(searchTerm, uploadedBy, requesterUid, isAdmin);
    }

    public void downloadAndProcessToStream(
            Long photoId,
            String requesterUid,
            boolean isAdmin,
            Integer maxWidth,
            Integer maxHeight,
            String outputFormat,
            boolean applySepia,
            boolean applyBlur,
            java.io.OutputStream outputStream
    ) throws ExecutionException, InterruptedException {
        // Unutarnji .get() osigurava da dretva čeka završetak obrade
        this.downloadPhotoWithFiltersOptimized(
                photoId, requesterUid, isAdmin,
                maxWidth, maxHeight, outputFormat,
                applySepia, applyBlur, outputStream
        ).get();
    }

    public CompletableFuture<Void> downloadPhotoWithFiltersOptimized(
            Long photoId,
            String requesterUid,
            boolean isAdmin,
            Integer maxWidth,
            Integer maxHeight,
            String outputFormat,
            boolean applySepia,
            boolean applyBlur,
            java.io.OutputStream responseOutputStream
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] originalBytes = photoService.downloadPhotoWithFilters(
                        photoId, requesterUid, isAdmin, null, null, null, false, false
                ).get();

                if (originalBytes == null || originalBytes.length == 0) return;

                try (java.io.InputStream inputStream = new java.io.ByteArrayInputStream(originalBytes)) {
                    // RJEŠAVA GREŠKE IZ HELPERA (Slika image_ce6534.jpg)
                    this.imageProcessingService.processImage(
                            inputStream,
                            responseOutputStream,
                            maxWidth,
                            maxHeight,
                            outputFormat,
                            applySepia, // Mora se podudarati s nazivom u parametrima gore
                            applyBlur   // Mora se podudarati s nazivom u parametrima gore
                    );
                }
            } catch (Exception e) {
                log.error("Greška pri procesiranju: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}