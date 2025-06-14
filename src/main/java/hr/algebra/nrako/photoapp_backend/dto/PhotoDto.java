package hr.algebra.nrako.photoapp_backend.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhotoDto {
    private Long id;
    private String filename;
    private String description;
    private String hashtags;
    private String uploadedBy;
    private String uploadDate; // Ili Timestamp, ovisno o vašoj Photo klasi
    private String fileUrl;
    private Boolean isPrivate;
}
