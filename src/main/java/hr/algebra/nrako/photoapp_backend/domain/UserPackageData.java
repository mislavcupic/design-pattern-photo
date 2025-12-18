package hr.algebra.nrako.photoapp_backend.domain;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.Exclude;
import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor // Za Firestore (prazan konstruktor)
@AllArgsConstructor // Generira konstruktor sa svih 6 polja
@Builder
@IgnoreExtraProperties
public class UserPackageData {
    private String firebaseUid;
    private String userPackage;
    private Timestamp lastPackageChangeDateTime;
    private Timestamp nextEligibleChangeDateTime;
    private int currentDailyUploadCount;
    private Timestamp lastUploadDate;

    // RUČNO DODAJEMO TVOJ STARI KONSTRUKTOR (da kod ne baca grešku koju si naveo)
    public UserPackageData(String firebaseUid, String userPackage, Timestamp lastPackageChangeDateTime) {
        this.firebaseUid = firebaseUid;
        this.userPackage = userPackage;
        this.lastPackageChangeDateTime = lastPackageChangeDateTime;
        if (lastPackageChangeDateTime != null) {
            this.nextEligibleChangeDateTime = Timestamp.ofTimeSecondsAndNanos(
                    lastPackageChangeDateTime.getSeconds() + 86400, // +1 dan
                    lastPackageChangeDateTime.getNanos()
            );
        }
        this.currentDailyUploadCount = 0; // Inicijalizacija
    }

    @Exclude // OBAVEZNO: sprječava InvocationTargetException (L15 grešku)
    public UserPackage getUserPackageEnum() {
        if (this.userPackage == null) return null;
        try {
            return UserPackage.valueOf(this.userPackage);
        } catch (Exception e) {
            return null;
        }
    }

    @Exclude
    public void setUserPackageEnum(UserPackage userPackage) {
        if (userPackage != null) {
            this.userPackage = userPackage.name();
        }
    }
}