package hr.algebra.nrako.photoapp_backend.dto;

import hr.algebra.nrako.photoapp_backend.domain.UserPackage;
import hr.algebra.nrako.photoapp_backend.domain.UserType;

public class UserDto {
    private Long id;
    private String email; // Uklonjeno username i password ako se ne koriste izravno
    private UserType userType;
    private UserPackage userPackage;
    private String firebaseUid;
    private String displayName;

    public UserDto(Long id, String email, UserType userType, UserPackage userPackage, String firebaseUid, String displayName) {
        this.id = id;
        this.email = email;
        this.userType = userType;
        this.userPackage = userPackage;
        this.firebaseUid = firebaseUid;
        this.displayName = displayName;
    }

    public UserDto() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public UserPackage getUserPackage() {
        return userPackage;
    }

    public void setUserPackage(UserPackage userPackage) {
        this.userPackage = userPackage;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}

