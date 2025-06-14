package hr.algebra.nrako.photoapp_backend.service.factory;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.packages.*;

public class PackageFactory {

    public static PackageLimit create(UserPackage userPackage) {
        return switch (userPackage) {
            case FREE -> new FreePackage();
            case PRO -> new ProPackage();
            case GOLD -> new GoldPackage();
        };
    }
}
