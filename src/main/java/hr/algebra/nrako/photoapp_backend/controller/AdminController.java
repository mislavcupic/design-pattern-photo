package hr.algebra.nrako.photoapp_backend.controller;

import hr.algebra.nrako.photoapp_backend.asynchelper.AsyncHelperAdmin;
import hr.algebra.nrako.photoapp_backend.domain.User;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AsyncHelperAdmin asyncHelperAdmin;

    public AdminController(AsyncHelperAdmin asyncHelperAdmin) {
        this.asyncHelperAdmin = asyncHelperAdmin;
    }

    @GetMapping("/users/all")
    public ResponseEntity<List<User>> getAllUsers() throws ExecutionException, InterruptedException {
        List<User> users = asyncHelperAdmin.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{uid}")
    public ResponseEntity<User> getUserByUid(@PathVariable String uid) throws ExecutionException, InterruptedException {
        User user = asyncHelperAdmin.getUserByUid(uid);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/user-packages/all")
    public ResponseEntity<List<UserPackageData>> getAllUserPackages() throws ExecutionException, InterruptedException {
        List<UserPackageData> packages = asyncHelperAdmin.getAllUserPackages();
        return ResponseEntity.ok(packages);
    }

    @GetMapping("/user-packages/{uid}")
    public ResponseEntity<UserPackageData> getUserPackageByUid(@PathVariable String uid) throws ExecutionException, InterruptedException {
        UserPackageData userPackage = asyncHelperAdmin.getUserPackageByUid(uid);
        return ResponseEntity.ok(userPackage);
    }
}
