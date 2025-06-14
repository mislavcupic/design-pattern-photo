package hr.algebra.nrako.photoapp_backend.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import hr.algebra.nrako.photoapp_backend.domain.ImageProcessingOptions;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.observer.PhotoUploadObserver;
import hr.algebra.nrako.photoapp_backend.repository.PhotoRepository;
import hr.algebra.nrako.photoapp_backend.util.ImageProcessorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException; // Ostaje, jer se koristi u drugim dijelovima koda
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


    @Service
    public class PhotoServiceImpl implements hr.algebra.nrako.photoapp_backend.service.PhotoService {
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

            photoRepository.savePhoto(photo);
            logger.info("Photo object saved to Firestore with ID: {}", photo.getId());

            notifyObservers(uid);

            return CompletableFuture.completedFuture(photo);
        }

        @Override
        public CompletableFuture<List<Photo>> getPhotosByUser(String requestedUid) {
            return photoRepository.getPhotosByUser(requestedUid)
                    .thenApply(photos -> {
                        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                        String authenticatedUid = null;
                        boolean isAdmin = false;

                        if (authentication != null && authentication.getPrincipal() instanceof String) {
                            authenticatedUid = (String) authentication.getPrincipal();
                            isAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                        }

                        if (requestedUid.equals(authenticatedUid) || isAdmin) {
                            logger.info("Pristup svim fotografijama korisnika {} (vlasnik ili admin).", requestedUid);
                            return photos;
                        } else {
                            logger.info("Pristup javnim fotografijama korisnika {} (nije vlasnik ni admin).", requestedUid);
                            return photos.stream()
                                    .filter(photo -> !photo.getIsPrivate())
                                    .collect(Collectors.toList());
                        }
                    });
        }

        @Override
        public CompletableFuture<Photo> getPhotoDetails(Long id) {
            return CompletableFuture.supplyAsync(() -> {
                // Originalni poziv photoRepository.getPhotoById
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
        public CompletableFuture<Photo> updatePhotoMetadata(Long photoId, String newDescription, String newHashtags, String uid, Boolean isPrivate) {
            return CompletableFuture.supplyAsync(() -> {
                // Originalni poziv photoRepository.getPhotoById
                Photo photo = photoRepository.getPhotoById(photoId.toString());
                if (photo == null) {
                    throw new RuntimeException("Photo not found for ID: " + photoId);
                }
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                String authenticatedUid = (String) authentication.getPrincipal();
                boolean isAdmin = isCurrentUserAdmin();

                if (!photo.getUploadedBy().equals(authenticatedUid) && !isAdmin) {
                    throw new RuntimeException("Unauthorized to update this photo");
                }

                photo.setDescription(newDescription);
                photo.setHashtags(newHashtags);
                photo.setIsPrivate(isPrivate);
                photoRepository.savePhoto(photo);
                return photo;
            });
        }
        @Override // Ili izbaci @Override ako ne nadjačavaš
        public CompletableFuture<Void> deletePhoto(Long photoId, String requesterUid, boolean isAdmin) {
            return CompletableFuture.runAsync(() -> {
                try {
                    // 1. Dohvati QueryDocumentSnapshot koristeći novu metodu iz repozitorija.
                    // Ova metoda pronalazi dokument po tvom Long ID-u i vraća QueryDocumentSnapshot.
                    List<com.google.cloud.firestore.QueryDocumentSnapshot> documents = photoRepository.getPhotoDocumentByLongId(photoId);

                    if (documents == null || documents.isEmpty()) {
                        logger.warn("Attempted to delete photo with ID {} but it was not found.", photoId);
                        return;
                    }

                    // Ako je pronađen, uzmi prvi (i jedini) dokument
                    com.google.cloud.firestore.QueryDocumentSnapshot snapshot = documents.get(0);

                    // Izvucite Photo objekt i stvarni Firestore Document ID iz snapshot-a
                    hr.algebra.nrako.photoapp_backend.domain.Photo photo = snapshot.toObject(hr.algebra.nrako.photoapp_backend.domain.Photo.class);
                    String firestoreDocumentId = snapshot.getId(); // <-- Evo ga, pravi Firestore ID!

                    // 2. Provjera autorizacije
                    if (!photo.getUploadedBy().equals(requesterUid) && !isAdmin) {
                        logger.warn("User {} is unauthorized to delete photo ID: {}", requesterUid, photoId);
                        throw new RuntimeException("Unauthorized to delete this photo");
                    }

                    // 3. Obriši stvarnu datoteku iz Firebase Storagea
                    // Koristi photo.getFilename() kao putanju u Storageu
                    if (photo.getFilename() != null && !photo.getFilename().isEmpty()) {
                        storageService.deletePhoto(photo.getFilename());
                        logger.info("Deleted file from Storage: {}", photo.getFilename());
                    } else {
                        logger.warn("Storage path (filename) not found for photo ID: {}. Skipping Storage deletion.", photoId);
                    }

                    // 4. Obriši zapis (metapodatke) iz Firestorea
                    // KORISTI STVARNI FIRESTORE DOCUMENT ID
                    Firestore firestore = FirestoreClient.getFirestore();
                    if (firestoreDocumentId != null && !firestoreDocumentId.isEmpty()) {
                        firestore.collection("photos").document(firestoreDocumentId).delete().get();
                        logger.info("Deleted document from Firestore: {}", firestoreDocumentId);
                    } else {
                        logger.error("Firestore document ID not found for photo ID: {}. Cannot delete from Firestore.", photoId);
                        throw new RuntimeException("Firestore document ID missing for photo.");
                    }

                    logger.info("Photo with ID {} deleted successfully.", photoId);

                } catch (Exception e) {
                    logger.error("Error deleting photo ID {}: {}", photoId, e.getMessage(), e);
                    throw new RuntimeException("Failed to delete photo", e);
                }
            });
        }

//        @Override
//        public CompletableFuture<Void> deletePhoto(Long photoId, String requesterUid, boolean isAdmin) {
//            return CompletableFuture.runAsync(() -> {
//                Photo photo = photoRepository.getPhotoById(photoId.toString());
//                if (!photo.getUploadedBy().equals(requesterUid) && !isAdmin) {
//                    throw new RuntimeException("Unauthorized to delete this photo");
//                }
//                storageService.deletePhoto(photo.getFilename());
//                photoRepository.deletePhoto(photoId);
//            });
//        }

        @Override
        public CompletableFuture<Void> deleteAllPhotos(String uid) {
            return photoRepository.getPhotosByUser(uid).thenCompose(userPhotos -> {
                userPhotos.forEach(photo -> storageService.deletePhoto(photo.getFilename()));
                List<CompletableFuture<Void>> deletionFutures = userPhotos.stream()
                        .map(photo -> CompletableFuture.runAsync(() -> photoRepository.deletePhoto(photo.getId())))
                        .collect(Collectors.toList());
                return CompletableFuture.allOf(deletionFutures.toArray(new CompletableFuture[0]));
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
                Photo photo = photoRepository.getPhotoById(photoId.toString());
                if (photo == null) {
                    throw new RuntimeException("Photo not found for ID: " + photoId);
                }

                if (photo.getIsPrivate() && !(photo.getUploadedBy().equals(requesterUid) || isAdmin)) {
                    logger.warn("Pokušaj preuzimanja privatne fotografije ID: {} od strane neovlaštenog korisnika.", photoId);
                    throw new RuntimeException("Unauthorized to download this private photo.");
                }

                byte[] originalImageBytes = storageService.downloadPhotoAsBytes(photo.getFilename());
                if (originalImageBytes == null || originalImageBytes.length == 0) {
                    // KLJUČNA PROMJENA: Umotavanje IOException u RuntimeException
                    throw new RuntimeException("Could not download original image bytes for photo: " + photo.getFilename());
                }

                imageProcessorBuilder.reset();
                ImageProcessingOptions options = imageProcessorBuilder
                        .withImageBytes(originalImageBytes)
                        .resize(maxWidth, maxHeight)
                        .format(outputFormat)
                        .sepia(applySepia)
                        .blur(applyBlur)
                        .build();

                try {
                    return imageProcessingService.processImage(
                            options.getImageBytes(),
                            options.getMaxWidth(),
                            options.getMaxHeight(),
                            options.getOutputFormat(),
                            options.isApplySepia(),
                            options.isApplyBlur()
                    );
                } catch (IOException e) {
                    // Ovdje se već lovi IOException iz imageProcessingService.processImage
                    logger.error("Greška prilikom obrade slike ID {}: {}", photoId, e.getMessage(), e);
                    throw new RuntimeException("Greška prilikom obrade slike: " + e.getMessage(), e);
                }
            });
        }

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
