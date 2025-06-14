package hr.algebra.nrako.photoapp_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;



@SpringBootApplication
@EnableAsync

public class PhotoappApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhotoappApplication.class, args);
    }


}
