package hr.algebra.nrako.photoapp_backend.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class PhotoPerformanceAspect {
    private static final Logger logger = LoggerFactory.getLogger(PhotoPerformanceAspect.class);
    private final MeterRegistry registry;

    public PhotoPerformanceAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Pointcut("execution(* hr.algebra.nrako.photoapp_backend.service.PhotoServiceImpl.*(..))")
    public void photoServiceMethods() {}

    @Around("photoServiceMethods()")
    public Object profileAsynchronousMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();

        Object result = joinPoint.proceed();

        // Ako je metoda asinkrona (CompletableFuture), čekamo završetak za zapis metrike
        if (result instanceof CompletableFuture<?>) {
            return ((CompletableFuture<?>) result).thenApply(value -> {
                long duration = System.currentTimeMillis() - start;
                // Bilježimo trajanje u Timer metriku za CSV analizu
                registry.timer("photo.processing.duration").record(duration, TimeUnit.MILLISECONDS);
                logger.info("AOP [PERF]: Asinkrona metoda {} završena za {} ms", methodName, duration);
                return value;
            });
        }

        // Za sinkrone metode bilježimo odmah
        long duration = System.currentTimeMillis() - start;
        registry.timer("photo.processing.duration").record(duration, TimeUnit.MILLISECONDS);
        return result;
    }
}