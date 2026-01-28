package hr.algebra.nrako.photoapp_backend.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.observer.PhotoUploadObserver;
import hr.algebra.nrako.photoapp_backend.observer.PhotoUploadSubject;
import hr.algebra.nrako.photoapp_backend.repository.PhotoRepository;
import hr.algebra.nrako.photoapp_backend.util.ImageProcessorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class PhotoServiceImpl implements PhotoService, PhotoUploadSubject {
    private static final Logger logger = LoggerFactory.getLogger(PhotoServiceImpl.class);

    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final ImageProcessingService imageProcessingService;
    private final ImageProcessorBuilder imageProcessorBuilder;

    private final List<PhotoUploadObserver> observers = new CopyOnWriteArrayList<>();
    private boolean isAdmin;

    public PhotoServiceImpl(PhotoRepository photoRepository, StorageService storageService,
                            ImageProcessingService imageProcessingService,
                            ImageProcessorBuilder imageProcessorBuilder) {
        this.photoRepository = photoRepository;
        this.storageService = storageService;
        this.imageProcessingService = imageProcessingService;
        this.imageProcessorBuilder = imageProcessorBuilder;
    }

    private String getAuthUid() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(auth -> !auth.getName().equals("anonymousUser"))
                .map(Authentication::getName)
                .orElse(null);
    }

    private boolean isCurrentUserAdmin() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
                .orElse(false);
    }

    private String generatePublicUrl(String path) {
        try {
            String encoded = URLEncoder.encode(path, StandardCharsets.UTF_8.toString()).replace("+", "%20");
            return String.format("https://firebasestorage.googleapis.com/v0/b/photoapp-c195d/o/%s?alt=media", encoded);
        } catch (Exception e) { return null; }
    }

    @Override
    public void registerObserver(PhotoUploadObserver observer) {
        this.observers.add(observer);
    }

    @Override
    public void unregisterObserver(PhotoUploadObserver observer) {
        this.observers.remove(observer);
    }

    @Override
    public void notifyObservers(String userId) {
        for (PhotoUploadObserver observer : observers) {
            observer.onPhotoUploaded(userId);
        }
    }

/*
    // funkcionalno programiranje - NOVO
    @Override
    public void notifyObservers(String userId) {
        observers.forEach(observer -> observer.onPhotoUploaded(userId));
    }
*/

    @Override
    public CompletableFuture<Photo> uploadPhoto(MultipartFile file, String description, String hashtags, String uid, String fileUrl, Boolean isPrivate) {
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        return CompletableFuture.supplyAsync(() -> {
            try {
                String fullPath = "userPhotos/" + uid + "/" + System.currentTimeMillis() + "_" + originalFilename;
                try (InputStream is = file.getInputStream()) {
                    storageService.uploadPhotoFromStream(is, fullPath, contentType);
                }
                return fullPath;
            } catch (IOException e) {
                throw new RuntimeException("Greška pri uploadu: " + e.getMessage());
            }
        }).thenApply(fullPath -> {
            Photo photo = new Photo();
            photo.setId(System.currentTimeMillis());
            photo.setFilename(fullPath);
            photo.setDescription(description);
            photo.setHashtags(hashtags);
            photo.setUploadedBy(uid);
            photo.setUploadDate(Timestamp.now());
            photo.setIsPrivate(isPrivate);

            photo.setFileUrl(generatePublicUrl(fullPath));
            photoRepository.savePhoto(photo);
            notifyObservers(uid);

            return photo;
        });
    }

    @Override
    @Async
    public CompletableFuture<List<Photo>> getPhotosByUser(String requestedUid) {
        String authenticatedUid = getAuthUid();
        boolean isAdminUser = isCurrentUserAdmin();

        return photoRepository.getPhotosByUser(requestedUid)
                .thenApply(photos -> photos.stream()
                        .filter(p -> requestedUid.equals(authenticatedUid) || isAdminUser || !p.getIsPrivate())
                        .collect(Collectors.toList())
                );
    }

    @Override
    public CompletableFuture<Photo> getPhotoDetails(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            Photo photo = photoRepository.getPhotoById(id.toString());
            if (photo == null) return null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String uid = (auth != null) ? (String) auth.getPrincipal() : null;
            boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (photo.getIsPrivate() && !(photo.getUploadedBy().equals(uid) || admin)) throw new RuntimeException("Unauthorized");
            return photo;
        });
    }

