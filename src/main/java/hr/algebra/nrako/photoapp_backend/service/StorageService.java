package hr.algebra.nrako.photoapp_backend.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

public interface StorageService {
    String uploadPhoto(MultipartFile file, String filename);
    void deletePhoto(String filename);
    InputStream downloadPhotoAsStream(String filename);
    byte[] downloadPhotoAsBytes(String filename); // <-- DODANA LINIJA
}
