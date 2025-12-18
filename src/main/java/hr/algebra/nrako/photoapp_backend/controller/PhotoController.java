package hr.algebra.nrako.photoapp_backend.controller;

import hr.algebra.nrako.photoapp_backend.asynchelper.AsyncHelperPhoto;
import hr.algebra.nrako.photoapp_backend.dto.PhotoDto;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.filter.FirebaseAuthenticationFilter;
import hr.algebra.nrako.photoapp_backend.service.PhotoService;
import hr.algebra.nrako.photoapp_backend.service.StorageService;
import hr.algebra.nrako.photoapp_backend.util.FirebaseTokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final FirebaseTokenUtils firebaseTokenUtils;
    private final AsyncHelperPhoto asyncHelperPhoto;
    private final StorageService storageService;
    private final PhotoService photoService;
    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    public PhotoController(FirebaseTokenUtils firebaseTokenUtils, PhotoService photoService, AsyncHelperPhoto asyncHelperPhoto, StorageService storageService) {
        this.firebaseTokenUtils = firebaseTokenUtils;
        this.asyncHelperPhoto = asyncHelperPhoto;
        this.storageService = storageService;
        this.photoService = photoService;
    }

    // ============================================
    // HELPER METODA ZA EKSTRAKCIJU UID-a
    // ============================================
    private String extractUserUid(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // Za @WithMockUser testove - principal je UserDetails
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }

        // Za Firebase authentication - principal je String
        if (principal instanceof String) {
            return (String) principal;
        }

        // Fallback - pokušaj getName()
        return authentication.getName();
    }

    @GetMapping("/public")
    public CompletableFuture<ResponseEntity<List<PhotoDto>>> getAllPublicPhotos() {
        return photoService.getAllPhotos()
                .thenApply(photos -> ResponseEntity.ok(photos.stream()
                        .map(this::mapToDto)
                        .collect(Collectors.toList())))
                .exceptionally(ex -> {
                    logger.error("Error retrieving all public photos: {}", ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
                });
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('REGISTERED') or hasRole('ADMIN')")
    public ResponseEntity<PhotoDto> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description,
            @RequestParam("hashtags") String hashtags,
            @RequestParam("isPrivate") Boolean isPrivate) throws ExecutionException, InterruptedException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String uid = extractUserUid(authentication); // ✅ ZAMIJENJEN CAST

        logger.info("Handling photo upload request for UID: {}", uid);

        Photo photo = asyncHelperPhoto.uploadPhoto(file, description, hashtags, uid, null, isPrivate);

        return ResponseEntity.ok(mapToDto(photo));
    }

    @GetMapping("/user")
    @PreAuthorize("hasRole('REGISTERED') or hasRole('ADMIN')")
    public List<PhotoDto> getPhotosForCurrentUser() throws ExecutionException, InterruptedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUid = extractUserUid(authentication); // ✅ ZAMIJENJEN CAST

        logger.info("Dohvaćanje fotografija za CURRENT korisnika (UID: {}).", authenticatedUid);

        List<Photo> photos = asyncHelperPhoto.getPhotosByUser(authenticatedUid);
        return photos.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhotoDto> getPhotoDetails(@PathVariable Long id) throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(mapToDto(asyncHelperPhoto.getPhotoDetails(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
    public ResponseEntity<PhotoDto> updateMetadata(
            @PathVariable Long id,
            @RequestParam String description,
            @RequestParam String hashtags,
            @RequestParam Boolean isPrivate
    ) throws ExecutionException, InterruptedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String uid = extractUserUid(authentication); // ✅ ZAMIJENJEN CAST

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        logger.info("Ažuriranje fotografije ID: {} za UID: {}, isAdmin: {}", id, uid, isAdmin);

        try {
            Photo updatedPhoto = asyncHelperPhoto.updatePhotoMetadata(id, description, hashtags, uid, isPrivate, isAdmin);
            return ResponseEntity.ok(mapToDto(updatedPhoto));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("Greška prilikom ažuriranja fotografije ID: {}: {}", id, cause != null ? cause.getMessage() : e.getMessage(), cause);

            if (cause != null) {
                if (cause.getMessage().contains("Unauthorized")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                if (cause.getMessage().contains("Photo not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                }
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Ažuriranje fotografije ID: {} prekinuto: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id) throws ExecutionException, InterruptedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String uid = extractUserUid(authentication); // ✅ ZAMIJENJEN CAST
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        asyncHelperPhoto.deletePhoto(id, uid, isAdmin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/last10")
    public List<PhotoDto> getLast10Photos() throws ExecutionException, InterruptedException {
        List<Photo> photos = asyncHelperPhoto.getLast10Photos();
        return photos.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/user/{uid}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
    public List<PhotoDto> getPhotosByUser(@PathVariable String uid) throws ExecutionException, InterruptedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUid = extractUserUid(authentication); // ✅ ZAMIJENJEN CAST

        logger.info("Pokušaj dohvata fotografija za korisnika {}. Autentificirani UID: {}.", uid, authenticatedUid);

        List<Photo> photos = asyncHelperPhoto.getPhotosByUser(uid);
        return photos.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private PhotoDto mapToDto(Photo photo) {
        PhotoDto photoDto = new PhotoDto();
        photoDto.setId(photo.getId());
        photoDto.setFilename(photo.getFilename());
        photoDto.setDescription(photo.getDescription());

        if (photo.getHashtags() != null) {
            photoDto.setHashtags(photo.getHashtags());
        } else {
            photoDto.setHashtags(String.valueOf(Collections.emptyList()));
        }

        photoDto.setUploadedBy(photo.getUploadedBy());
        photoDto.setUploadDate(photo.getUploadDate() != null ? photo.getUploadDate().toString() : null);
        photoDto.setFileUrl(photo.getFileUrl());
        photoDto.setIsPrivate(photo.getIsPrivate());
        return photoDto;
    }

    @PutMapping("/{photoId}/toggle-privacy")
    @PreAuthorize("isAuthenticated() or hasRole('ADMIN') or hasRole('REGISTERED')")
    public ResponseEntity<String> togglePhotoPrivacy(@PathVariable String photoId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String firebaseUid = extractUserUid(authentication); // ✅ ZAMIJENJEN CAST

        logger.info("Toggle privacy request for photo ID: {} by user: {}", photoId, firebaseUid);

        try {
            Boolean success = asyncHelperPhoto.togglePhotoPrivacy(photoId, firebaseUid);
            if (success) {
                return ResponseEntity.ok("Status privatnosti uspješno promijenjen.");
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Nema dozvolu za promjenu statusa privatnosti ili fotografija nije pronađena.");
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Greška pri promjeni statusa privatnosti za ID {}: {}", photoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Greška pri promjeni statusa privatnosti: " + e.getMessage());
        }
    }

    @GetMapping("/{photoId}/download")
    public CompletableFuture<ResponseEntity<byte[]>> downloadPhotoWithFilters(
            @PathVariable Long photoId,
            @RequestParam(required = false) Integer maxWidth,
            @RequestParam(required = false) Integer maxHeight,
            @RequestParam(required = false) String outputFormat,
            @RequestParam(defaultValue = "false") boolean sepia,
            @RequestParam(defaultValue = "false") boolean blur
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String requesterUid = extractUserUid(authentication);

        if (requesterUid == null) {
            logger.warn("Neautentificirani korisnik u downloadPhotoWithFilters.");
            requesterUid = "anonymous_user_id";
        }

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return asyncHelperPhoto.downloadPhotoWithFilters(photoId, requesterUid, isAdmin, maxWidth, maxHeight, outputFormat, sepia, blur)
                .thenApply(imageBytes -> {
                    if (imageBytes == null || imageBytes.length == 0) {
                        logger.warn("Fotografija s ID {} nije pronađena ili je obrađena u prazne bajtove.", photoId);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new byte[0]);
                    }

                    MediaType contentType;
                    String finalOutputFormat = outputFormat;

                    if (finalOutputFormat == null || finalOutputFormat.isEmpty()) {
                        try {
                            String detectedFormat = asyncHelperPhoto.getFormatFromBytes(imageBytes);
                            if (detectedFormat != null) {
                                finalOutputFormat = detectedFormat;
                                logger.debug("Detektirani format iz bajtova: {}", detectedFormat);
                            } else {
                                finalOutputFormat = "jpeg";
                                logger.warn("Nije moguće detektirati format slike iz bajtova, koristim default: {}", finalOutputFormat);
                            }
                        } catch (IOException e) {
                            logger.warn("Greška prilikom detekcije formata slike iz bajtova za ID {}: {}", photoId, e.getMessage());
                            finalOutputFormat = "jpeg";
                        }
                    }

                    switch (finalOutputFormat.toLowerCase()) {
                        case "png":
                            contentType = MediaType.IMAGE_PNG;
                            break;
                        case "gif":
                            contentType = MediaType.IMAGE_GIF;
                            break;
                        case "bmp":
                            contentType = MediaType.parseMediaType("image/bmp");
                            break;
                        case "avif":
                            contentType = MediaType.parseMediaType("image/avif");
                            break;
                        case "webp":
                            contentType = MediaType.parseMediaType("image/webp");
                            break;
                        case "tif":
                        case "tiff":
                            contentType = MediaType.parseMediaType("image/tiff");
                            break;
                        case "svg":
                            contentType = MediaType.parseMediaType("image/svg+xml");
                            break;
                        default:
                            contentType = MediaType.IMAGE_JPEG;
                            break;
                    }

                    String filename = "processed_photo." + finalOutputFormat.toLowerCase();

                    return ResponseEntity.ok()
                            .contentType(contentType)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                            .body(imageBytes);
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause();
                    logger.error("Greška prilikom preuzimanja/obrade fotografije ID {}: {}", photoId, cause != null ? cause.getMessage() : ex.getMessage(), ex);

                    if (cause instanceof PhotoService.PhotoNotFoundException) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new byte[0]);
                    }
                    if (cause instanceof SecurityException) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new byte[0]);
                    }
                    if (cause instanceof IOException && cause.getMessage() != null && cause.getMessage().contains("Failed to read image bytes")) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
                    }
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
                });
    }

    @GetMapping("/search")
    public CompletableFuture<ResponseEntity<List<PhotoDto>>> searchPhotos(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String uploadedBy,
            HttpServletRequest request
    ) {
        String requesterUid = firebaseTokenUtils.extractUidFromRequest(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return asyncHelperPhoto.searchPhotos(searchTerm, uploadedBy, requesterUid, isAdmin)
                .thenApply(photos -> ResponseEntity.ok(photos.stream().map(this::mapToDto).collect(Collectors.toList())))
                .exceptionally(ex -> {
                    logger.error("Error searching photos for term '{}': {}", searchTerm, ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
                });
    }
}