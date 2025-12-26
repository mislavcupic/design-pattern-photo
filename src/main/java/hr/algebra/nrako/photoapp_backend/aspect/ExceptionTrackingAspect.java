package hr.algebra.nrako.photoapp_backend.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExceptionTrackingAspect {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionTrackingAspect.class);
    private final Counter errorCounter;

    // Konstruktor ubrizgava MeterRegistry i inicijalizira brojač grešaka
    public ExceptionTrackingAspect(MeterRegistry registry) {
        this.errorCounter = registry.counter("photo.system.errors");
    }

    @AfterThrowing(pointcut = "execution(* hr.algebra.nrako.photoapp_backend.service.*.*(..))", throwing = "ex")
    public void trackServiceExceptions(JoinPoint joinPoint, Throwable ex) {
        // Svaki put kad metoda baci iznimku, povećavamo metriku za CSV
        errorCounter.increment();

        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        logger.error("!!! PHOTO-APP ERROR REPORT !!!");
        logger.error("Lokacija: {}.{}", className, methodName);
        logger.error("Poruka: {}", ex.getMessage());
    }
}