package hr.algebra.nrako.photoapp_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhotoUsageDto {
    private int numberOfPhotos;
    private long totalSizeInMB;
    private int dailyUploadCount;
}

