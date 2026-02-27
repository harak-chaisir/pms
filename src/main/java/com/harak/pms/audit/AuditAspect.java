package com.harak.pms.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

/**
 * AOP aspect that intercepts methods annotated with {@link Auditable} and
 * automatically records audit events via {@link AuditService}.
 *
 * <p>This aspect eliminates repetitive manual {@code auditService.logEvent(...)}
 * calls in REST controllers by extracting all audit context (username, IP address,
 * entity ID, detail message) from the method invocation and its return value.
 *
 * <p>The aspect fires <strong>after successful return only</strong>
 * ({@code @AfterReturning}), ensuring that failed operations (exceptions) do not
 * produce misleading audit entries. Failed access attempts are captured separately
 * by {@link AuthenticationAuditListener} and
 * {@link com.harak.pms.security.CustomAccessDeniedHandler}.
 *
 * <p><strong>Entity ID resolution order:</strong>
 * <ol>
 *   <li>A {@code @PathVariable}-annotated parameter of type {@link UUID} named {@code id}.</li>
 *   <li>The {@code id()} accessor on the {@link ResponseEntity} body (if it is a record
 *       with a {@link UUID} component named {@code id}).</li>
 * </ol>
 *
 * <p><strong>Detail resolution order:</strong>
 * <ol>
 *   <li>{@link Auditable#detailExpression()} — evaluated as a SpEL expression with access
 *       to method parameters by name and {@code #result} for the return value.</li>
 *   <li>{@link Auditable#detail()} — used as a static string if no SpEL expression is set.</li>
 * </ol>
 *
 * @see Auditable
 * @see AuditService
 * @see AuditContext
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final BeanFactory beanFactory;

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * Intercepts methods annotated with {@link Auditable} after successful execution
     * and records an audit event.
     *
     * @param joinPoint the join point representing the intercepted method invocation.
     * @param auditable the {@link Auditable} annotation metadata from the intercepted method.
     * @param result    the return value of the intercepted method.
     */
    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void auditAfterReturning(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            String username = AuditContext.getCurrentUsername();
            String ipAddress = resolveIpAddress(joinPoint);
            UUID entityId = resolveEntityId(joinPoint, result);
            String detail = resolveDetail(auditable, joinPoint, result);

            auditService.logEvent(
                    auditable.action(),
                    auditable.entityType(),
                    entityId,
                    username,
                    ipAddress,
                    detail
            );
        } catch (Exception e) {
            // Audit failures must never break the business operation.
            // Log the error and continue — the controller has already returned successfully.
            log.error("Failed to record audit event for action={}: {}",
                    auditable.action(), e.getMessage(), e);
        }
    }

    /**
     * Resolves the client IP address from the current HTTP request.
     *
     * <p>First checks the method arguments for an {@link HttpServletRequest} parameter.
     * Falls back to {@link AuditContext#getCurrentHttpRequest()} via {@code RequestContextHolder}.
     *
     * @param joinPoint the join point containing the method arguments.
     * @return the client IP address, or {@code "unknown"} if not resolvable.
     */
    private String resolveIpAddress(JoinPoint joinPoint) {
        // Check method arguments for HttpServletRequest
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof HttpServletRequest httpRequest) {
                return AuditContext.getClientIp(httpRequest);
            }
        }
        // Fall back to RequestContextHolder
        return AuditContext.getCurrentHttpRequest()
                .map(AuditContext::getClientIp)
                .orElse("unknown");
    }

    /**
     * Resolves the entity ID from the method invocation.
     *
     * <p>Checks {@code @PathVariable}-annotated {@link UUID} parameters named {@code id} first,
     * then attempts to extract the ID from the {@link ResponseEntity} body via reflection.
     *
     * @param joinPoint the join point containing the method arguments and signature.
     * @param result    the return value of the intercepted method.
     * @return the resolved entity ID, or {@code null} if not resolvable.
     */
    private UUID resolveEntityId(JoinPoint joinPoint, Object result) {
        // Strategy 1: Look for a @PathVariable UUID parameter named "id"
        UUID idFromArgs = resolveEntityIdFromArgs(joinPoint);
        if (idFromArgs != null) {
            return idFromArgs;
        }

        // Strategy 2: Extract from ResponseEntity body via id() accessor
        return resolveEntityIdFromResult(result);
    }

    /**
     * Scans method parameters for a {@code @PathVariable}-annotated {@link UUID} named {@code id}.
     *
     * @param joinPoint the join point with method signature and arguments.
     * @return the UUID value, or {@code null} if not found.
     */
    private UUID resolveEntityIdFromArgs(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            if (param.getType().equals(UUID.class)
                    && param.isAnnotationPresent(PathVariable.class)
                    && "id".equals(resolvePathVariableName(param))) {
                return (UUID) args[i];
            }
        }
        return null;
    }

    /**
     * Resolves the effective name of a {@code @PathVariable} parameter.
     *
     * <p>Uses the annotation's {@code value()} or {@code name()} attribute if set,
     * otherwise falls back to the parameter's declared name.
     *
     * @param param the method parameter annotated with {@code @PathVariable}.
     * @return the resolved parameter name.
     */
    private String resolvePathVariableName(Parameter param) {
        PathVariable annotation = param.getAnnotation(PathVariable.class);
        if (annotation != null) {
            if (!annotation.value().isEmpty()) {
                return annotation.value();
            }
            if (!annotation.name().isEmpty()) {
                return annotation.name();
            }
        }
        return param.getName();
    }

    /**
     * Attempts to extract a {@link UUID} entity ID from the {@link ResponseEntity} body.
     *
     * <p>If the body is a record with an {@code id()} component of type {@link UUID},
     * the value is returned via reflection.
     *
     * @param result the return value of the intercepted method.
     * @return the UUID from the response body, or {@code null} if not extractable.
     */
    private UUID resolveEntityIdFromResult(Object result) {
        if (!(result instanceof ResponseEntity<?> responseEntity)) {
            return null;
        }

        Object body = responseEntity.getBody();
        if (body == null) {
            return null;
        }

        try {
            var idMethod = body.getClass().getMethod("id");
            if (UUID.class.equals(idMethod.getReturnType())) {
                return (UUID) idMethod.invoke(body);
            }
        } catch (NoSuchMethodException e) {
            // Body does not have an id() method — this is expected for some responses
        } catch (Exception e) {
            log.debug("Could not extract entity ID from response body: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Resolves the audit detail string from the annotation.
     *
     * <p>If {@link Auditable#detailExpression()} is set, evaluates it as a SpEL expression.
     * Otherwise, falls back to the static {@link Auditable#detail()} string.
     *
     * @param auditable the annotation with detail configuration.
     * @param joinPoint the join point for SpEL variable resolution.
     * @param result    the method return value, exposed as {@code #result} in SpEL.
     * @return the resolved detail string, or {@code null} if neither attribute is set.
     */
    private String resolveDetail(Auditable auditable, JoinPoint joinPoint, Object result) {
        // SpEL expression takes precedence
        if (!auditable.detailExpression().isEmpty()) {
            return evaluateSpelExpression(auditable.detailExpression(), joinPoint, result);
        }

        // Static detail string
        if (!auditable.detail().isEmpty()) {
            return auditable.detail();
        }

        return null;
    }

    /**
     * Evaluates a SpEL expression with method parameters and return value in scope.
     *
     * <p>Exposes the following variables:
     * <ul>
     *   <li>{@code #result} — the method's return value.</li>
     *   <li>Named method parameters (e.g., {@code #id}, {@code #request}, {@code #patientId}).</li>
     * </ul>
     *
     * @param expression the SpEL expression string.
     * @param joinPoint  the join point for parameter name resolution.
     * @param result     the method return value.
     * @return the evaluated expression as a {@link String}.
     */
    private String evaluateSpelExpression(String expression, JoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        var context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        context.setVariable("result", result);

        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        return expressionParser.parseExpression(expression).getValue(context, String.class);
    }
}






