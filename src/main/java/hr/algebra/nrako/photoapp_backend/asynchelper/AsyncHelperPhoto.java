//package hr.algebra.nrako.photoapp_backend.asynchelper;
//
//import hr.algebra.nrako.photoapp_backend.domain.Photo;
//import hr.algebra.nrako.photoapp_backend.service.PhotoService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutionException;
//
//@Service
//@RequiredArgsConstructor
//public class AsyncHelperPhoto {
//    private final PhotoService photoService;
//
//    public Photo uploadPhoto(MultipartFile file, String description, String hashtags, String uid, String fileUrl, Boolean isPrivate) throws ExecutionException, InterruptedException {
//        return photoService.uploadPhoto(file, description, hashtags, uid, fileUrl, isPrivate).get();
//    }
//
//    public List<Photo> getPhotosByUser(String uid) throws ExecutionException, InterruptedException {
//        return photoService.getPhotosByUser(uid).get();
//    }
//
//    public Photo getPhotoDetails(Long id) throws ExecutionException, InterruptedException {
//        return photoService.getPhotoDetails(id).get();
//    }
//
//    public List<Photo> getLast10Photos() throws ExecutionException, InterruptedException {
//        return photoService.getLast10Photos().get();
//    }
//
//    public Photo updatePhotoMetadata(Long photoId, String newDescription, String newHashtags, String uid, Boolean isPrivate) throws ExecutionException, InterruptedException {
//        return photoService.updatePhotoMetadata(photoId, newDescription, newHashtags, uid, isPrivate).get();
//    }
//
//    public Void deletePhoto(Long photoId, String requesterUid, boolean isAdmin) throws ExecutionException, InterruptedException {
//        return photoService.deletePhoto(photoId, requesterUid, isAdmin).get();
//    }
//
//    public Void deleteAllPhotos(String uid) throws ExecutionException, InterruptedException {
//        return photoService.deleteAllPhotos(uid).get();
//    }
//
//    public List<Photo> getAllPhotos() throws ExecutionException, InterruptedException {
//        return photoService.getAllPhotos().get();
//    }
//
//
//    public Boolean togglePhotoPrivacy(String photoId, String authenticatedFirebaseUid) throws ExecutionException, InterruptedException {
//        // Calls the PhotoService's togglePhotoPrivacy method and blocks until its CompletableFuture completes.
//        return photoService.togglePhotoPrivacy(photoId, authenticatedFirebaseUid).get();
//    }
//
//    // **************** KLJUČNE PROMJENE OVDJE ****************
//    // Ove metode SADA vraćaju CompletableFuture, kao što PhotoService vraća.
//    // Uklonjen je .get() poziv.
//
//    public CompletableFuture<byte[]> downloadPhotoWithFilters(
//            Long photoId,
//            String requesterUid,
//            boolean isAdmin,
//            Integer maxWidth,
//            Integer maxHeight,
//            String outputFormat,
//            boolean applySepia,
//            boolean applyBlur
//    ) {
//        return photoService.downloadPhotoWithFilters(photoId, requesterUid, isAdmin, maxWidth, maxHeight, outputFormat, applySepia, applyBlur);
//    }
//
//    public CompletableFuture<List<Photo>> searchPhotos(
//            String searchTerm,
//            String uploadedByUid,
//            String requesterUid,
//            boolean isAdmin
//    ) {
//        return photoService.searchPhotos(searchTerm, uploadedByUid, requesterUid, isAdmin);
//    }
//
//    public List<Photo> getPhotosByUser(String requestedUid, String authenticatedUid, boolean isAdmin) throws ExecutionException, InterruptedException {
//        // Poziva service metodu koja će imati logiku filtriranja
//        return photoService.getPhotosByUser(requestedUid).get();
//    }
//
//}

package hr.algebra.nrako.photoapp_backend.asynchelper;

import hr.algebra.nrako.photoapp_backend.domain.Photo;
import hr.algebra.nrako.photoapp_backend.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors; // Dodaj za stream API

@Service
@RequiredArgsConstructor
public class AsyncHelperPhoto {
    private final PhotoService photoService;

