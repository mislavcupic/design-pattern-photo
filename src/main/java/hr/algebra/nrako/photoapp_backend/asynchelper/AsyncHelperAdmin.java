package hr.algebra.nrako.photoapp_backend.asynchelper;

import hr.algebra.nrako.photoapp_backend.domain.User;
import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class AsyncHelperAdmin {

    private final AdminService adminService;

    public List<User> getAllUsers() throws ExecutionException, InterruptedException {
        return adminService.getAllUsers().get();
    }

    public User getUserByUid(String uid) throws ExecutionException, InterruptedException {
        return adminService.getUserByUid(uid).get();
    }

    public List<UserPackageData> getAllUserPackages() throws ExecutionException, InterruptedException {
        return adminService.getAllUserPackages().get();
    }

    public UserPackageData getUserPackageByUid(String uid) throws ExecutionException, InterruptedException {
        return adminService.getUserPackageByUid(uid).get();
    }

    public List<Object> updateUserRole(String uid, String newRole) throws ExecutionException, InterruptedException {
        // Pozivamo implementaciju i čekamo rezultat
        return Collections.singletonList(adminService.updateUserRole(uid, newRole).get());
    }
}

