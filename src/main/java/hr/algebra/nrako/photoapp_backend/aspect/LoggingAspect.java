package hr.algebra.nrako.photoapp_backend.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * @Around - Presreće metodu "oko" nje (prije i poslije).
     * Pointcut cilja tvoj paket sa servisima: AdminService, UserService, UserPackageService, itd.
     */
    @Around("execution(* hr.algebra.nrako.photoapp_backend.service.*.*(..))")
    public Object logServiceMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        // Informacije o metodi koja se upravo poziva
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        logger.info("AOP [LOG]: Pokretanje metode: {}.{}", className, methodName);

        Object result;
        try {
            // joinPoint.proceed() zapravo pokreće tvoj originalni kod u servisu
            result = joinPoint.proceed();
        } catch (Exception e) {
            logger.error("AOP [LOG]: Metoda {} je bacila grešku: {}", methodName, e.getMessage());
            throw e;
        }

        long executionTime = System.currentTimeMillis() - start;

        // Logiranje završetka i vremena (pomaže kod ishoda O4 - poboljšanje performansi)
        logger.info("AOP [LOG]: Metoda {}.{} završena. Trajanje: {} ms",
                className, methodName, executionTime);

        return result;
    }
}