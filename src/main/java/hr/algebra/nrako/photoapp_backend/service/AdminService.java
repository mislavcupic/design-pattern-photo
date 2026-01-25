package hr.algebra.nrako.photoapp_backend.service;

import hr.algebra.nrako.photoapp_backend.domain.User;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AdminService {
    CompletableFuture<List<User>> getAllUsers();
    CompletableFuture<User> getUserByUid(String uid);
    CompletableFuture<List<UserPackageData>> getAllUserPackages();
    CompletableFuture<UserPackageData> getUserPackageByUid(String uid);

    CompletableFuture<List<Object>> updateUserRole(String uid, String newRole);
}