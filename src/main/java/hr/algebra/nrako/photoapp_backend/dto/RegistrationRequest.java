package hr.algebra.nrako.photoapp_backend.dto;


public class RegistrationRequest {
    private String email;
    private String password;
    private String displayName;
    private String userPackage;
    private String idToken;

    public RegistrationRequest(String email, String password, String displayName, String userPackage, String idToken) {
        this.email = email;
        this.password = password;
        this.displayName = displayName;
        this.userPackage = userPackage;
        this.idToken = idToken;
    }
    public RegistrationRequest() {}

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }



    public String getUserPackage() {
        return userPackage;
    }

    public void setUserPackage(String userPackage) {
        this.userPackage = userPackage;
    }

    public String getIdToken() {
        return idToken;
    }
}

