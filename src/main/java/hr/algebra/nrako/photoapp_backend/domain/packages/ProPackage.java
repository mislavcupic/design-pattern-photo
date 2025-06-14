package hr.algebra.nrako.photoapp_backend.domain.packages;
import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
public class ProPackage extends PackageLimit  {

    @Override
    public int getDailyUploadLimit() {
        return 20;
    }

    @Override
    public long getMaxStorageSizeMB() {
        return 500;
    }

    @Override
    public UserPackage getPackageType() {
        return UserPackage.PRO;
    }
}
