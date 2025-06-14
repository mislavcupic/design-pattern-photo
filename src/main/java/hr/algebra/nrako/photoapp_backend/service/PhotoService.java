package hr.algebra.nrako.photoapp_backend.service;

import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.observer.PhotoUploadSubject;
import hr.algebra.nrako.photoapp_backend.repository.PhotoRepository;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PhotoService extends PhotoUploadSubject {

    CompletableFuture<Photo> uploadPhoto(MultipartFile file, String description, String hashtags, String uid, String fileUrl, Boolean isPrivate);

    CompletableFuture<List<Photo>> getPhotosByUser(String uid);

    CompletableFuture<Photo> getPhotoDetails(Long id);

    CompletableFuture<List<Photo>> getLast10Photos();

    CompletableFuture<Photo> updatePhotoMetadata(Long photoId, String newDescription, String newHashtags, String uid, Boolean isPrivate);

    CompletableFuture<Void> deletePhoto(Long photoId, String requesterUid, boolean isAdmin);

    CompletableFuture<Void> deleteAllPhotos(String uid);

    CompletableFuture<List<Photo>> getAllPhotos();
  // ⬅️ Dodajte ovu liniju
    CompletableFuture<Boolean> togglePhotoPrivacy(String photoId, String authenticatedFirebaseUid);

    boolean isOwner(String photoId, String firebaseUid); // OVO JE BILA GREŠKA BEZ CompletableFuture!
    CompletableFuture<byte[]> downloadPhotoWithFilters(
            Long photoId,
            String requesterUid,
            boolean isAdmin,
            Integer maxWidth,
            Integer maxHeight,
            String outputFormat,
            boolean applySepia,
            boolean applyBlur
    );

    CompletableFuture<List<Photo>> searchPhotos(
            String searchTerm,
            String uploadedByUid,
            String requesterUid,
            boolean isAdmin
    );

}
