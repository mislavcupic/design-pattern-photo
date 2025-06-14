package hr.algebra.nrako.photoapp_backend.observer;

public interface PhotoUploadSubject {
    void registerObserver(PhotoUploadObserver observer);
    void unregisterObserver(PhotoUploadObserver observer);
    void notifyObservers(String userId);
}