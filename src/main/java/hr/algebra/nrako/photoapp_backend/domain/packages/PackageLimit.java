package hr.algebra.nrako.photoapp_backend.domain.packages;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;

public abstract class PackageLimit {
    public abstract int getDailyUploadLimit();
    public abstract long getMaxStorageSizeMB();
    public abstract UserPackage getPackageType();
}