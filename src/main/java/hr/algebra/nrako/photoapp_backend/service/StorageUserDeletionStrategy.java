package hr.algebra.nrako.photoapp_backend.service;

import org.springframework.stereotype.Component;

@Component
public class StorageUserDeletionStrategy implements UserDeletionStrategy {

    private final PhotoService photoService;

    public StorageUserDeletionStrategy(PhotoService photoService) {
        this.photoService = photoService;
    }

    @Override
    public void delete(String uid) {
        photoService.deleteAllPhotos(uid);
    }
}
