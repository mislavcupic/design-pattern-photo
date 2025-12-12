package hr.algebra.nrako.photoapp_backend.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
// Uklanjamo ovaj import jer više nećemo koristiti FirestoreClient.getFirestore()
// import com.google.firebase.cloud.FirestoreClient;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class PhotoRepository {

    private final Firestore firestore;
    private static final Logger logger = LoggerFactory.getLogger(PhotoRepository.class);

    // 1. Promjena: Konstruktor sada prima Firestore instancu kao argument
    // Spring će automatski injektirati Firestore bean konfiguriran u FirebaseTestConfig.
    public PhotoRepository(Firestore firestore) {
        this.firestore = firestore;
        logger.info("PhotoRepository: Firestore client initialized via dependency injection.");
    }

    // Bilješka: Metoda @PreDestroy bi mogla biti suvišna ako Spring upravlja životnim ciklusom Beana
    // i samim Firestore beanom, ali je ostavljamo radi sigurnosti.
    @PreDestroy
    public void destroy() {
        if (this.firestore != null) {
            try {
                // Važno: Ovdje bi se trebalo voditi računa o tome tko je vlasnik Firestore instance.
                // Ako je Spring stvara i upravlja njome, možda je on i zadužen za njeno gašenje.
                // Ručno gašenje ovdje može uzrokovati probleme ako se Firestore instanca koristi negdje drugdje.
                // Za potrebe testova, često je bolje pustiti Spring da se brine o tome.
                // Ako ipak ostaviš, provjeri je li to ispravno ponašanje za tvoj Firebase konfiguracijski Bean.
                // this.firestore.shutdown(); // Komentiraj ako Spring treba upravljati gašenjem
                logger.info("PhotoRepository: PreDestroy called. Firestore client managed by Spring.");
            } catch (Exception e) {
                logger.warn("PhotoRepository: Error during Firestore client shutdown in PreDestroy: {}", e.getMessage(), e);
            }
        }
    }

    public void savePhoto(Photo photo) {
        ApiFuture<WriteResult> future = firestore.collection("photos")
                .document(photo.getId().toString())
                .set(photo);
        try {
            future.get();
            logger.info("PhotoRepository: Photo with ID {} saved successfully.", photo.getId());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("PhotoRepository: Error saving photo with ID {}: {}", photo.getId(), e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save photo", e);
        }
    }

    public CompletableFuture<List<Photo>> getAllPhotos() {
        ApiFuture<QuerySnapshot> future = firestore.collection("photos").get();
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                logger.debug("PhotoRepository: getAllPhotos - Retrieved {} documents.", documents.size());
                return documents.stream()
                        .map(doc -> doc.toObject(Photo.class))
                        .collect(Collectors.toList());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("PhotoRepository: Error getting all photos: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to get all photos", e);
            }
        });
    }

    public Photo getPhotoById(String id) {
        ApiFuture<DocumentSnapshot> future = firestore.collection("photos").document(id).get();
        try {
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                Photo photo = document.toObject(Photo.class);
                logger.debug("PhotoRepository: getPhotoById({}) - Found photo. ID: {}, isPrivate: {}", id, photo.getId(), photo.getIsPrivate());
                return photo;
            } else {
                logger.warn("PhotoRepository: getPhotoById({}) - Photo not found.", id);
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("PhotoRepository: Error getting photo by ID {}: {}", id, e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get photo by ID", e);
        }
    }

    public List<QueryDocumentSnapshot> getPhotoDocumentByLongId(Long photoId) throws Exception {
        logger.debug("PhotoRepository: getPhotoDocumentByLongId({}) - Querying by 'id' field.", photoId);
        return firestore.collection("photos")
                .whereEqualTo("id", photoId)
                .limit(1)
                .get().get().getDocuments();
    }

    public CompletableFuture<List<Photo>> findTop10ByOrderByUploadDateDesc() {
        ApiFuture<QuerySnapshot> future = firestore.collection("photos")
                .orderBy("uploadDate", Query.Direction.DESCENDING)
                .limit(10)
                .get();
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                logger.debug("PhotoRepository: findTop10ByOrderByUploadDateDesc - Retrieved {} documents.", documents.size());
                return documents.stream()
                        .map(doc -> doc.toObject(Photo.class))
                        .collect(Collectors.toList());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("PhotoRepository: Error finding top 10 photos: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to find top 10 photos", e);
            }
        });
    }

    public CompletableFuture<List<Photo>> getPhotosByUser(String uid) {
        ApiFuture<QuerySnapshot> future = firestore.collection("photos")
                .whereEqualTo("uploadedBy", uid)
                .get();
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                logger.debug("PhotoRepository: getPhotosByUser({}) - Retrieved {} documents.", uid, documents.size());
                return documents.stream()
                        .map(doc -> doc.toObject(Photo.class))
                        .collect(Collectors.toList());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("PhotoRepository: Error getting photos by user {}: {}", uid, e.getMessage(), e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to get photos by user", e);
            }
        });
    }

    // Metoda za brisanje fotki, ako je potrebno
    // public CompletableFuture<Void> deletePhoto(Long photoId) {
    //     return CompletableFuture.runAsync(() -> {
    //         try {
    //             firestore.collection("photos").document(photoId.toString()).delete().get();
    //             logger.info("PhotoRepository: Photo with ID {} deleted from Firestore.", photoId);
    //         } catch (InterruptedException | ExecutionException e) {
    //             logger.error("PhotoRepository: Error deleting photo {}: {}", photoId, e.getMessage(), e);
    //             Thread.currentThread().interrupt();
    //             throw new RuntimeException("Failed to delete photo", e);
    //         }
    //     });
    // }

    public CompletableFuture<List<Photo>> searchPhotos(String searchTerm, String uploadedByUid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Query query = firestore.collection("photos");

                if (uploadedByUid != null && !uploadedByUid.isEmpty()) {
                    query = query.whereEqualTo("uploadedBy", uploadedByUid);
                }

                ApiFuture<QuerySnapshot> future = query.get();
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                logger.debug("PhotoRepository: searchPhotos - Retrieved {} documents before client-side filtering for term '{}', uploadedBy '{}'.", documents.size(), searchTerm, uploadedByUid);

                List<Photo> filteredPhotos = documents.stream()
                        .map(doc -> doc.toObject(Photo.class))
                        .filter(photo -> {
                            if (searchTerm != null && !searchTerm.isEmpty()) {
                                String lowerSearchTerm = searchTerm.toLowerCase();
                                boolean descriptionMatch = photo.getDescription() != null && photo.getDescription().toLowerCase().contains(lowerSearchTerm);

                                // PROMJENA OVDJE: Prilagođeno za String hashtags
                                // Pretpostavlja da je hashtag string poput "[pas]", "[pas, mačka]" ili "pas mačka"
                                // i da je dovoljno provjeriti sadrži li taj string traženi pojam.
                                boolean hashtagMatch = photo.getHashtags() != null && photo.getHashtags().toLowerCase().contains(lowerSearchTerm);

                                return descriptionMatch || hashtagMatch;
                            }
                            return true; // Ako nema searchTerma, ne filtriraj po njemu
                        })
                        .collect(Collectors.toList());

                logger.debug("PhotoRepository: searchPhotos - After client-side filtering, {} photos remain.", filteredPhotos.size());
                return filteredPhotos;
            } catch (InterruptedException | ExecutionException e) {
                logger.error("PhotoRepository: Error searching photos: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to search photos", e);
            }
        });
    }
}