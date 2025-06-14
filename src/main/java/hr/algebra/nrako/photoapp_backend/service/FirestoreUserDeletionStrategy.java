package hr.algebra.nrako.photoapp_backend.service;

import hr.algebra.nrako.photoapp_backend.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class FirestoreUserDeletionStrategy implements UserDeletionStrategy {

    private final UserRepository userRepository;

    public FirestoreUserDeletionStrategy(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void delete(String uid) {
        userRepository.deleteUserData(uid);
    }
}
