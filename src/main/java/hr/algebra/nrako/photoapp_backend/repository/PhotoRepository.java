package hr.algebra.nrako.photoapp_backend.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import hr.algebra.nrako.photoapp_backend.domain.Photo;
import jakarta.annotation.PreDestroy; // <-- DODAJ OVAJ IMPORT
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger; // <-- DODAJ OVAJ IMPORT
import org.slf4j.LoggerFactory; // <-- DODAJ OVAJ IMPORT

@Repository
public class PhotoRepository {

    private final Firestore firestore;
    private static final Logger logger = LoggerFactory.getLogger(PhotoRepository.class); // <-- DODAJ OVAJ RED

    public PhotoRepository() {
        this.firestore = FirestoreClient.getFirestore();
        logger.info("Firestore client initialized."); // <-- DODAJ LOG
    }

    // <-- DODAJ OVDJE OVAJ BLOK KODA
    @PreDestroy
    public void destroy() {
        if (this.firestore != null) {
            try {
                // Gašenje Firestore klijenta za oslobađanje resursa i niti
                this.firestore.shutdown();
                logger.info("Firestore client shut down successfully.");
            } catch (Exception e) {
                logger.warn("Error shutting down Firestore client: {}", e.getMessage(), e);
            }
        }
    }
    // <-- KRAJ BLOKA KOJI SE DODAJU

    public void savePhoto(Photo photo) {
        ApiFuture<WriteResult> future = firestore.collection("photos")
                .document(photo.getId().toString())
                .set(photo);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error saving photo: {}", e.getMessage(), e); // <-- PROMJENA: Koristi logger
            throw new RuntimeException("Failed to save photo", e); // <-- PROMJENA: Baci kao unchecked
        }
    }

    public CompletableFuture<List<Photo>> getAllPhotos() {
        ApiFuture<QuerySnapshot> future = firestore.collection("photos").get();
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                return documents.stream().map(doc -> doc.toObject(Photo.class)).collect(Collectors.toList());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error getting all photos: {}", e.getMessage(), e); // <-- PROMJENA: Koristi logger
                throw new RuntimeException("Failed to get all photos", e); // <-- PROMJENA: Baci kao unchecked
            }
        });
    }

//    public Photo getPhotoById(String id) {
//        ApiFuture<DocumentSnapshot> future = firestore.collection("photos").document(id).get();
//        try {
//            DocumentSnapshot document = future.get();
//            if (document.exists()) {
//                return document.toObject(Photo.class);
//            } else {
//                return null;
//            }
//        } catch (InterruptedException | ExecutionException e) {
//            logger.error("Error getting photo by ID {}: {}", id, e.getMessage(), e); // <-- PROMJENA: Koristi logger
//            throw new RuntimeException("Failed to get photo by ID", e); // <-- PROMJENA: Baci kao unchecked
//        }
//    }
public List<QueryDocumentSnapshot> getPhotoDocumentByLongId(Long photoId) throws Exception {
    return firestore.collection("photos")
            .whereEqualTo("id", photoId) // Ključno: Traži po tvom LONG 'id' polju
            .limit(1) // Očekujemo samo jedan rezultat
            .get().get().getDocuments(); // Dohvaća stvarne dokumente
}
    public CompletableFuture<List<Photo>> findTop10ByOrderByUploadDateDesc() {
        ApiFuture<QuerySnapshot> future = firestore.collection("photos")
                .orderBy("uploadDate", Query.Direction.DESCENDING)
                .limit(10)
                .get();
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                return documents.stream().map(doc -> doc.toObject(Photo.class)).collect(Collectors.toList());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error finding top 10 photos: {}", e.getMessage(), e); // <-- PROMJENA: Koristi logger
                throw new RuntimeException("Failed to find top 10 photos", e); // <-- PROMJENA: Baci kao unchecked
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
                return documents.stream().map(doc -> doc.toObject(Photo.class)).collect(Collectors.toList());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error getting photos by user {}: {}", uid, e.getMessage(), e); // <-- PROMJENA: Koristi logger
                throw new RuntimeException("Failed to get photos by user", e); // <-- PROMJENA: Baci kao unchecked
            }
        });
    }

    public CompletableFuture<Void> deletePhoto(Long photoId) {
        return CompletableFuture.runAsync(() -> {
            try {
                firestore.collection("photos").document(photoId.toString()).delete().get();
                logger.info("Photo {} deleted from Firestore.", photoId); // <-- DODAJ LOG
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error deleting photo {}: {}", photoId, e.getMessage(), e); // <-- PROMJENA: Koristi logger
                throw new RuntimeException("Failed to delete photo", e);
            }
        });
    }
    public CompletableFuture<List<Photo>> searchPhotos(String searchTerm, String uploadedByUid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Query query = firestore.collection("photos");

                if (searchTerm != null && !searchTerm.isEmpty()) {
                    // Firestore nema 'contains' ili 'like' operacije.
                    // Stoga, pretraga po searchTerm-u će se morati filtrirati na klijentskoj strani
                    // nakon što se dohvate potencijalno relevantni dokumenti ili svi dokumenti
                    // ako nema drugih filtera.
                    // Ovdje se ne dodaje where uvjet za searchTerm jer to Firebase ne podržava direktno za substrings.
                }

                if (uploadedByUid != null && !uploadedByUid.isEmpty()) {
                    query = query.whereEqualTo("uploadedBy", uploadedByUid);
                }

                ApiFuture<QuerySnapshot> future = query.get();
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();

                List<Photo> filteredPhotos = documents.stream()
                        .map(doc -> doc.toObject(Photo.class))
                        .filter(photo -> {
                            if (searchTerm != null && !searchTerm.isEmpty()) {
                                String lowerSearchTerm = searchTerm.toLowerCase();
                                boolean descriptionMatch = photo.getDescription() != null && photo.getDescription().toLowerCase().contains(lowerSearchTerm);
                                boolean hashtagMatch = photo.getHashtags() != null && photo.getHashtags().toLowerCase().contains(lowerSearchTerm);
                                return descriptionMatch || hashtagMatch;
                            }
                            return true; // Ako nema searchTerma, ne filtriraj po njemu
                        })
                        .collect(Collectors.toList());

                return filteredPhotos;
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error searching photos: {}", e.getMessage(), e); // <-- PROMJENA: Koristi logger
                throw new RuntimeException("Failed to search photos", e); // <-- PROMJENA: Baci kao unchecked
            }
        });
    }
    public Photo getPhotoById(String id) { // Ovo je postojeća metoda koja dohvaća po Firestore Document ID-u (String)
        ApiFuture<DocumentSnapshot> future = firestore.collection("photos").document(id).get();
        try {
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                return document.toObject(Photo.class);
            } else {
                return null;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error getting photo by ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to get photo by ID", e);
        }
    }
}