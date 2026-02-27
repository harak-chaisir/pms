package com.harak.pms.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method for automatic audit logging via AOP.
 *
 * <p>When applied to a {@code @RestController} method, the {@link AuditAspect}
 * intercepts the method after successful execution and records an audit event
 * using {@link AuditService}. This eliminates repetitive manual
 * {@code auditService.logEvent(...)} calls in controllers.
 *
 * <p>The aspect automatically resolves the authenticated username from
 * {@link org.springframework.security.core.context.SecurityContextHolder}
 * and the client IP address from the current {@link jakarta.servlet.http.HttpServletRequest}
 * (with {@code X-Forwarded-For} fallback for proxied requests).
 *
 * <p>The entity ID is resolved in the following order:
 * <ol>
 *   <li>From a {@code @PathVariable}-annotated {@link java.util.UUID} parameter named {@code id}.</li>
 *   <li>From the {@code id()} accessor on the {@link org.springframework.http.ResponseEntity} body,
 *       if the body is a record with an {@code id} component of type {@link java.util.UUID}.</li>
 * </ol>
 *
 * <p>Usage example:
 * <pre>{@code
 * @Auditable(action = "PATIENT_CREATED", entityType = "PATIENT", detail = "Created patient record")
 * @PostMapping
 * public ResponseEntity<PatientResponse> createPatient(@Valid @RequestBody CreatePatientRequest request) {
 *     // ...
 * }
 * }</pre>
 *
 * @see AuditAspect
 * @see AuditService
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /**
     * The audit action name (e.g., {@code "PATIENT_CREATED"}, {@code "CLINICAL_RECORD_VIEWED"}).
     *
     * @return the action string recorded in the audit log.
     */
    String action();

    /**
     * The type of entity being acted upon (e.g., {@code "PATIENT"}, {@code "CLINICAL_RECORD"}).
     *
     * @return the entity type string recorded in the audit log.
     */
    String entityType();

    /**
     * A static detail message describing the action.
     *
     * <p>If left empty, the aspect will not record a detail string.
     * For dynamic details that depend on method arguments or return values,
     * use {@link #detailExpression()} instead.
     *
     * @return the detail string recorded in the audit log.
     */
    String detail() default "";

    /**
     * A Spring Expression Language (SpEL) expression for dynamic audit detail.
     *
     * <p>The expression is evaluated against a context that exposes:
     * <ul>
     *   <li>{@code #result} — the method's return value ({@link org.springframework.http.ResponseEntity}).</li>
     *   <li>{@code #args} — the method arguments array.</li>
     *   <li>Named method parameters (e.g., {@code #id}, {@code #request}, {@code #patientId}).</li>
     * </ul>
     *
     * <p>Example: {@code "'Created clinical record for patient ID: ' + #request.patientId()"}
     *
     * <p>If both {@link #detail()} and {@code detailExpression()} are set,
     * {@code detailExpression()} takes precedence.
     *
     * @return the SpEL expression to evaluate for the audit detail.
     */
    String detailExpression() default "";
}

