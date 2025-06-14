package hr.algebra.nrako.photoapp_backend.service;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.observer.PhotoUploadObserver;
import hr.algebra.nrako.photoapp_backend.repository.UserPackageDataRepository;
import hr.algebra.nrako.photoapp_backend.service.context.PackageContext;
import hr.algebra.nrako.photoapp_backend.service.factory.PackageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class UserPackageServiceImpl implements UserPackageService, PhotoUploadObserver {

    public static final String USER_PACKAGE_DATA_NOT_FOUND = "User package data not found";
    private final UserPackageDataRepository userPackageDataRepository;
    private final PhotoService photoService; // Inject PhotoService za registraciju

    // Držimo broj dnevnih uploadova po korisniku u memoriji (može biti i u bazi)
    private final Map<String, Integer> dailyUploadCounts = new ConcurrentHashMap<>();

    @PostConstruct
    public void subscribeToPhotoUploads() {
        photoService.registerObserver(this);
    }

    @Override
    public void onPhotoUploaded(String userId) {
        dailyUploadCounts.compute(userId, (key, count) -> (count == null) ? 1 : count + 1);
        System.out.println("User " + userId + " uploaded a photo. Current count: " + dailyUploadCounts.get(userId));
        // Ovdje možete implementirati logiku za provjeru limita i potencijalno ažuriranje statusa korisnika
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Integer> getRemainingUploads(String firebaseUid) {
        return getUserPackageData(firebaseUid)
                .thenApply(packageData -> {
                    int limit = PackageFactory.create(packageData.getUserPackageEnum()).getDailyUploadLimit();
                    int uploadedToday = dailyUploadCounts.getOrDefault(firebaseUid, 0);
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

                    //stavi neki drugi timestamp ako ovaj modul ne radi
                    LocalDateTime now = LocalDateTime.now();
                    com.google.cloud.Timestamp timestampNow = com.google.cloud.Timestamp.of(java.util.Date
                            .from(now.atZone(java.time.ZoneId.systemDefault()).toInstant()));
                    data.setLastPackageChangeDateTime(timestampNow);
                    data.setNextEligibleChangeDateTime(com.google.cloud.Timestamp.ofTimeSecondsAndNanos(
                            timestampNow.getSeconds() + 86400, timestampNow.getNanos()));

                    return userPackageDataRepository.save(data);
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
    public CompletableFuture<Void> createUserPackageData(String firebaseUid, UserPackage initialPackage) {
        UserPackageData newUserPackageData = new UserPackageData();
        newUserPackageData.setFirebaseUid(firebaseUid);
        newUserPackageData.setUserPackage(initialPackage.name());
        // ... (ostatak implementacije createUserPackageData) ...
        return userPackageDataRepository.save(newUserPackageData);
    }
}