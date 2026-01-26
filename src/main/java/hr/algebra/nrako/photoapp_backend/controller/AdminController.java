package hr.algebra.nrako.photoapp_backend.controller;

import hr.algebra.nrako.photoapp_backend.domain.User;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users/all")
    public CompletableFuture<ResponseEntity<List<User>>> getAllUsers() {
        return adminService.getAllUsers()
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/users/{uid}")
    public CompletableFuture<ResponseEntity<User>> getUserByUid(@PathVariable String uid) {
        // Pretpostavka: AdminService ima metodu koja vraća CompletableFuture<User>
        return adminService.getUserByUid(uid)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/user-packages/all")
    public CompletableFuture<ResponseEntity<List<UserPackageData>>> getAllUserPackages() {
        return adminService.getAllUserPackages()
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/user-packages/{uid}")
    public CompletableFuture<ResponseEntity<UserPackageData>> getUserPackageByUid(@PathVariable String uid) {
        return adminService.getUserPackageByUid(uid)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/users/{uid}/role")
    public CompletableFuture<ResponseEntity<Map<String, String>>> updateUserRole(@PathVariable String uid, @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        return adminService.updateUserRole(uid, newRole)
                .thenApply(res -> ResponseEntity.ok(Map.of("message", "Uloga uspješno ažurirana na " + newRole)))
                .exceptionally(e -> ResponseEntity.status(500).body(Map.of("error", e.getMessage())));
    }
}