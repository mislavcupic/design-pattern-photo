package hr.algebra.nrako.photoapp_backend.dto;


public class LoginRequest {
    private String email;
    private String password;
    private String idToken; // Promijenjeno s password ili dodano
    private String refreshToken; // Dodano refreshToken
    private String userType;
    private String userPackage;

    public LoginRequest(String email, String password, String idToken, String refreshToken,String userType, String userPackage) {
        this.email = email;
        this.password = password;
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.userType = userType;
        this.userPackage = userPackage;
    }
    public LoginRequest() {

    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getPassword() {
        return password;
    }

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

    public void setPassword(String password) {
        this.password = password;


    }
}


