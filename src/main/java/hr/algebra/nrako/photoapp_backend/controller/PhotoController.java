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
    private final StorageService storageService; // Injektiran, iako se direktno ne koristi u kontroleru za upload/download
    private final PhotoService photoService; // Injektiran, iako se direktno ne koristi (koristi ga asyncHelperPhoto)
    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class); // Loger

    public PhotoController(FirebaseTokenUtils firebaseTokenUtils, PhotoService photoService, AsyncHelperPhoto asyncHelperPhoto, StorageService storageService) {
        this.firebaseTokenUtils = firebaseTokenUtils;
        this.asyncHelperPhoto = asyncHelperPhoto;
        this.storageService = storageService;
        this.photoService = photoService;
    }
    @GetMapping("/public") // Ovaj endpoint će vraćati samo javne fotografije
    public CompletableFuture<ResponseEntity<List<PhotoDto>>> getAllPublicPhotos() {
        // photoService.getAllPhotos() već sadrži logiku za filtriranje samo javnih fotografija
        // i vraća CompletableFuture<List<Photo>>.
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
        String uid = (String) authentication.getPrincipal();

        logger.info("Handling photo upload request for UID: {}", uid);

        Photo photo = asyncHelperPhoto.uploadPhoto(file, description, hashtags, uid, null, isPrivate);

        return ResponseEntity.ok(mapToDto(photo));
    }
    @GetMapping("/user") // Endpoint za CURRENT korisnika
    @PreAuthorize("hasRole('REGISTERED') or hasRole('ADMIN')")
    public List<PhotoDto> getPhotosForCurrentUser() throws ExecutionException, InterruptedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUid = (String) authentication.getPrincipal(); // UID trenutno ulogiranog korisnika
        // boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); // OVA LINIJA VIŠE NE TREBA OVDJE

        logger.info("Dohvaćanje fotografija za CURRENT korisnika (UID: {}).", authenticatedUid);

        // KLJUČNA PROMJENA OVDJE: Pozovi metodu koja prima SAMO JEDAN PARAMETAR (UID)
        // PhotoServiceImpl.getPhotosByUser() već unutar sebe provjerava autentikaciju i admin status.
        List<Photo> photos = asyncHelperPhoto.getPhotosByUser(authenticatedUid); // <--- PROMJENA JE OVDJE!
        return photos.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
