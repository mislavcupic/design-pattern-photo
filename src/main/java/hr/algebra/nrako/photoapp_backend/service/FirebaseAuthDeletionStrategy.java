package hr.algebra.nrako.photoapp_backend.service;

import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class FirebaseAuthDeletionStrategy implements UserDeletionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthDeletionStrategy.class);
    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthDeletionStrategy(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public void delete(String uid) throws Exception {
        logger.info("Starting Firebase Authentication deletion for user: {}", uid);
        firebaseAuth.deleteUser(uid);
        logger.info("User deleted from Firebase Authentication: {}", uid);
    }
}