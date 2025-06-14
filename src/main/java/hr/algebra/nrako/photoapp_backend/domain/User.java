package hr.algebra.nrako.photoapp_backend.domain;


public class User {
    private Long id;
    private String username;
    private String password;
    private UserType userType; // REGISTERED, ANONYMOUS, ADMIN
    private UserPackage userPackage; // FREE, PRO, GOLD
    private String firebaseUid;
    private String email;
    private String displayName;

    public User(Long id, String username, String password, UserType userType, UserPackage userPackage, String firebaseUid, String email, String displayName) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.userType = userType;
        this.userPackage = userPackage;
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.displayName = displayName;
    }

    public User() {}

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public UserType getUserType() {
        return userType;
    }

    public UserPackage getUserPackage() {
        return userPackage;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public void setUserPackage(UserPackage userPackage) {
        this.userPackage = userPackage;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


}

