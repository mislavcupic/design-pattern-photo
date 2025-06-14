package hr.algebra.nrako.photoapp_backend.domain;

import com.google.cloud.Timestamp;

public class UserPackageData {
    private String firebaseUid;
    private String userPackage;
    private Timestamp lastPackageChangeDateTime;
    private Timestamp nextEligibleChangeDateTime;
    private int currentDailyUploadCount; // Broj uploadova za tekući dan
    public UserPackageData() {
        // Firestore treba prazan konstruktor
    }
    private Timestamp lastUploadDate;


    public UserPackageData(String firebaseUid, String userPackage, Timestamp lastPackageChangeDateTime) {
        this.firebaseUid = firebaseUid;
        this.userPackage = userPackage;
        this.lastPackageChangeDateTime = lastPackageChangeDateTime;
        this.nextEligibleChangeDateTime = Timestamp.ofTimeSecondsAndNanos(
                lastPackageChangeDateTime.getSeconds() + 86400, // +1 dan
                lastPackageChangeDateTime.getNanos()
        );
        this.currentDailyUploadCount = currentDailyUploadCount;
        this.lastUploadDate = lastUploadDate;

    }

    // Getteri i setteri

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    public String getUserPackage() {
        return userPackage;
    }

    public void setUserPackage(String userPackage) {
        this.userPackage = userPackage;
    }

    public Timestamp getLastPackageChangeDateTime() {
        return lastPackageChangeDateTime;
    }

    public void setLastPackageChangeDateTime(Timestamp lastPackageChangeDateTime) {
        this.lastPackageChangeDateTime = lastPackageChangeDateTime;
    }

    public Timestamp getNextEligibleChangeDateTime() {
        return nextEligibleChangeDateTime;
    }

    public void setNextEligibleChangeDateTime(Timestamp nextEligibleChangeDateTime) {
        this.nextEligibleChangeDateTime = nextEligibleChangeDateTime;
    }


    public UserPackage getUserPackageEnum() {
        return UserPackage.valueOf(this.userPackage);
    }
    public void setUserPackageEnum(UserPackage userPackage) {
        this.userPackage = userPackage.name();
    }

    public int getCurrentDailyUploadCount() {
        return currentDailyUploadCount;
    }
    public void setCurrentDailyUploadCount(int currentDailyUploadCount) {
        this.currentDailyUploadCount = currentDailyUploadCount;
    }
    public Timestamp getLastUploadDate() {
        return lastUploadDate;
    }
    public void setLastUploadDate(Timestamp lastUploadDate) {
        this.lastUploadDate = lastUploadDate;
    }


}

