package hr.algebra.nrako.photoapp_backend.asynchelper;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.service.UserPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class AsyncHelperDataUserPackage {
    private final UserPackageService userPackageService;

    public UserPackage getUserPackage( String uuid) throws ExecutionException, InterruptedException {
        return userPackageService.getUserPackage(uuid).get();
    }

    public Integer getRemainingUploads(String firebaseUid) throws ExecutionException, InterruptedException {
        return userPackageService.getRemainingUploads(firebaseUid).get();
    }

    public UserPackageData getUserPackageData(String firebaseUid) throws ExecutionException, InterruptedException {
        return userPackageService.getUserPackageData(firebaseUid).get();
    }

    public Boolean canChangePackage(String firebaseUid) throws ExecutionException, InterruptedException {
        return userPackageService.canChangePackage(firebaseUid).get();
    }
    public Void changeUserPackage(String firebaseUid, UserPackage newUserPackage) throws ExecutionException, InterruptedException {
        return userPackageService.changeUserPackage(firebaseUid, newUserPackage).get();
    }
    public LocalDateTime getNextEligibleChange(String firebaseUid) throws ExecutionException, InterruptedException {
        return userPackageService.getNextEligibleChange(firebaseUid).get();
    }
    public Void createUserPackageData(String firebaseUid, UserPackage initialPackage) throws ExecutionException, InterruptedException {
        return userPackageService.createUserPackageData(firebaseUid, initialPackage).get();
    }

}