/*
    // funkcionalno programiranje - NOVO
    @Override
    public CompletableFuture<Photo> getPhotoDetails(Long id) {
        String authUid = getAuthUid();
        boolean isAdminUser = isCurrentUserAdmin();

        return CompletableFuture.supplyAsync(() -> photoRepository.getPhotoById(id.toString()))
                .thenApply(Optional::ofNullable)
                .thenApply(opt -> opt.filter(p -> !p.getIsPrivate() || p.getUploadedBy().equals(authUid) || isAdminUser)
                        .orElseThrow(() -> new RuntimeException("Unauthorized or Photo not found")));
    }
*/

    @Override
    public CompletableFuture<List<Photo>> getLast10Photos() {
        return photoRepository.findTop10ByOrderByUploadDateDesc()
                .thenApply(photos -> photos.stream()
                        .filter(photo -> !photo.getIsPrivate())
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Photo> updatePhotoMetadata(Long photoId, String newDescription, String newHashtags, String uid, Boolean isPrivate, boolean isAdmin) {
        return CompletableFuture.supplyAsync(() -> {
            Photo photo = photoRepository.getPhotoById(photoId.toString());
            if (photo == null) throw new RuntimeException("Not found");
            if (!photo.getUploadedBy().equals(uid) && !isAdmin) throw new RuntimeException("Unauthorized");
            photo.setDescription(newDescription);
            photo.setIsPrivate(isPrivate);
            photoRepository.savePhoto(photo);
            return photo;
        });
    }

/*
    // funkcionalno programiranje - NOVO
    @Override
    public CompletableFuture<Photo> updatePhotoMetadata(Long photoId, String newDesc, String newTags, String uid, Boolean isPriv, boolean isAdminUser) {
        return CompletableFuture.supplyAsync(() -> photoRepository.getPhotoById(photoId.toString()))
                .thenApply(Optional::ofNullable)
                .thenApply(opt -> opt.filter(p -> p.getUploadedBy().equals(uid) || isAdminUser)
                        .map(p -> {
                            p.setDescription(newDesc);
                            p.setHashtags(parseTagsFunctional(newTags));
                            p.setIsPrivate(isPriv);
                            photoRepository.savePhoto(p);
                            return p;
                        })
                        .orElseThrow(() -> new RuntimeException("Update failed: Unauthorized or Not Found")));
    }

    private String parseTagsFunctional(String tags) {
        return Optional.ofNullable(tags)
                .map(t -> Arrays.stream(t.split(" "))
                        .filter(s -> !s.isBlank())
                        .map(String::trim)
                        .collect(Collectors.joining(", ")))
                .orElse("");
    }
*/

    @Override
    public CompletableFuture<Void> deletePhoto(Long photoId, String requesterUid, boolean isAdmin) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<QueryDocumentSnapshot> docs = photoRepository.getPhotoDocumentByLongId(photoId);
                if (!docs.isEmpty()) {
                    Photo p = docs.get(0).toObject(Photo.class);
                    if (p.getUploadedBy().equals(requesterUid) || isAdmin) {
                        storageService.deletePhoto(p.getFilename());
                        photoRepository.deletePhotoById(photoId.toString());
                    } else {
                        throw new RuntimeException("Unauthorized");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

/*
    // funkcionalno programiranje - NOVO
    @Override
    public CompletableFuture<Void> deletePhoto(Long photoId, String requesterUid, boolean isAdminUser) {
        return CompletableFuture.runAsync(() ->
                Optional.ofNullable(photoRepository.getPhotoById(photoId.toString()))
                        .filter(p -> p.getUploadedBy().equals(requesterUid) || isAdminUser)
                        .ifPresentOrElse(p -> {
                            storageService.deletePhoto(p.getFilename());
                            photoRepository.deletePhotoById(photoId.toString());
                        }, () -> { throw new RuntimeException("Delete failed: Unauthorized or Not Found"); })
        );
    }
*/

    @Override
    public CompletableFuture<List<Photo>> getAllPhotos() {
        return photoRepository.getAllPhotos()
                .thenApply(photos -> photos.stream()
                        .filter(photo -> !photo.getIsPrivate())
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Boolean> togglePhotoPrivacy(String photoId, String authenticatedFirebaseUid) {
        return CompletableFuture.supplyAsync(() -> photoRepository.getPhotoById(photoId))
                .thenApply(Optional::ofNullable)
                .thenApply(opt -> opt.filter(p -> p.getUploadedBy().equals(authenticatedFirebaseUid) || this.isAdmin))
                .thenApply(opt -> opt.map(p -> {
                    p.setIsPrivate(!p.getIsPrivate());
                    photoRepository.savePhoto(p);
                    return true;
                }).orElse(false));
    }

/*
    // funkcionalno programiranje - NOVO
    @Override
    public CompletableFuture<Boolean> togglePhotoPrivacy(String photoId, String authUid) {
        String currentUid = getAuthUid();
        boolean isAdminUser = isCurrentUserAdmin();

        return CompletableFuture.supplyAsync(() -> photoRepository.getPhotoById(photoId))
                .thenApply(Optional::ofNullable)
                .thenApply(opt -> opt.filter(p -> p.getUploadedBy().equals(currentUid) || isAdminUser)
                        .map(p -> {
                            p.setIsPrivate(!p.getIsPrivate());
                            photoRepository.savePhoto(p);
                            return true;
                        }).orElse(false));
    }
*/

    @Override
    public boolean isOwner(String photoId, String firebaseUid) {
        return Optional.ofNullable(photoRepository.getPhotoById(photoId))
                .map(p -> p.getUploadedBy().equals(firebaseUid))
                .orElse(false);
    }

    @Override
    public String getFormatFromBytes(byte[] imageBytes) throws IOException {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            return imageProcessingService.getOriginalImageFormat(is);
        }
    }

    @Override
    public CompletableFuture<byte[]> downloadPhotoWithFilters(Long photoId, String requesterUid, boolean isAdmin,
                                                              Integer w, Integer h, String fmt, boolean sepia, boolean blur) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Photo photo = photoRepository.getPhotoById(photoId.toString());
                if (photo == null) throw new SecurityException("Photo not found");
                if (photo.getIsPrivate() && !photo.getUploadedBy().equals(requesterUid) && !isAdmin) throw new SecurityException("Unauthorized");

                // Prilagođeno novom StorageService-u (streaming umjesto AsBytes)
                try (InputStream gcsStream = storageService.downloadPhotoAsStream(photo.getFilename());
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    imageProcessingService.processImage(gcsStream, baos, w, h, fmt, sepia, blur);
                    return baos.toByteArray();
                }
            } catch (Exception e) {
                throw new RuntimeException("Greška u nefunkcionalnom bloku: " + e.getMessage());
            }
        });
    }

/*
    // funkcionalno programiranje - NOVO
    @Override
    public CompletableFuture<byte[]> downloadPhotoWithFilters(
            Long photoId, String requesterUid, boolean isAdminUser,
            Integer w, Integer h, String fmt, boolean sepia, boolean blur) {

        return CompletableFuture.supplyAsync(() -> photoRepository.getPhotoById(photoId.toString()))
                .thenApply(Optional::ofNullable)
                .thenApply(opt -> opt.filter(p -> !p.getIsPrivate() || p.getUploadedBy().equals(requesterUid) || isAdminUser)
                        .orElseThrow(() -> new SecurityException("Unauthorized or Photo not found")))
                .thenApply(p -> {
                    try (InputStream gcsStream = storageService.downloadPhotoAsStream(p.getFilename());
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        imageProcessingService.processImage(gcsStream, baos, w, h, fmt, sepia, blur);
                        return baos.toByteArray();
                    } catch (IOException e) {
                        throw new RuntimeException("Streaming error", e);
                    }
                });
    }
*/

    @Override
    public CompletableFuture<List<Photo>> searchPhotos(String searchTerm, String uploadedByUid, String requesterUid, boolean isAdmin) {
        return photoRepository.searchPhotos(searchTerm, uploadedByUid)
                .thenApply(photos -> photos.stream()
                        .filter(p -> !p.getIsPrivate() || p.getUploadedBy().equals(requesterUid) || isAdmin)
                        .collect(Collectors.toList()));
    }
}