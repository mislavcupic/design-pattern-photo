package hr.algebra.nrako.photoapp_backend.service;

import com.google.cloud.Timestamp;
import hr.algebra.nrako.photoapp_backend.domain.ImageProcessingOptions;
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

import java.io.IOException;
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

    // Koristimo CopyOnWriteArrayList radi thread-safe funkcionalne iteracije
    private final List<PhotoUploadObserver> observers = new CopyOnWriteArrayList<>();

    public PhotoServiceImpl(PhotoRepository photoRepository, StorageService storageService,
                            ImageProcessingService imageProcessingService,
                            ImageProcessorBuilder imageProcessorBuilder) {
        this.photoRepository = photoRepository;
        this.storageService = storageService;
        this.imageProcessingService = imageProcessingService;
        this.imageProcessorBuilder = imageProcessorBuilder;
    }

    // --- POMOĆNE METODE ZA FUNKCIONALNI PRISTUP ---

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

    @Override
    public void registerObserver(PhotoUploadObserver observer) {
        this.observers.add(observer);
    }

    @Override
    public void unregisterObserver(PhotoUploadObserver observer) {
        this.observers.remove(observer);
    }

    // 1. METODA: notifyObservers
    // prije promjene u funkcionalno programiranje
    /*
    @Override
    public void notifyObservers(String userId) {
        for (PhotoUploadObserver observer : observers) {
            observer.onPhotoUploaded(userId);
        }
    }
    */
    // funkcionalno programiranje
    @Override
    public void notifyObservers(String userId) {
        observers.forEach(observer -> observer.onPhotoUploaded(userId));
    }

    @Override
    public CompletableFuture<Photo> uploadPhoto(MultipartFile file, String description, String hashtags, String uid, String fileUrl, Boolean isPrivate) {
        return CompletableFuture.supplyAsync(() -> "userPhotos/" + uid + "/" + System.currentTimeMillis() + "_" + file.getOriginalFilename())
                .thenApply(fullPath -> {
                    storageService.uploadPhoto(file, fullPath);
                    return fullPath;
                })
                .thenApply(fullPath -> {
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

    private String generatePublicUrl(String path) {
        try {
            String encoded = URLEncoder.encode(path, StandardCharsets.UTF_8.toString()).replace("+", "%20");
            return String.format("https://firebasestorage.googleapis.com/v0/b/photoapp-c195d/o/%s?alt=media", encoded);
        } catch (Exception e) { return null; }
    }

    @Override
    @Async
    public CompletableFuture<List<Photo>> getPhotosByUser(String requestedUid) {
        String authenticatedUid = getAuthUid();
        boolean isAdmin = isCurrentUserAdmin();

        return photoRepository.getPhotosByUser(requestedUid)
                .thenApply(photos -> photos.stream()
                        .filter(p -> requestedUid.equals(authenticatedUid) || isAdmin || !p.getIsPrivate())
                        .collect(Collectors.toList())
                );
    }

    // 2. METODA: getPhotoDetails
    // prije promjene u funkcionalno programiranje
    /*
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
    */
    // funkcionalno programiranje
    @Override
    public CompletableFuture<Photo> getPhotoDetails(Long id) {
        String authUid = getAuthUid();
        boolean isAdmin = isCurrentUserAdmin();

        return CompletableFuture.supplyAsync(() -> photoRepository.getPhotoById(id.toString()))
                .thenApply(Optional::ofNullable)
                .thenApply(opt -> opt.filter(p -> !p.getIsPrivate() || p.getUploadedBy().equals(authUid) || isAdmin)
                        .orElseThrow(() -> new RuntimeException("Unauthorized or Photo not found")));
    }

    @Override
    public CompletableFuture<List<Photo>> getLast10Photos() {
        return photoRepository.findTop10ByOrderByUploadDateDesc()
                .thenApply(photos -> photos.stream()
                        .filter(photo -> !photo.getIsPrivate())
                        .collect(Collectors.toList()));
    }

    // 3. METODA: updatePhotoMetadata
    // prije promjene u funkcionalno programiranje
    /*
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
    */
    // funkcionalno programiranje
    @Override
    public CompletableFuture<Photo> updatePhotoMetadata(Long photoId, String newDesc, String newTags, String uid, Boolean isPriv, boolean isAdmin) {
        return CompletableFuture.supplyAsync(() -> photoRepository.getPhotoById(photoId.toString()))
                .thenApply(Optional::ofNullable)
                .thenApply(opt -> opt.filter(p -> p.getUploadedBy().equals(uid) || isAdmin)
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

    // 4. METODA: deletePhoto
    // prije promjene u funkcionalno programiranje
    /*
    @Override
    public CompletableFuture<Void> deletePhoto(Long photoId, String requesterUid, boolean isAdmin) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<QueryDocumentSnapshot> docs = photoRepository.getPhotoDocumentByLongId(photoId);
                // ... ostatak imperativne logike ...
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    */
    // funkcionalno programiranje
    @Override
    public CompletableFuture<Void> deletePhoto(Long photoId, String requesterUid, boolean isAdmin) {
        return CompletableFuture.runAsync(() ->
                Optional.ofNullable(photoRepository.getPhotoById(photoId.toString()))
                        .filter(p -> p.getUploadedBy().equals(requesterUid) || isAdmin)
                        .ifPresentOrElse(p -> {
                            storageService.deletePhoto(p.getFilename());
                            photoRepository.deletePhotoById(photoId.toString());
                        }, () -> { throw new RuntimeException("Delete failed: Unauthorized or Not Found"); })
        );
    }

    @Override
    public CompletableFuture<List<Photo>> getAllPhotos() {
        return photoRepository.getAllPhotos()
                .thenApply(photos -> photos.stream()
                        .filter(photo -> !photo.getIsPrivate())
                        .collect(Collectors.toList()));
    }

    // 5. METODA: togglePhotoPrivacy
    // prije promjene u funkcionalno programiranje
    /*
    @Override
    public CompletableFuture<Boolean> togglePhotoPrivacy(String photoId, String authenticatedFirebaseUid) {
        return CompletableFuture.supplyAsync(() -> {
            Photo photo = photoRepository.getPhotoById(photoId);
            if (photo == null) return false;
            if (!photo.getUploadedBy().equals(uid) && !isAdmin) return false;
            photo.setIsPrivate(!photo.getIsPrivate());
            photoRepository.savePhoto(photo);
            return true;
        });
    }
    */
    // funkcionalno programiranje
    @Override
    public CompletableFuture<Boolean> togglePhotoPrivacy(String photoId, String authUid) {
        String currentUid = getAuthUid();
        boolean isAdmin = isCurrentUserAdmin();

        return CompletableFuture.supplyAsync(() -> photoRepository.getPhotoById(photoId))
                .thenApply(Optional::ofNullable)
                .thenApply(opt -> opt.filter(p -> p.getUploadedBy().equals(currentUid) || isAdmin)
                        .map(p -> {
                            p.setIsPrivate(!p.getIsPrivate());
                            photoRepository.savePhoto(p);
                            return true;
                        }).orElse(false));
    }

    @Override
    public boolean isOwner(String photoId, String firebaseUid) {
        return Optional.ofNullable(photoRepository.getPhotoById(photoId))
                .map(p -> p.getUploadedBy().equals(firebaseUid))
                .orElse(false);
    }

    @Override
    public String getFormatFromBytes(byte[] imageBytes) throws IOException {
        return imageProcessingService.getOriginalImageFormat(imageBytes);
    }

    // 6. METODA: downloadPhotoWithFilters
    // prije promjene u funkcionalno programiranje
    /*
    @Override
    public CompletableFuture<byte[]> downloadPhotoWithFilters(...) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Photo photo = photoRepository.getPhotoById(photoId.toString());
                // ... puno if-ova, try-catch-ova i fos.write() debugginga ...
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    */
    // funkcionalno programiranje
    @Override
    public CompletableFuture<byte[]> downloadPhotoWithFilters(
            Long photoId, String requesterUid, boolean isAdmin,
            Integer w, Integer h, String fmt, boolean sepia, boolean blur) {

        return CompletableFuture.supplyAsync(() -> photoRepository.getPhotoById(photoId.toString()))
                .thenApply(Optional::ofNullable)
                .thenApply(opt -> opt.filter(p -> !p.getIsPrivate() || p.getUploadedBy().equals(requesterUid) || isAdmin)
                        .orElseThrow(() -> new SecurityException("Unauthorized or Photo not found")))
                .thenApply(p -> storageService.downloadPhotoAsBytes(p.getFilename()))
                .thenApply(bytes -> {
                    imageProcessorBuilder.reset();
                    return imageProcessorBuilder.withImageBytes(bytes).resize(w, h).format(fmt).sepia(sepia).blur(blur).build();
                })
                .thenApply(opts -> {
                    try {
                        return imageProcessingService.processImage(
                                opts.getImageBytes(), opts.getMaxWidth(), opts.getMaxHeight(),
                                opts.getOutputFormat(), opts.isApplySepia(), opts.isApplyBlur());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    public CompletableFuture<List<Photo>> searchPhotos(String searchTerm, String uploadedByUid, String requesterUid, boolean isAdmin) {
        return photoRepository.searchPhotos(searchTerm, uploadedByUid)
                .thenApply(photos -> photos.stream()
                        .filter(p -> !p.getIsPrivate() || p.getUploadedBy().equals(requesterUid) || isAdmin)
                        .collect(Collectors.toList()));
    }
}