package hr.algebra.nrako.photoapp_backend.domain.packages;


import hr.algebra.nrako.photoapp_backend.domain.UserPackage;

public class FreePackage extends PackageLimit {
    @Override
    public int getDailyUploadLimit() {
        return 5;
    }

    @Override
    public long getMaxStorageSizeMB() {
        return 100;
    }

    @Override
    public UserPackage getPackageType() {
        return UserPackage.FREE;
    }
}
