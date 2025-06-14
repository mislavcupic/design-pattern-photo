package hr.algebra.nrako.photoapp_backend.domain;

import com.google.cloud.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class Photo {


    private Long id;

    private String filename;
    private String description;
    private String hashtags;
    private String uploadedBy;
    private Timestamp uploadDate;
    private String fileUrl;
    private Boolean isPrivate = false; // Defaultno false (javno)

}

