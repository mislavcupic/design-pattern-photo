package hr.algebra.nrako.photoapp_backend.service;

import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.observer.PhotoUploadObserver;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PhotoService {

    void registerObserver(PhotoUploadObserver observer);
    void unregisterObserver(PhotoUploadObserver observer);
    void notifyObservers(String userId);

    CompletableFuture<Photo> uploadPhoto(MultipartFile file, String description, String hashtags, String uid, String fileUrl, Boolean isPrivate);
    CompletableFuture<List<Photo>> getPhotosByUser(String requestedUid);
    CompletableFuture<Photo> getPhotoDetails(Long id);
    CompletableFuture<List<Photo>> getLast10Photos();
    CompletableFuture<Photo> updatePhotoMetadata(Long photoId, String newDescription, String newHashtags, String uid, Boolean isPrivate, boolean isAdmin);
    CompletableFuture<Void> deletePhoto(Long photoId, String requesterUid, boolean isAdmin);
    CompletableFuture<List<Photo>> getAllPhotos();
    CompletableFuture<Boolean> togglePhotoPrivacy(String photoId, String authenticatedFirebaseUid);
    boolean isOwner(String photoId, String firebaseUid);
    String getFormatFromBytes(byte[] imageBytes) throws IOException;

    /**
     * Downloads a photo with applied filters and resizing.
     *
     * @param photoId The ID of the photo to download.
     * @param requesterUid The UID of the user requesting the download.
     * @param isAdmin True if the requester is an admin.
     * @param maxWidth The maximum width for the downloaded image (optional).
     * @param maxHeight The maximum height for the downloaded image (optional).
     * @param outputFormat The desired output format (e.g., "jpeg", "png") (optional).
     * @param applySepia True if sepia filter should be applied.
     * @param applyBlur True if blur filter should be applied.
     * @return A CompletableFuture containing the byte array of the processed image.
     * @throws PhotoNotFoundException if the photo with the given ID is not found.
     * @throws SecurityException if the requester is not authorized to access a private photo.
     * @throws RuntimeException for other unexpected errors during download or processing.
     */
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

    /**
     * Searches for photos based on a search term, optionally filtered by uploader UID.
     * Handles privacy rules to return only public photos to unauthorized users,
     * or all photos if the requester is the owner or an admin.
     *
     * @param searchTerm The term to search for in photo descriptions or hashtags. Can be null or empty.
     * @param uploadedByUid Optional: The UID of the user who uploaded the photos, to filter results. Can be null.
     * @param requesterUid The UID of the currently authenticated user making the request.
     * @param isAdmin True if the requester has administrative privileges.
     * @return A CompletableFuture containing a list of Photo objects that match the criteria.
     */
    CompletableFuture<List<Photo>> searchPhotos(
            String searchTerm,
            String uploadedByUid,
            String requesterUid,
            boolean isAdmin
    );

    // --- Custom Exceptions ---

    /**
     * Custom exception to indicate that a photo was not found.
     * This helps the controller map to a 404 Not Found HTTP status.
     */
    class PhotoNotFoundException extends RuntimeException {
        public PhotoNotFoundException(String message) {
            super(message);
        }
    }
}