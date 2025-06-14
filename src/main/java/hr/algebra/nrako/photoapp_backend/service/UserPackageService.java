package hr.algebra.nrako.photoapp_backend.service;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

public interface UserPackageService {
    CompletableFuture<UserPackageData> getUserPackageData(String firebaseUid);
    CompletableFuture<Boolean> canChangePackage(String firebaseUid);
    CompletableFuture<Void> changeUserPackage(String firebaseUid, UserPackage newUserPackage);
    CompletableFuture<LocalDateTime> getNextEligibleChange(String firebaseUid);
    CompletableFuture<UserPackage> getUserPackage(String firebaseUid);
    CompletableFuture<Void> createUserPackageData(String firebaseUid, UserPackage initialPackage);
    CompletableFuture<Integer> getRemainingUploads(String firebaseUid);

}
