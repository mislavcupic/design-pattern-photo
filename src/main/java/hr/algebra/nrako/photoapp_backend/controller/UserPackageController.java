package hr.algebra.nrako.photoapp_backend.controller;

import hr.algebra.nrako.photoapp_backend.asynchelper.AsyncHelperDataUserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.service.UserPackageService;
import hr.algebra.nrako.photoapp_backend.util.FirebaseTokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

@CrossOrigin(origins = "http://localhost:3000", allowedHeaders = {"Authorization", "Content-Type"}, methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS}, allowCredentials = "true")
@RestController
@RequestMapping("/user-package")
public class UserPackageController {

    private final AsyncHelperDataUserPackage asyncHelperData;
    private final FirebaseTokenUtils firebaseTokenUtils;

    public UserPackageController(UserPackageService userPackageService, AsyncHelperDataUserPackage asyncHelperData, FirebaseTokenUtils firebaseTokenUtils, @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.asyncHelperData = asyncHelperData;
        this.firebaseTokenUtils = firebaseTokenUtils;
    }

    @GetMapping("/can-change")
    public Boolean canChangePackage(HttpServletRequest request) throws ExecutionException, InterruptedException {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return asyncHelperData.canChangePackage(uid);
    }

    @GetMapping("/user-package-data")
    public UserPackageData getUserPackageData(HttpServletRequest request) throws ExecutionException, InterruptedException {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return asyncHelperData.getUserPackageData(uid);
    }

    @PostMapping("/change-package")
    public Void changeUserPackage(@RequestBody UserPackage newUserPackage, HttpServletRequest request) throws ExecutionException, InterruptedException {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return asyncHelperData.changeUserPackage(uid, newUserPackage);
    }

    @GetMapping("/next-eligible-change")
    public LocalDateTime getNextEligibleChange(HttpServletRequest request) throws ExecutionException, InterruptedException {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return asyncHelperData.getNextEligibleChange(uid);
    }

    @PreAuthorize("hasRole('REGISTERED') or hasRole('ADMIN')")
    @GetMapping("/user-package")
    public UserPackage getUserPackage(HttpServletRequest request) throws ExecutionException, InterruptedException {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return asyncHelperData.getUserPackage(uid);
    }

    @GetMapping("/remaining-uploads")
    public Integer getRemainingUploads(HttpServletRequest request) throws ExecutionException, InterruptedException {
        String uid = firebaseTokenUtils.extractUidFromRequest(request);
        return asyncHelperData.getRemainingUploads(uid);
    }
}