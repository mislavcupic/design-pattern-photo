package hr.algebra.nrako.photoapp_backend.service;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.observer.PhotoUploadObserver;
import hr.algebra.nrako.photoapp_backend.observer.PhotoUploadSubject; // <-- DODANO!
import hr.algebra.nrako.photoapp_backend.repository.UserPackageDataRepository;
import hr.algebra.nrako.photoapp_backend.service.context.PackageContext;
import hr.algebra.nrako.photoapp_backend.service.factory.PackageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import com.google.cloud.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.CompletableFuture;


@Service
@RequiredArgsConstructor
public class UserPackageServiceImpl implements UserPackageService, PhotoUploadObserver {

    public static final String USER_PACKAGE_DATA_NOT_FOUND = "User package data not found";
    private final UserPackageDataRepository userPackageDataRepository;
    // OVDJE JE PROMJENA: Injiciramo PhotoUploadSubject, a ne PhotoService
    private final PhotoUploadSubject photoUploadSubject; // <-- PROMJENJENO!

    @PostConstruct
    public void subscribeToPhotoUploads() {
        // Pozivamo registerObserver na PhotoUploadSubject
        photoUploadSubject.registerObserver(this); // <-- PROMJENJENO!
    }

    @Override
    public void onPhotoUploaded(String userId) {
        // Logika brojača sada dohvaća i sprema podatke u bazu
        getUserPackageData(userId)
                .thenAccept(packageData -> {
                    LocalDate today = LocalDate.now(ZoneId.systemDefault());
                    LocalDate lastUpload = null;

                    // Pretvaramo Google Cloud Timestamp u LocalDate
                    if (packageData.getLastUploadDate() != null) {
                        lastUpload = packageData.getLastUploadDate().toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    }

                    // Ako je zadnji upload bio neki drugi dan ili nikad, resetiraj brojač
                    if (lastUpload == null || !lastUpload.isEqual(today)) {
                        packageData.setCurrentDailyUploadCount(1); // Počinjemo s 1 za danas
                        packageData.setLastUploadDate(Timestamp.now()); // Postavi trenutni Timestamp
                    } else {
                        // Ako je isti dan, samo inkrementiraj
                        packageData.setCurrentDailyUploadCount(packageData.getCurrentDailyUploadCount() + 1);
                    }

                    // Spremi ažurirane podatke u bazu
                    userPackageDataRepository.save(packageData)
                            .thenAccept(savedData -> {
                                System.out.println("User " + userId + " uploaded a photo. Current count in DB: " + savedData.getCurrentDailyUploadCount());
                            })
                            .exceptionally(ex -> {
                                System.err.println("Error saving user package data for " + userId + ": " + ex.getMessage());
                                return null;
                            });
                })
                .exceptionally(ex -> {
                    System.err.println("Error fetching user package data for " + userId + ": " + ex.getMessage());
                    return null;
                });
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Integer> getRemainingUploads(String firebaseUid) {
        return getUserPackageData(firebaseUid)
                .thenApply(packageData -> {
                    int limit = PackageFactory.create(packageData.getUserPackageEnum()).getDailyUploadLimit();

                    LocalDate today = LocalDate.now(ZoneId.systemDefault());
                    LocalDate lastUpload = null;

                    // Pretvaramo Google Cloud Timestamp u LocalDate za provjeru datuma
                    if (packageData.getLastUploadDate() != null) {
                        lastUpload = packageData.getLastUploadDate().toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    }

                    int uploadedToday;
                    // Ako je novi dan ili nikad nije uploadano, brojač je 0 za danas
                    if (lastUpload == null || !lastUpload.isEqual(today)) {
                        uploadedToday = 0;
                    } else {
                        uploadedToday = packageData.getCurrentDailyUploadCount();
                    }

                    return limit - uploadedToday;
                });
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<UserPackageData> getUserPackageData(String firebaseUid) {
        return userPackageDataRepository.findByFirebaseUid(firebaseUid)
                .thenApply(data -> data.orElseThrow(() -> new IllegalArgumentException(USER_PACKAGE_DATA_NOT_FOUND)));
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Boolean> canChangePackage(String firebaseUid) {
        return getUserPackageData(firebaseUid)
                .thenApply(data -> new PackageContext(data).canChangePackage());
    }
//OVDJE KORISTIM FACTORY
    @Override
    @Async("taskExecutor")
    public CompletableFuture<Void> changeUserPackage(String firebaseUid, UserPackage newUserPackage) {
        return getUserPackageData(firebaseUid)
                .thenCompose(data -> {
                    PackageContext context = new PackageContext(data);

                    if (!context.canChangePackage()) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("You can only change your package once every 24 hours.")
                        );
                    }

                    data.setUserPackage(newUserPackage.name());

                    // Postojeća logika za promjenu paketa (Timestamp je već korišten)
                    LocalDateTime now = LocalDateTime.now();
                    com.google.cloud.Timestamp timestampNow = com.google.cloud.Timestamp.of(java.util.Date
                            .from(now.atZone(java.time.ZoneId.systemDefault()).toInstant()));
                    data.setLastPackageChangeDateTime(timestampNow);
                    data.setNextEligibleChangeDateTime(com.google.cloud.Timestamp.ofTimeSecondsAndNanos(
                            timestampNow.getSeconds() + 86400, timestampNow.getNanos()));

                    // Ovdje je promjena: pretvaramo CompletableFuture<UserPackageData> u CompletableFuture<Void>
                    return userPackageDataRepository.save(data).thenApply(savedData -> null);
                });
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<LocalDateTime> getNextEligibleChange(String firebaseUid) {
        return getUserPackageData(firebaseUid)
                .thenApply(data -> new PackageContext(data).getNextEligibleChange());
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<UserPackage> getUserPackage(String firebaseUid) {
        return getUserPackageData(firebaseUid)
                .thenApply(UserPackageData::getUserPackageEnum);
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<UserPackageData> createUserPackageData(String firebaseUid, UserPackage initialPackage) {
        UserPackageData newUserPackageData = new UserPackageData();
        newUserPackageData.setFirebaseUid(firebaseUid);
        newUserPackageData.setUserPackage(initialPackage.name());
        // Inicijaliziraj nova polja za brojač
        newUserPackageData.setCurrentDailyUploadCount(0);
        newUserPackageData.setLastUploadDate(null); // Ili Timestamp.now() ako želiš inicijalizirati na trenutni datum

        // ... (ostatak implementacije createUserPackageData) ...
        return userPackageDataRepository.save(newUserPackageData);
    }
}//package hr.algebra.nrako.photoapp_backend.service;
//
//import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
//import hr.algebra.nrako.photoapp_backend.domain.UserPackageData; // Morat ćeš dodati nova polja ovdje!
//import hr.algebra.nrako.photoapp_backend.observer.PhotoUploadObserver;
//import hr.algebra.nrako.photoapp_backend.repository.UserPackageDataRepository;
//import hr.algebra.nrako.photoapp_backend.service.context.PackageContext;
//import hr.algebra.nrako.photoapp_backend.service.factory.PackageFactory;
//import lombok.RequiredArgsConstructor;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.PostConstruct;
//import java.time.LocalDateTime;
//// Ukloni import za java.util.Map i java.util.concurrent.ConcurrentHashMap ako ti više ne trebaju za dailyUploadCounts
//// import java.util.Map;
//// import java.util.concurrent.ConcurrentHashMap;
//
//// Dodatni importi potrebni za rad s Timestampom i datumima
//import com.google.cloud.Timestamp;
//import java.time.LocalDate;
//import java.time.ZoneId;
//import java.util.Date; // Potrebno za pretvorbu Timestamp u Date pa u Instant
//
//import java.util.concurrent.CompletableFuture; // Potrebno za CompletableFuture
//
//
//@Service
//@RequiredArgsConstructor
//public class UserPackageServiceImpl implements UserPackageService, PhotoUploadObserver {
//
//    public static final String USER_PACKAGE_DATA_NOT_FOUND = "User package data not found";
//    private final UserPackageDataRepository userPackageDataRepository;
//    private final PhotoService photoService; // Inject PhotoService za registraciju
//
//    // Ukloni ovu liniju, više ti ne treba mapa u memoriji:
//    // private final Map<String, Integer> dailyUploadCounts = new ConcurrentHashMap<>();
//
//    @PostConstruct
//    public void subscribeToPhotoUploads() {
//        photoService.registerObserver(this);
//    }
//
//    @Override
//    public void onPhotoUploaded(String userId) {
//        // Logika brojača sada dohvaća i sprema podatke u bazu
//        getUserPackageData(userId)
//                .thenAccept(packageData -> {
//                    LocalDate today = LocalDate.now(ZoneId.systemDefault());
//                    LocalDate lastUpload = null;
//
//                    // Pretvaramo Google Cloud Timestamp u LocalDate
//                    if (packageData.getLastUploadDate() != null) {
//                        lastUpload = packageData.getLastUploadDate().toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
//                    }
//
//                    // Ako je zadnji upload bio neki drugi dan ili nikad, resetiraj brojač
//                    if (lastUpload == null || !lastUpload.isEqual(today)) {
//                        packageData.setCurrentDailyUploadCount(1); // Počinjemo s 1 za danas
//                        packageData.setLastUploadDate(Timestamp.now()); // Postavi trenutni Timestamp
//                    } else {
//                        // Ako je isti dan, samo inkrementiraj
//                        packageData.setCurrentDailyUploadCount(packageData.getCurrentDailyUploadCount() + 1);
//                    }
//
//                    // Spremi ažurirane podatke u bazu
//                    userPackageDataRepository.save(packageData)
//                            .thenAccept(savedData -> {
//                                System.out.println("User " + userId + " uploaded a photo. Current count in DB: " + savedData.getCurrentDailyUploadCount());
//                            })
//                            .exceptionally(ex -> {
//                                System.err.println("Error saving user package data for " + userId + ": " + ex.getMessage());
//                                return null;
//                            });
//                })
//                .exceptionally(ex -> {
//                    System.err.println("Error fetching user package data for " + userId + ": " + ex.getMessage());
//                    return null;
//                });
//    }
//
//    @Override
//    @Async("taskExecutor")
//    public CompletableFuture<Integer> getRemainingUploads(String firebaseUid) {
//        return getUserPackageData(firebaseUid)
//                .thenApply(packageData -> {
//                    int limit = PackageFactory.create(packageData.getUserPackageEnum()).getDailyUploadLimit();
//
//                    LocalDate today = LocalDate.now(ZoneId.systemDefault());
//                    LocalDate lastUpload = null;
//
//                    // Pretvaramo Google Cloud Timestamp u LocalDate za provjeru datuma
//                    if (packageData.getLastUploadDate() != null) {
//                        lastUpload = packageData.getLastUploadDate().toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
//                    }
//
//                    int uploadedToday;
//                    // Ako je novi dan ili nikad nije uploadano, brojač je 0 za danas
//                    if (lastUpload == null || !lastUpload.isEqual(today)) {
//                        uploadedToday = 0;
//                    } else {
//                        uploadedToday = packageData.getCurrentDailyUploadCount();
//                    }
//
//                    return limit - uploadedToday;
//                });
//    }
//
//    @Override
//    @Async("taskExecutor")
//    public CompletableFuture<UserPackageData> getUserPackageData(String firebaseUid) {
//        return userPackageDataRepository.findByFirebaseUid(firebaseUid)
//                .thenApply(data -> data.orElseThrow(() -> new IllegalArgumentException(USER_PACKAGE_DATA_NOT_FOUND)));
//    }
//
//    @Override
//    @Async("taskExecutor")
//    public CompletableFuture<Boolean> canChangePackage(String firebaseUid) {
//        return getUserPackageData(firebaseUid)
//                .thenApply(data -> new PackageContext(data).canChangePackage());
//    }
//
//    @Override
//    @Async("taskExecutor")
//    public CompletableFuture<Void> changeUserPackage(String firebaseUid, UserPackage newUserPackage) {
//        return getUserPackageData(firebaseUid)
//                .thenCompose(data -> {
//                    PackageContext context = new PackageContext(data);
//
//                    if (!context.canChangePackage()) {
//                        return CompletableFuture.failedFuture(
//                                new IllegalStateException("You can only change your package once every 24 hours.")
//                        );
//                    }
//
//                    data.setUserPackage(newUserPackage.name());
//
//                    // Postojeća logika za promjenu paketa (Timestamp je već korišten)
//                    LocalDateTime now = LocalDateTime.now();
//                    com.google.cloud.Timestamp timestampNow = com.google.cloud.Timestamp.of(java.util.Date
//                            .from(now.atZone(java.time.ZoneId.systemDefault()).toInstant()));
//                    data.setLastPackageChangeDateTime(timestampNow);
//                    data.setNextEligibleChangeDateTime(com.google.cloud.Timestamp.ofTimeSecondsAndNanos(
//                            timestampNow.getSeconds() + 86400, timestampNow.getNanos()));
//
//                    // Ovdje je promjena: pretvaramo CompletableFuture<UserPackageData> u CompletableFuture<Void>
//                    return userPackageDataRepository.save(data).thenApply(savedData -> null);
//                });
//    }
//
//    @Override
//    @Async("taskExecutor")
//    public CompletableFuture<LocalDateTime> getNextEligibleChange(String firebaseUid) {
//        return getUserPackageData(firebaseUid)
//                .thenApply(data -> new PackageContext(data).getNextEligibleChange());
//    }
//
//    @Override
//    @Async("taskExecutor")
//    public CompletableFuture<UserPackage> getUserPackage(String firebaseUid) {
//        return getUserPackageData(firebaseUid)
//                .thenApply(UserPackageData::getUserPackageEnum);
//    }
//
//    @Override
//    @Async("taskExecutor")
//    public CompletableFuture<UserPackageData> createUserPackageData(String firebaseUid, UserPackage initialPackage) {
//        UserPackageData newUserPackageData = new UserPackageData();
//        newUserPackageData.setFirebaseUid(firebaseUid);
//        newUserPackageData.setUserPackage(initialPackage.name());
//        // Inicijaliziraj nova polja za brojač
//        newUserPackageData.setCurrentDailyUploadCount(0);
//        newUserPackageData.setLastUploadDate(null); // Ili Timestamp.now() ako želiš inicijalizirati na trenutni datum
//
//        // ... (ostatak implementacije createUserPackageData) ...
//        return userPackageDataRepository.save(newUserPackageData);
//    }
//}