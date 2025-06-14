package hr.algebra.nrako.photoapp_backend.service.context;

import hr.algebra.nrako.photoapp_backend.domain.UserPackageData;
import hr.algebra.nrako.photoapp_backend.domain.packages.PackageLimit;
import hr.algebra.nrako.photoapp_backend.service.factory.PackageFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class PackageContext {

    private final PackageLimit currentPackage;
    private final LocalDateTime lastChange;

    public PackageContext(UserPackageData userPackageData) {
        this.currentPackage = PackageFactory.create(userPackageData.getUserPackageEnum());

        // Pretvori Google Timestamp u LocalDateTime
        if (userPackageData.getLastPackageChangeDateTime() != null) {
            this.lastChange = userPackageData.getLastPackageChangeDateTime()
                    .toDate()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } else {
            this.lastChange = null;
        }
    }

    public boolean canChangePackage() {
        return lastChange == null || LocalDateTime.now().isAfter(lastChange.plusHours(24));
    }

    public LocalDateTime getNextEligibleChange() {
        return lastChange != null ? lastChange.plusHours(24) : LocalDateTime.now();
    }

    public PackageLimit getCurrentPackage() {
        return currentPackage;
    }
}