//    @GetMapping("/user")
//    @PreAuthorize("hasRole('REGISTERED') or hasRole('ADMIN')")
//    public List<PhotoDto> getPhotosForCurrentUser() throws ExecutionException, InterruptedException {
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        String authenticatedUid = (String) authentication.getPrincipal(); // UID trenutno ulogiranog korisnika
//        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
//
//        logger.info("Dohvaćanje fotografija za CURRENT korisnika (UID: {}). Je li admin: {}", authenticatedUid, isAdmin);
//
//        // KLJUČNA PROMJENA: Pozivamo NOVU metodu u AsyncHelperPhoto
//        List<Photo> photos = asyncHelperPhoto.getPhotosByUser(authenticatedUid, authenticatedUid, isAdmin);
//        return photos.stream()
//                .map(this::mapToDto)
//                .collect(Collectors.toList());
//    }


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
            // OVDJE JE UKLONJENO: @RequestParam boolean isAdmin
            // NIKAD ne dopuštaj klijentu da šalje isAdmin status!
    ) throws ExecutionException, InterruptedException { // Ostaju exceptioni jer ti tako koristiš
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String uid = (String) authentication.getPrincipal();

        // KLJUČNA IZMJENA: Izračunavamo isAdmin na serverskoj strani, sigurno!
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        logger.info("Ažuriranje fotografije ID: {} za UID: {}, isAdmin: {}", id, uid, isAdmin);


        // Poziv AsyncHelperPhoto metode, prosljeđujemo sada izračunati isAdmin
        // S obzirom da vraća CompletableFuture, moramo ga "čekati" s .get()
        // Razmotri dodavanje timeouta za .get() kako bi izbjegao beskonačno čekanje:
        try {
            Photo updatedPhoto = asyncHelperPhoto.updatePhotoMetadata(id, description, hashtags, uid, isPrivate, isAdmin);
            // Ako je sve prošlo ok, mapiraj i vrati DTO
            return ResponseEntity.ok(mapToDto(updatedPhoto));
        } catch (ExecutionException e) {
            // Uhvati stvarni uzrok iznimke iz CompletableFuture
            Throwable cause = e.getCause();
            logger.error("Greška prilikom ažuriranja fotografije ID: {}: {}", id, cause != null ? cause.getMessage() : e.getMessage(), cause);

            // Tvoje rukovanje greškama u stilu: baci RuntimeException ili vrati određeni HTTP status
            if (cause != null) {
                if (cause.getMessage().contains("Unauthorized")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                if (cause.getMessage().contains("Photo not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                }
            }
            // Generic fallback za ostale greške
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException e) {
            // Ako se nit prekine dok čeka rezultat
            Thread.currentThread().interrupt(); // Ponovno postavi interrupt flag
            logger.error("Ažuriranje fotografije ID: {} prekinuto: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        // Ako si dodao .get(timeout, TimeUnit.SECONDS), moraš dodati i TimeoutException
        // catch (TimeoutException e) {
        //     logger.error("Ažuriranje fotografije ID: {} isteklo vrijeme: {}", id, e.getMessage(), e);
        //     return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
        // }
    }

    @DeleteMapping("/{id}")
    //@PreAuthorize("hasRole('ADMIN') or @photoService.isOwner(#id.toString(), authentication.principal)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id) throws ExecutionException, InterruptedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String uid = (String) authentication.getPrincipal();
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        asyncHelperPhoto.deletePhoto(id, uid, isAdmin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/last10")
    public List<PhotoDto> getLast10Photos() throws ExecutionException, InterruptedException {
        List<Photo> photos =  asyncHelperPhoto.getLast10Photos();
        return photos.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    @GetMapping("/user/{uid}") // Endpoint za SPECIFIČNOG korisnika (po UID-u u putanji)
    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
    public List<PhotoDto> getPhotosByUser(@PathVariable String uid) throws ExecutionException, InterruptedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUid = (String) authentication.getPrincipal();
        // boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); // OVA LINIJA VIŠE NE TREBA OVDJE

        logger.info("Pokušaj dohvata fotografija za korisnika {}. Autentificirani UID: {}.", uid, authenticatedUid);

        // KLJUČNA PROMJENA OVDJE: Opet, pozovi metodu koja prima SAMO JEDAN PARAMETAR (UID)
        List<Photo> photos = asyncHelperPhoto.getPhotosByUser(uid); // <--- PROMJENA JE OVDJE!
        return photos.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
//    @GetMapping("/user/{uid}")
//    @PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
//    public List<PhotoDto> getPhotosByUser(@PathVariable String uid) throws ExecutionException, InterruptedException {
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        String authenticatedUid = (String) authentication.getPrincipal();
//        // KLJUČNA PROMJENA: Deklaracija i inicijalizacija isAdmin
//        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
//
//        logger.info("Pokušaj dohvata fotografija za korisnika {}. Autentificirani UID: {}. Je li admin: {}", uid, authenticatedUid, isAdmin);
//
//        List<Photo> photos = asyncHelperPhoto.getPhotosByUser(uid, authenticatedUid, isAdmin);
//        return photos.stream()
//                .map(this::mapToDto)
//                .collect(Collectors.toList());
//    }



    private PhotoDto mapToDto(Photo photo) {
        PhotoDto photoDto = new PhotoDto();
        photoDto.setId(photo.getId());
        photoDto.setFilename(photo.getFilename());
        photoDto.setDescription(photo.getDescription());

        if (photo.getHashtags() != null) {
            photoDto.setHashtags(photo.getHashtags());
        } else {
            photoDto.setHashtags(String.valueOf(Collections.emptyList())); // Osiguraj da nikad nije null
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
        String firebaseUid = (String) authentication.getPrincipal();

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

//    @GetMapping("/{photoId}/download")
//    @PreAuthorize("isAuthenticated() or hasRole('ADMIN') or hasRole('REGISTERED')")
//    public CompletableFuture<ResponseEntity<byte[]>> downloadPhotoWithFilters(
//            @PathVariable Long photoId,
//            @RequestParam(required = false) Integer maxWidth,
//            @RequestParam(required = false) Integer maxHeight,
//            @RequestParam(required = false) String outputFormat,
//            @RequestParam(defaultValue = "false") boolean sepia,
//            @RequestParam(defaultValue = "false") boolean blur
//    ) {
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        String requesterUid = (String) authentication.getPrincipal();
//        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
//
//        return asyncHelperPhoto.downloadPhotoWithFilters(photoId, requesterUid, isAdmin, maxWidth, maxHeight, outputFormat, sepia, blur)
//                .thenApply(imageBytes -> {
//                    if (imageBytes == null || imageBytes.length == 0) {
//                        // KLJUČNA PROMJENA: Eksplicitno castamo null u (byte[]) null
//                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body((byte[]) null);
//                    }
//
//                    MediaType contentType = MediaType.IMAGE_JPEG; // Default
//                    if (outputFormat != null) {
//                        switch (outputFormat.toLowerCase()) {
//                            case "png":
//                                contentType = MediaType.IMAGE_PNG;
//                                break;
//                            case "gif":
//                                contentType = MediaType.IMAGE_GIF;
//                                break;
//                            case "bmp":
//                                contentType = MediaType.parseMediaType("image/bmp");
//                                break;
//                            // Dodajte ostale formate po potrebi
//                            default:
//                                contentType = MediaType.IMAGE_JPEG;
//                                break;
//                        }
//                    } else {
//                        // Ovdje bi se moglo pokušati odrediti ContentType iz ImageBytes ako outputFormat nije zadan.
//                        // Za sada, ako outputFormat nije zadan, ostaje default JPEG ili null.
//                        // Bolja praksa bi bila dodati logiku za detekciju tipa iz bajtova (npr. koristeći ImageIO.getImageReaders)
//                        // ali za ovu svrhu, možemo se osloniti na default ili klijenta.
//                    }
//
//                    return ResponseEntity.ok()
//                            .contentType(contentType)
//                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"processed_photo." + (outputFormat != null ? outputFormat.toLowerCase() : "jpeg") + "\"")
//                            .body(imageBytes);
//                })
//                .exceptionally(ex -> {
//                    logger.error("Error downloading photo with filters for ID {}: {}", photoId, ex.getMessage(), ex);
//                    if (ex.getCause() instanceof RuntimeException && ex.getCause().getMessage().contains("Unauthorized")) {
//                        // KLJUČNA PROMJENA: Eksplicitno castamo null u (byte[]) null
//                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body((byte[]) null);
//                    }
//                    // KLJUČNA PROMJENA: Eksplicitno castamo null u (byte[]) null
//                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((byte[]) null);
//                });
//    }
//@GetMapping("/{photoId}/download")
////@PreAuthorize("hasRole('ADMIN') or hasRole('REGISTERED')")
//public CompletableFuture<ResponseEntity<byte[]>> downloadPhotoWithFilters(
//        @PathVariable Long photoId,
//        @RequestParam(required = false) Integer maxWidth,
//        @RequestParam(required = false) Integer maxHeight,
//        @RequestParam(required = false) String outputFormat,
//        @RequestParam(defaultValue = "false") boolean sepia,
//        @RequestParam(defaultValue = "false") boolean blur
//) {
//    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//    String requesterUid = (String) authentication.getPrincipal();
//    boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
//
//    return asyncHelperPhoto.downloadPhotoWithFilters(photoId, requesterUid, isAdmin, maxWidth, maxHeight, outputFormat, sepia, blur)
//            .thenApply(imageBytes -> {
//                // Ako su bajtovi prazni, nešto je pošlo po zlu prije ili nije pronađeno.
//                if (imageBytes == null || imageBytes.length == 0) {
//                    logger.warn("Fotografija s ID {} nije pronađena ili je obrađena u prazne bajtove.", photoId);
//                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new byte[0]);
//                }
//
//                // Dinamički odredi Content-Type
//                MediaType contentType;
//                String finalOutputFormat = outputFormat;
//
//                // Ako outputFormat nije zadan, pokušaj ga detektirati iz bajtova
//                if (finalOutputFormat == null || finalOutputFormat.isEmpty()) {
//                    try {
//                        // POZIV NOVE METODE!
//                        String detectedFormat = asyncHelperPhoto.getFormatFromBytes(imageBytes);
//                        if (detectedFormat != null) {
//                            finalOutputFormat = detectedFormat;
//                            logger.debug("Detektirani format iz bajtova: {}", detectedFormat);
//                        } else {
//                            finalOutputFormat = "jpeg"; // Fallback ako detekcija ne uspije
//                            logger.warn("Nije moguće detektirati format slike iz bajtova, koristim default: {}", finalOutputFormat);
//                        }
//                    } catch (IOException e) {
//                        logger.warn("Greška prilikom detekcije formata slike iz bajtova za ID {}: {}", photoId, e.getMessage());
//                        finalOutputFormat = "jpeg"; // Fallback u slučaju greške
//                    }
//                }
//
//                // Mapiraj string format na MediaType
//                switch (finalOutputFormat.toLowerCase()) {
//                    case "png":
//                        contentType = MediaType.IMAGE_PNG;
//                        break;
//                    case "gif":
//                        contentType = MediaType.IMAGE_GIF;
//                        break;
//                    case "bmp":
//                        contentType = MediaType.parseMediaType("image/bmp");
//                        break;
//                    case "avif":
//                        contentType = MediaType.parseMediaType("image/avif");
//                        break;
//                    case "webp":
//                        contentType = MediaType.parseMediaType("image/webp");
//                        break;
//                    case "tif":
//                    case "tiff":
//                        contentType = MediaType.parseMediaType("image/tiff");
//                        break;
//                    case "svg": // Ako planiraš podržati SVG, iako nije raster
//                        contentType = MediaType.parseMediaType("image/svg+xml");
//                        break;
//                    default:
//                        contentType = MediaType.IMAGE_JPEG; // Default na JPEG
//                        break;
//                }
//
//                // Odredi naziv datoteke za download
//                String filename = "processed_photo." + finalOutputFormat.toLowerCase();
//
//                // Vrati uspješan ResponseEntity s bajtovima i headerima
//                return ResponseEntity.ok()
//                        .contentType(contentType)
//                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
//                        .body(imageBytes);
//            })
//            .exceptionally(ex -> {
//                Throwable cause = ex.getCause(); // Dohvati pravi uzrok iz CompletionException
//                logger.error("Greška prilikom preuzimanja/obrade fotografije ID {}: {}", photoId, cause != null ? cause.getMessage() : ex.getMessage(), ex);
//
//                if (cause instanceof PhotoService.PhotoNotFoundException) {
//                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new byte[0]);
//                }
//                if (cause instanceof SecurityException) {
//                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new byte[0]);
//                }
//                if (cause instanceof IOException && cause.getMessage() != null && cause.getMessage().contains("Failed to read image bytes")) {
//                    // Greška pri čitanju samih bajtova slike (npr. nepodržan format, oštećena datoteka)
//                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
//                }
//                // Generic RuntimeException ili drugi neobrađeni slučajevi
//                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
//            });
//}
// izmjene 28.7.2025. testiranja
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
    String requesterUid; // Više nije direktno castanje na String

    if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
        // Ako je principal tipa UserDetails (kao kod @WithMockUser testova)
        requesterUid = ((UserDetails) authentication.getPrincipal()).getUsername();
    } else if (authentication != null && authentication.getPrincipal() instanceof String) {
        // Ako je principal direktno String (kao što vaš Firebase filter postavlja u produkciji)
        requesterUid = (String) authentication.getPrincipal();
    } else {
        // Ako korisnik nije autentificiran ili principal nije očekivanog tipa.
        // Ovisno o vašoj logici, ovdje možete:
        // 1. Baciti iznimku (npr. new AccessDeniedException("User not authenticated or principal type not recognized."))
        // 2. Postaviti neku defaultnu "anonimnu" vrijednost (ako to ima smisla za vašu poslovnu logiku)
        logger.warn("Neautentificirani korisnik ili neočekivani tip principala u downloadPhotoWithFilters. Principal type: {}",
                authentication != null ? authentication.getPrincipal().getClass().getName() : "null");
        // Možete odlučiti što je prikladnije:
        // throw new AccessDeniedException("User not authenticated for photo download.");
        requesterUid = "anonymous_user_id"; // Primjer: postaviti generički ID za neautentificirane
    }

    boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

    return asyncHelperPhoto.downloadPhotoWithFilters(photoId, requesterUid, isAdmin, maxWidth, maxHeight, outputFormat, sepia, blur)
            .thenApply(imageBytes -> {
                // ... ostatak vaše .thenApply logike (ovo izgleda OK)
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
                // ... ostatak vaše .exceptionally logike (ovo izgleda OK)
                Throwable cause = ex.getCause();
                logger.error("Greška prilikom preuzimanja/obrade fotografije ID {}: {}", photoId, cause != null ? cause.getMessage() : ex.getMessage(), ex);

                if (cause instanceof PhotoService.PhotoNotFoundException) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new byte[0]);
                }
                if (cause instanceof SecurityException) {
                    // Ovo je ključno: Spring Security će baciti SecurityException ako @PreAuthorize ne prođe
                    // ili ako je vaša logika unutar servisa bacila SecurityException.
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new byte[0]);
                }
                if (cause instanceof IOException && cause.getMessage() != null && cause.getMessage().contains("Failed to read image bytes")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
            });
}
    @GetMapping("/search")
  // @PreAuthorize("isAuthenticated() or hasRole('ADMIN') or hasRole('REGISTERED')")
    public CompletableFuture<ResponseEntity<List<PhotoDto>>> searchPhotos(
            @RequestParam (required = false) String searchTerm,
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