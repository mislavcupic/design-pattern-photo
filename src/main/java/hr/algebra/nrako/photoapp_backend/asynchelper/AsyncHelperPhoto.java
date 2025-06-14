package hr.algebra.nrako.photoapp_backend.asynchelper;

import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class AsyncHelperPhoto {
    private final PhotoService photoService;


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

    public Photo updatePhotoMetadata(Long photoId, String newDescription, String newHashtags, String uid, Boolean isPrivate) throws ExecutionException, InterruptedException {
        return photoService.updatePhotoMetadata(photoId, newDescription, newHashtags, uid, isPrivate).get();
    }

    public Void deletePhoto(Long photoId, String requesterUid, boolean isAdmin) throws ExecutionException, InterruptedException {
        return photoService.deletePhoto(photoId, requesterUid, isAdmin).get();
    }

    public Void deleteAllPhotos(String uid) throws ExecutionException, InterruptedException {
        return photoService.deleteAllPhotos(uid).get();
    }

    public List<Photo> getAllPhotos() throws ExecutionException, InterruptedException {
        return photoService.getAllPhotos().get();
    }


    public Boolean togglePhotoPrivacy(String photoId, String authenticatedFirebaseUid) throws ExecutionException, InterruptedException {
        // Calls the PhotoService's togglePhotoPrivacy method and blocks until its CompletableFuture completes.
        return photoService.togglePhotoPrivacy(photoId, authenticatedFirebaseUid).get();
    }

    // **************** KLJUČNE PROMJENE OVDJE ****************
    // Ove metode SADA vraćaju CompletableFuture, kao što PhotoService vraća.
    // Uklonjen je .get() poziv.

    public CompletableFuture<byte[]> downloadPhotoWithFilters(
            Long photoId,
            String requesterUid,
            boolean isAdmin,
            Integer maxWidth,
            Integer maxHeight,
            String outputFormat,
            boolean applySepia,
            boolean applyBlur
    ) {
        return photoService.downloadPhotoWithFilters(photoId, requesterUid, isAdmin, maxWidth, maxHeight, outputFormat, applySepia, applyBlur);
    }

    public CompletableFuture<List<Photo>> searchPhotos(
            String searchTerm,
            String uploadedByUid,
            String requesterUid,
            boolean isAdmin
    ) {
        return photoService.searchPhotos(searchTerm, uploadedByUid, requesterUid, isAdmin);
    }
}