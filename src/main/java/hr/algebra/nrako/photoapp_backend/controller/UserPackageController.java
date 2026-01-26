package hr.algebra.nrako.photoapp_backend.controller;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.service.UserPackageService;
import hr.algebra.nrako.photoapp_backend.util.FirebaseTokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/user-package")
public class UserPackageController {

    private final UserPackageService userPackageService;
    private final FirebaseTokenUtils firebaseTokenUtils;

    public UserPackageController(UserPackageService userPackageService, FirebaseTokenUtils firebaseTokenUtils) {
        this.userPackageService = userPackageService;
        this.firebaseTokenUtils = firebaseTokenUtils;
    }

    @GetMapping("/data")
    public CompletableFuture<ResponseEntity<UserPackageData>> getUserPackageData(HttpServletRequest request) {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return userPackageService.getUserPackageData(uid)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/change-package")
    public CompletableFuture<ResponseEntity<Void>> changeUserPackage(@RequestBody UserPackage newUserPackage, HttpServletRequest request) {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return userPackageService.changeUserPackage(uid, newUserPackage)
                .thenApply(v -> ResponseEntity.ok().build());
    }

    @GetMapping("/next-eligible-change")
    public CompletableFuture<ResponseEntity<LocalDateTime>> getNextEligibleChange(HttpServletRequest request) {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return userPackageService.getNextEligibleChange(uid)
                .thenApply(ResponseEntity::ok);
    }

    @PreAuthorize("hasRole('REGISTERED') or hasRole('ADMIN')")
    @GetMapping("/user-package")
    public CompletableFuture<ResponseEntity<UserPackage>> getUserPackage(HttpServletRequest request) {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return userPackageService.getUserPackage(uid)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/remaining-uploads")
    public CompletableFuture<ResponseEntity<Integer>> getRemainingUploads(HttpServletRequest request) {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        // Pretpostavka: Metoda u servisu je asinkrona
        return userPackageService.getRemainingUploads(uid)
                .thenApply(ResponseEntity::ok);
    }
}