    public Photo uploadPhoto(MultipartFile file, String description, String hashtags, String uid, String fileUrl, Boolean isPrivate) throws ExecutionException, InterruptedException {
        return photoService.uploadPhoto(file, description, hashtags, uid, fileUrl, isPrivate).get();
    }

    // Ovu metodu više nećemo direktno koristiti iz kontrolera za dohvat "svojih" fotki
    // Zadržavamo je ako negdje drugdje u aplikaciji treba "samo" fotke nekog usera bez dodatne logike.
    public List<Photo> getPhotosByUser(String uid) throws ExecutionException, InterruptedException {
        return photoService.getPhotosByUser(uid).get();
    }

    public Photo getPhotoDetails(Long id) throws ExecutionException, InterruptedException {
        return photoService.getPhotoDetails(id).get();
    }

    public List<Photo> getLast10Photos() throws ExecutionException, InterruptedException {
        return photoService.getLast10Photos().get();
    }

    public Photo updatePhotoMetadata(Long photoId, String newDescription, String newHashtags, String uid, Boolean isPrivate) throws ExecutionException, InterruptedException {
        return photoService.updatePhotoMetadata(photoId, newDescription, newHashtags, uid, isPrivate).get();
    }

    public Void deletePhoto(Long photoId, String requesterUid, boolean isAdmin) throws ExecutionException, InterruptedException {
        return photoService.deletePhoto(photoId, requesterUid, isAdmin).get();
    }

    public Void deleteAllPhotos(String uid) throws ExecutionException, InterruptedException {
        return photoService.deleteAllPhotos(uid).get();
    }

    // PAŽNJA: photoService.getAllPhotos() vraća CompletableFuture<List<Photo>>
    // Ova metoda treba dohvaćati samo JAVNE fotografije, kao što i PhotoController to očekuje.
    public CompletableFuture<List<Photo>> getAllPhotos() {
        return photoService.getAllPhotos(); // Uklonjen .get()
    }


    public Boolean togglePhotoPrivacy(String photoId, String authenticatedFirebaseUid) throws ExecutionException, InterruptedException {
        return photoService.togglePhotoPrivacy(photoId, authenticatedFirebaseUid).get();
    }

    // **************** NOVO/IZMIJENJENO ZA PROBLEM 1 (Profile Page Photos) ****************
    // Ova metoda sada ima logiku filtriranja na temelju uloga i tko traži čije fotografije.
    public List<Photo> getPhotosByUser(String requestedUid, String authenticatedUid, boolean isAdmin) throws ExecutionException, InterruptedException {
        // Dohvati SVE fotografije za requestedUid (PhotoService.getPhotosByUser(requestedUid) mora vraćati i privatne)
        List<Photo> allUserPhotos = photoService.getPhotosByUser(requestedUid).get();

        // Implementiraj logiku filtriranja ovdje
        if (isAdmin || requestedUid.equals(authenticatedUid)) {
            // Admin ili vlasnik može vidjeti SVE fotografije (javne i privatne)
            return allUserPhotos;
        } else {
            // Drugi korisnici vide samo JAVNE fotografije
            return allUserPhotos.stream()
                    .filter(photo -> !photo.getIsPrivate()) // Filtriraj samo javne
                    .collect(Collectors.toList());
        }
    }

    // **************** IZMIJENJENO ZA PROBLEM 2 (Download) ****************
    // Metoda downloadPhotoWithFilters mora biti AsyncHelperPhoto metoda.
    // Ona će pozvati PhotoService za logiku preuzimanja i obrade.
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
        return photoService.downloadPhotoWithFilters(photoId, requesterUid, isAdmin, maxWidth, maxHeight, outputFormat, applySepia, applyBlur);
    }

    public CompletableFuture<List<Photo>> searchPhotos(
            String searchTerm,
            String uploadedByUid,
            String requesterUid,
            boolean isAdmin
    ) {
        return photoService.searchPhotos(searchTerm, uploadedByUid, requesterUid, isAdmin);
    }
}