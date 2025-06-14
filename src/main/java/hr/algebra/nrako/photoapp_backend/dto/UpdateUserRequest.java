package hr.algebra.nrako.photoapp_backend.dto;

public class UpdateUserRequest {
    private String userType;
    private String userPackage;

    // Getteri i setteri

    public String getUserPackage() {
        return userPackage;
    }

    public void setUserPackage(String userPackage) {
        this.userPackage = userPackage;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }
}
