package hr.algebra.nrako.photoapp_backend.dto;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPackageDto {
    private String packageType;
    private String nextEligibleChange;
    private int uploadsLeft;
}

