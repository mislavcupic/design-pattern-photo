package hr.algebra.nrako.photoapp_backend.controller;

import hr.algebra.nrako.photoapp_backend.dto.PhotoDto;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.service.PhotoService;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final FirebaseTokenUtils firebaseTokenUtils;
    private final PhotoService photoService;
    private static final Logger logger = LoggerFactory.getLogger(PhotoController.class);

    public PhotoController(FirebaseTokenUtils firebaseTokenUtils, PhotoService photoService) {
        this.firebaseTokenUtils = firebaseTokenUtils;
        this.photoService = photoService;
    }

    // 1. HELPER: Ekstrakcija UID-a
    private String extractUserUid(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) return ((UserDetails) principal).getUsername();
        if (principal instanceof String) return (String) principal;
        return authentication.getName();
    }

    // 2. HELPER: Mapiranje u DTO
    private PhotoDto mapToDto(Photo photo) {
        PhotoDto dto = new PhotoDto();
        dto.setId(photo.getId());
        dto.setFilename(photo.getFilename());
        dto.setDescription(photo.getDescription());
        dto.setHashtags(photo.getHashtags() != null ? photo.getHashtags() : "[]");
        dto.setUploadedBy(photo.getUploadedBy());
        dto.setUploadDate(photo.getUploadDate() != null ? photo.getUploadDate().toString() : null);
        dto.setFileUrl(photo.getFileUrl());
        dto.setIsPrivate(photo.getIsPrivate());
        return dto;
    }

    // 3. Get All Public Photos
    @GetMapping("/public")
    public CompletableFuture<ResponseEntity<List<PhotoDto>>> getAllPublicPhotos() {
        return photoService.getAllPhotos()
                .thenApply(photos -> ResponseEntity.ok(photos.stream().map(this::mapToDto).collect(Collectors.toList())))
                .exceptionally(ex -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    // 4. Upload Photo
    @PostMapping("/upload")
    @PreAuthorize("hasRole('REGISTERED') or hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<PhotoDto>> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description,
            @RequestParam("hashtags") String hashtags,
            @RequestParam("isPrivate") Boolean isPrivate) {

        if (file == null || file.isEmpty()) return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());

        String uid = extractUserUid(SecurityContextHolder.getContext().getAuthentication());
        return photoService.uploadPhoto(file, description, hashtags, uid, null, isPrivate)
                .thenApply(photo -> ResponseEntity.ok(mapToDto(photo)))
                .exceptionally(ex -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    // 5. Get Photos for Current User
    @GetMapping("/user")
    @PreAuthorize("hasRole('REGISTERED') or hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<List<PhotoDto>>> getPhotosForCurrentUser() {
        String uid = extractUserUid(SecurityContextHolder.getContext().getAuthentication());
        return photoService.getPhotosByUser(uid)
                .thenApply(photos -> ResponseEntity.ok(photos.stream().map(this::mapToDto).collect(Collectors.toList())));
    }

    // 6. Get Photo Details
    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<PhotoDto>> getPhotoDetails(@PathVariable Long id) {
        return photoService.getPhotoDetails(id)
                .thenApply(photo -> ResponseEntity.ok(mapToDto(photo)));
    }

    // 7. Update Metadata
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
    public CompletableFuture<ResponseEntity<PhotoDto>> updateMetadata(
            @PathVariable Long id, @RequestParam String description,
            @RequestParam String hashtags, @RequestParam Boolean isPrivate) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String uid = extractUserUid(auth);
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return photoService.updatePhotoMetadata(id, description, hashtags, uid, isPrivate, isAdmin)
                .thenApply(photo -> ResponseEntity.ok(mapToDto(photo)))
                .exceptionally(ex -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    // 8. Delete Photo
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
    public CompletableFuture<ResponseEntity<Void>> deletePhoto(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String uid = extractUserUid(auth);
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return photoService.deletePhoto(id, uid, isAdmin)
                .thenApply(v -> ResponseEntity.noContent().<Void>build());
    }

    // 9. Get Last 10 Photos
    @GetMapping("/last10")
    public CompletableFuture<ResponseEntity<List<PhotoDto>>> getLast10Photos() {
        return photoService.getLast10Photos()
                .thenApply(photos -> ResponseEntity.ok(photos.stream().map(this::mapToDto).collect(Collectors.toList())));
    }

    // 10. Get Photos By User UID
    @GetMapping("/user/{uid}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
    public CompletableFuture<ResponseEntity<List<PhotoDto>>> getPhotosByUser(@PathVariable String uid) {
        return photoService.getPhotosByUser(uid)
                .thenApply(photos -> ResponseEntity.ok(photos.stream().map(this::mapToDto).collect(Collectors.toList())));
    }

    // 11. Toggle Privacy
    @PutMapping("/{photoId}/toggle-privacy")
    @PreAuthorize("isAuthenticated() or hasRole('ADMIN') or hasRole('REGISTERED')")
    public CompletableFuture<ResponseEntity<String>> togglePhotoPrivacy(@PathVariable String photoId) {
        String uid = extractUserUid(SecurityContextHolder.getContext().getAuthentication());
        return photoService.togglePhotoPrivacy(photoId, uid)
                .thenApply(success -> success ? ResponseEntity.ok("Status promijenjen") : ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    // 12. Search Photos
    @GetMapping("/search")
    public CompletableFuture<ResponseEntity<List<PhotoDto>>> searchPhotos(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String uploadedBy, HttpServletRequest request) {

        String requesterUid = firebaseTokenUtils.extractUidFromRequest(request);
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return photoService.searchPhotos(searchTerm, uploadedBy, requesterUid, isAdmin)
                .thenApply(photos -> ResponseEntity.ok(photos.stream().map(this::mapToDto).collect(Collectors.toList())));
    }

    // 13. Download with Filters (Kompleksna obrada)
    @GetMapping("/{photoId}/download")
    public CompletableFuture<ResponseEntity<byte[]>> downloadPhotoWithFilters(
            @PathVariable Long photoId, @RequestParam(required = false) Integer maxWidth,
            @RequestParam(required = false) Integer maxHeight, @RequestParam(required = false) String outputFormat,
            @RequestParam(defaultValue = "false") boolean sepia, @RequestParam(defaultValue = "false") boolean blur) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String requesterUid = extractUserUid(auth) != null ? extractUserUid(auth) : "anonymous";
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return photoService.downloadPhotoWithFilters(photoId, requesterUid, isAdmin, maxWidth, maxHeight, outputFormat, sepia, blur)
                .thenApply(imageBytes -> {
                    if (imageBytes == null || imageBytes.length == 0) return ResponseEntity.notFound().build();

                    String format = (outputFormat != null) ? outputFormat.toLowerCase() : "jpeg";
                    MediaType type = switch (format) {
                        case "png" -> MediaType.IMAGE_PNG;
                        case "webp" -> MediaType.parseMediaType("image/webp");
                        default -> MediaType.IMAGE_JPEG;
                    };

                    return ResponseEntity.ok()
                            .contentType(type)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"photo." + format + "\"")
                            .body(imageBytes);
                });
    }
}