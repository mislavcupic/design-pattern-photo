package hr.algebra.nrako.photoapp_backend.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; // Važno!

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhotoUploadDto {
    private String description;
    private List<String> hashtags; // List<String>
    private Boolean isPrivate; // Koristiš isPrivate, pa zadržimo to
}
