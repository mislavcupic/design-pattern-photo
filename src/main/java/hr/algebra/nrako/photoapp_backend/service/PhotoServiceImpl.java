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

import java.io.FileOutputStream; // Ostavljamo za privremeni debugging!
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Service
public class PhotoServiceImpl implements PhotoService, PhotoUploadSubject {
    private static final Logger logger = LoggerFactory.getLogger(PhotoServiceImpl.class);

    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final ImageProcessingService imageProcessingService;
    private final ImageProcessorBuilder imageProcessorBuilder;
    private final List<PhotoUploadObserver> observers = new ArrayList<>();


    public PhotoServiceImpl(PhotoRepository photoRepository, StorageService storageService,
                            ImageProcessingService imageProcessingService,
                            ImageProcessorBuilder imageProcessorBuilder) {
        this.photoRepository = photoRepository;
        this.storageService = storageService;
        this.imageProcessingService = imageProcessingService;
        this.imageProcessorBuilder = imageProcessorBuilder;
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

    @Override
    public CompletableFuture<Photo> uploadPhoto(MultipartFile file, String description, String hashtags, String uid, String fileUrl, Boolean isPrivate) {
        logger.debug("Starting uploadPhoto for UID: {}, Original Filename: {}, Description: {}, Hashtags: {}, isPrivate: {}",
                uid, file.getOriginalFilename(), description, hashtags, isPrivate);

        String uniqueFilenamePart = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        String fullGcsObjectName = "userPhotos/" + uid + "/" + uniqueFilenamePart;

        logger.info("Generated full GCS object name for upload: {}", fullGcsObjectName);

        storageService.uploadPhoto(file, fullGcsObjectName);

        Photo photo = new Photo();
        photo.setId(System.currentTimeMillis());
        photo.setFilename(fullGcsObjectName);
        photo.setDescription(description);
        photo.setHashtags(hashtags);
        photo.setUploadedBy(uid);

        try {
            String bucketName = "photoapp-c195d"; // PROVJERI I AŽURIRAJ AKO TVOJ BUCKET NIJE OVAJ
            String encodedObjectName = URLEncoder.encode(fullGcsObjectName, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");
            String publicFileUrlFinal = String.format("https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media", bucketName, encodedObjectName);
            photo.setFileUrl(publicFileUrlFinal);
            logger.debug("Generated public file URL: {}", publicFileUrlFinal);
        } catch (Exception e) {
            logger.error("Error generating public file URL for {}: {}", fullGcsObjectName, e.getMessage(), e);
            photo.setFileUrl(null);
        }

        photo.setUploadDate(Timestamp.now());
        photo.setIsPrivate(isPrivate);
        // photo.setContentType(file.getContentType()); // DODAJ OVO ako si dodao polje u Photo objekt!

        photoRepository.savePhoto(photo);
        logger.info("Photo object saved to Firestore with ID: {}", photo.getId());

        notifyObservers(uid);

        return CompletableFuture.completedFuture(photo);
    }

    @Override
    @Async
    public CompletableFuture<List<Photo>> getPhotosByUser(String requestedUid) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String tempAuthenticatedUid = null;
            boolean tempIsAdmin = false;

            if (authentication != null && authentication.getPrincipal() instanceof String && !authentication.getName().equals("anonymousUser")) {
                tempAuthenticatedUid = authentication.getName();
                tempIsAdmin = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            } else {
                logger.info("Service: Anonymous user trying to access photos for UID: {}", requestedUid);
            }

            final String authenticatedUid = tempAuthenticatedUid;
            final boolean isAdmin = tempIsAdmin;

            logger.info("Service: Authenticated UID: {}, Is Admin: {}", authenticatedUid, isAdmin);

            return photoRepository.getPhotosByUser(requestedUid)
                    .thenApply(photosFromRepo -> {
                        logger.info("Service: getPhotosByUser za UID: {}. Broj fotki iz repozitorija PRIJE filtriranja: {}", requestedUid, photosFromRepo.size());

                        if (!photosFromRepo.isEmpty()) {
                            photosFromRepo.forEach(p -> logger.debug("  ID: {}, isPrivate: {}, UploadedBy: {}", p.getId(), p.getIsPrivate(), p.getUploadedBy()));
                        } else {
                            logger.debug("  Nema fotografija dohvaćenih iz repozitorija za UID: {}", requestedUid);
                        }

                        if (requestedUid.equals(authenticatedUid) || isAdmin) {
                            logger.info("Service: Pristup svim fotografijama korisnika {} (vlasnik ili admin). Vraćam {} fotki.", requestedUid, photosFromRepo.size());
                            return photosFromRepo;
                        } else {
                            List<Photo> publicPhotos = photosFromRepo.stream()
                                    .filter(photo -> !photo.getIsPrivate())
                                    .collect(Collectors.toList());
                            logger.info("Service: Pristup javnim fotografijama korisnika {} (nije vlasnik ni admin). Vraćam {} fotki.", requestedUid, publicPhotos.size());
                            return publicPhotos;
                        }
                    })
                    .exceptionally(ex -> {
                        logger.error("Service: Error getting photos by user {}: {}", requestedUid, ex.getMessage(), ex);
                        throw new RuntimeException("Failed to get photos by user", ex);
                    });

        } catch (Exception e) {
            logger.error("Service: Unexpected error in getPhotosByUser for user {}: {}", requestedUid, e.getMessage(), e);
            throw new RuntimeException("Failed to get photos by user due to unexpected error", e);
        }
    }
    @Override
    public CompletableFuture<Photo> getPhotoDetails(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            Photo photo = photoRepository.getPhotoById(id.toString());
            if (photo == null) {
                return null;
            }

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String authenticatedUid = null;
            boolean isAdmin = false;

            if (authentication != null && authentication.getPrincipal() instanceof String) {
                authenticatedUid = (String) authentication.getPrincipal();
                isAdmin = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            }

            if (photo.getIsPrivate() && !(photo.getUploadedBy().equals(authenticatedUid) || isAdmin)) {
                logger.warn("Pokušaj pristupa privatnoj fotografiji ID: {} od strane neovlaštenog korisnika.", id);
                throw new RuntimeException("Unauthorized to access this private photo.");
            }
            return photo;
        });
    }

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
            if (photo == null) {
                throw new RuntimeException("Photo not found for ID: " + photoId);
            }

            if (!photo.getUploadedBy().equals(uid) && !isAdmin) {
                logger.warn("Korisnik {} pokušao ažurirati fotografiju ID: {} bez ovlasti.", uid, photoId);
                throw new RuntimeException("Unauthorized to update this photo");
            }

            photo.setDescription(newDescription);
            if (newHashtags != null && !newHashtags.trim().isEmpty()) {
                photo.setHashtags(String.valueOf(Arrays.stream(newHashtags.split(" "))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList())));
            } else {
                photo.setHashtags(String.valueOf(Collections.emptyList()));
            }
            photo.setIsPrivate(isPrivate);
            photoRepository.savePhoto(photo);
            logger.info("Fotografija ID: {} uspješno ažurirana od strane korisnika {}.", photoId, uid);
            return photo;
        });
    }

    @Override
    public CompletableFuture<Void> deletePhoto(Long photoId, String requesterUid, boolean isAdmin) {
        return CompletableFuture.runAsync(() -> {
            // Ovdje je tvoja implementacija delete metode koju trenutno preskačemo.
            // Pretpostavljam da ćeš je vratiti kada budeš spreman.
            // Trenutno ostavljam originalni kod radi konteksta, ali ga nećeš mijenjati.
            try {
                List<com.google.cloud.firestore.QueryDocumentSnapshot> documents = photoRepository.getPhotoDocumentByLongId(photoId);

                if (documents == null || documents.isEmpty()) {
                    logger.warn("Attempted to delete photo with ID {} but it was not found.", photoId);
                    return;
                }

                com.google.cloud.firestore.QueryDocumentSnapshot snapshot = documents.get(0);
                hr.algebra.nrako.photoapp_backend.domain.Photo photo = snapshot.toObject(hr.algebra.nrako.photoapp_backend.domain.Photo.class);
                String firestoreDocumentId = snapshot.getId();

                if (!photo.getUploadedBy().equals(requesterUid) && !isAdmin) {
                    logger.warn("User {} is unauthorized to delete photo ID: {}", requesterUid, photoId);
                    throw new RuntimeException("Unauthorized to delete this photo");
                }

                if (photo.getFilename() != null && !photo.getFilename().isEmpty()) {
                    storageService.deletePhoto(photo.getFilename());
                    logger.info("Deleted file from Storage: {}", photo.getFilename());
                } else {
                    logger.warn("Storage path (filename) not found for photo ID: {}. Skipping Storage deletion.", photoId);
                }



                logger.info("Photo with ID {} deleted successfully.", photoId);

            } catch (Exception e) {
                logger.error("Error deleting photo ID {}: {}", photoId, e.getMessage(), e);
                throw new RuntimeException("Failed to delete photo", e);
            }
        });
    }



    @Override
    public CompletableFuture<List<Photo>> getAllPhotos() {
        return photoRepository.getAllPhotos()
                .thenApply(photos -> photos.stream()
                        .filter(photo -> !photo.getIsPrivate())
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Boolean> togglePhotoPrivacy(String photoId, String authenticatedFirebaseUid) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentAuthenticatedUid = (authentication != null && authentication.getPrincipal() instanceof String) ?
                (String) authentication.getPrincipal() : null;
        boolean currentIsAdmin = (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Pokušavam promijeniti status privatnosti za ID fotografije: {}", photoId);
                logger.debug("Debug: Unutar supplyAsync - authenticatedFirebaseUid: {}", authenticatedFirebaseUid);
                logger.debug("Debug: Unutar supplyAsync - currentAuthenticatedUid (iz konteksta): {}", currentAuthenticatedUid);
                logger.debug("Debug: Unutar supplyAsync - currentIsAdmin (iz konteksta): {}", currentIsAdmin);

                Photo photo = photoRepository.getPhotoById(photoId);
                if (photo == null) {
                    logger.warn("Fotografija s ID-jem {} nije pronađena za promjenu privatnosti.", photoId);
                    return false;
                }

                boolean isOwner = photo.getUploadedBy().equals(currentAuthenticatedUid);
                boolean isAdmin = currentIsAdmin;

                if (!isOwner && !isAdmin) {
                    logger.warn("Korisnik {} nema ovlasti za promjenu privatnosti fotografije {}", authenticatedFirebaseUid, photoId);
                    return false;
                }

                photo.setIsPrivate(!photo.getIsPrivate());
                photoRepository.savePhoto(photo);

                logger.info("Uspješno promijenjen status privatnosti za ID fotografije: {}. Novi status: {}", photoId, photo.getIsPrivate());
                return true;
            } catch (Exception e) {
                logger.error("Greška prilikom promjene privatnosti fotografije s ID-jem {}: {}", photoId, e.getMessage(), e);
                throw new RuntimeException("Greška prilikom promjene privatnosti fotografije u Firestore/Datastore", e);
            }
        });
    }

    @Override
    public boolean isOwner(String photoId, String firebaseUid) {
        try {
            Photo photo = photoRepository.getPhotoById(photoId);
            return photo != null && photo.getUploadedBy().equals(firebaseUid);
        } catch (Exception e) {
            logger.error("Greška prilikom provjere vlasništva fotografije s ID-jem {}: {}", photoId, e.getMessage());
            return false;
        }
    }

    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            return authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
    @Override
    public String getFormatFromBytes(byte[] imageBytes) throws IOException {
        // Poziva imageProcessingService koji ima stvarnu logiku za detekciju formata
        return imageProcessingService.getOriginalImageFormat(imageBytes);
    }
    @Override
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                Photo photo = photoRepository.getPhotoById(photoId.toString());
                if (photo == null) {
                    logger.warn("PhotoService: Photo not found for ID: {}", photoId);
                    throw new PhotoService.PhotoNotFoundException("Photo not found for ID: " + photoId);
                }

                if (photo.getIsPrivate() && !(photo.getUploadedBy().equals(requesterUid) || isAdmin)) {
                    logger.warn("Pokušaj preuzimanja privatne fotografije ID: {} od strane neovlaštenog korisnika (UID: {}).", photoId, requesterUid);
                    throw new SecurityException("Unauthorized to download this private photo.");
                }

                byte[] originalImageBytes = storageService.downloadPhotoAsBytes(photo.getFilename());

                if (originalImageBytes == null || originalImageBytes.length == 0) {
                    logger.error("Originalni bajtovi slike su NULL ili PRAZNI za ID: {} (filename: {})", photoId, photo.getFilename());
                    throw new RuntimeException("Original image data not found or is empty.");
                }
                logger.info("Preuzeto {} bajtova za sliku ID: {} (filename: {})", originalImageBytes.length, photoId, photo.getFilename());

                // Debugging: Privremeno spremanje bajtova u datoteku (OVO JE SAMO ZA DEBUGGING, UKLONI U PRODUKCIJI!)
                try (FileOutputStream fos = new FileOutputStream("debug_original_image_" + photoId + ".bin")) {
                    fos.write(originalImageBytes);
                    logger.info("Spremljeni sirovi bajtovi u datoteku: debug_original_image_{}.bin", photoId);
                } catch (IOException e) {
                    logger.error("Greška prilikom spremanja debug datoteke (original): {}", e.getMessage());
                }
                // KRAJ DEBUGGING KODA

                imageProcessorBuilder.reset();
                ImageProcessingOptions options = imageProcessorBuilder
                        .withImageBytes(originalImageBytes)
                        .resize(maxWidth, maxHeight)
                        .format(outputFormat)
                        .sepia(applySepia)
                        .blur(applyBlur)
                        .build();

                byte[] processedBytes = imageProcessingService.processImage(
                        options.getImageBytes(),
                        options.getMaxWidth(),
                        options.getMaxHeight(),
                        options.getOutputFormat(),
                        options.isApplySepia(),
                        options.isApplyBlur()
                );

                logger.info("Obrada slike ID: {} završena. Veličina procesiranih bajtova: {}", photoId, processedBytes.length);

                // Debugging: Privremeno spremanje procesiranih bajtova u datoteku (OVO JE SAMO ZA DEBUGGING, UKLONI U PRODUKCIJI!)
                try (FileOutputStream fos = new FileOutputStream("debug_processed_image_" + photoId + "_" + (outputFormat != null ? outputFormat : "jpeg") + ".bin")) {
                    fos.write(processedBytes);
                    logger.info("Spremljeni procesirani bajtovi u datoteku: debug_processed_image_{}_{}.bin", photoId, (outputFormat != null ? outputFormat : "jpeg"));
                } catch (IOException e) {
                    logger.error("Greška prilikom spremanja debug datoteke (procesirano): {}", e.getMessage());
                }
                // KRAJ DEBUGGING KODA

                return processedBytes;

            } catch (SecurityException e) {
                logger.error("PhotoService: Autorizacijska greška kod preuzimanja slike s filterima za ID {}: {}", photoId, e.getMessage());
                throw e;
            } catch (PhotoService.PhotoNotFoundException e) {
                logger.error("PhotoService: Fotografija ID {} nije pronađena: {}", photoId, e.getMessage());
                throw e;
            } catch (Exception e) {
                logger.error("PhotoService: Neočekivana greška prilikom preuzimanja/obrade slike ID {}: {}", photoId, e.getMessage(), e);
                throw new RuntimeException("Failed to download or process photo", e);
            }
        });
    }

    // VRAĆENA searchPhotos metoda iz tvog originalnog koda
    @Override
    public CompletableFuture<List<Photo>> searchPhotos(
            String searchTerm,
            String uploadedByUid,
            String requesterUid,
            boolean isAdmin
    ) {
        return photoRepository.searchPhotos(searchTerm, uploadedByUid)
                .thenApply(photos -> photos.stream()
                        .filter(photo -> {
                            if (photo.getIsPrivate()) {
                                return photo.getUploadedBy().equals(requesterUid) || isAdmin;
                            }
                            return true;
                        })
                        .collect(Collectors.toList()));
    }
}