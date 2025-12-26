package hr.algebra.nrako.photoapp_backend.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PhotoMetricsAspect {
    private final Counter uploadCounter;
    private final Counter errorCounter;
    private final MeterRegistry meterRegistry;

    public PhotoMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.uploadCounter = Counter.builder("photo.uploads.total").register(meterRegistry);
        this.errorCounter = Counter.builder("photo.system.errors").register(meterRegistry);
    }

    @AfterReturning("execution(* hr.algebra.nrako.photoapp_backend.service.PhotoServiceImpl.uploadPhoto(..))")
    public void trackUpload() {
        uploadCounter.increment();
    }

    @Around("execution(* hr.algebra.nrako.photoapp_backend.service.PhotoServiceImpl.downloadPhotoWithFilters(..))")
    public Object trackProcessingTime(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return joinPoint.proceed();
        } finally {
            sample.stop(meterRegistry.timer("photo.processing.duration"));
        }
    }
}