package hr.algebra.nrako.photoapp_backend.service;

public interface UserDeletionStrategy {
    void delete(String uid) throws Exception;
}
