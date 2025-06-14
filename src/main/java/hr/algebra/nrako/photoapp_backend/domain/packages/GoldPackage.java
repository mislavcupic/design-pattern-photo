package hr.algebra.nrako.photoapp_backend.domain.packages;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;

public class GoldPackage extends PackageLimit {
    @Override
    public int getDailyUploadLimit() {
        return 50;
    }

    @Override
    public long getMaxStorageSizeMB() {
        return 1000;
    }

    @Override
    public UserPackage getPackageType() {
        return UserPackage.GOLD;
    }
}
