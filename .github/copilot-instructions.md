# Copilot Instructions — Patient Management System (PMS)

This document defines the architecture, coding standards, HIPAA compliance rules, and best practices for the PMS project. GitHub Copilot (and all contributors) **must** follow these instructions when generating or reviewing code.

---

## 1. Project Overview

PMS is a **HIPAA-compliant Patient Management System** built with:

- **Java 25**, **Spring Boot 4.0.3**, **Spring Modulith 2.0.3**
- **PostgreSQL 16** with **Flyway** migrations
- **Spring Security** with stateless JWT (HS512) authentication
- **AES-256-GCM** encryption for all PHI fields at rest
- **Bucket4j** for IP-based rate limiting
- **Lombok** for boilerplate reduction
- **Jakarta Bean Validation** for input validation

---

## 2. Architecture

### 2.1 Modular Monolith (Spring Modulith)

The codebase is organized as a **modular monolith** with enforced module boundaries. Each top-level package under `com.harak.pms` is a module:

```
com.harak.pms
├── audit/        — Audit trail logging (HIPAA §164.312(b))
├── common/       — Cross-cutting: global exception handler, shared utilities
├── encryption/   — AES-256-GCM encryption engine for PHI
├── patient/      — Core domain: patient CRUD with encrypted PHI
├── security/     — Spring Security, JWT, rate limiting, CORS, HSTS
│   └── jwt/      — JWT provider, refresh tokens, scheduled maintenance
└── user/         — User management, authentication, account lockout
```

**Rules:**
- Each module exposes its API through its top-level package classes only.
- Modules communicate through Spring-managed beans injected via constructor injection (`@RequiredArgsConstructor`).
- Do **not** create circular dependencies between modules.
- When adding a new module, create it as a new top-level package under `com.harak.pms`.

### 2.2 Layered Structure Within Each Module

Each module follows this internal structure (not all layers are required for every module):

```
module/
├── Entity.java              — JPA @Entity (domain model)
├── EntityRepository.java    — Spring Data JPA repository interface
├── EntityService.java       — @Service with business logic
├── EntityController.java    — @RestController with REST endpoints
├── CreateEntityRequest.java — Validated request DTO (Java record)
├── UpdateEntityRequest.java — Validated request DTO (Java record)
├── EntityResponse.java      — Response DTO with PHI masking (Java record)
└── EntityNotFoundException.java — Domain-specific exception
```

### 2.3 Key Architectural Decisions

| Decision | Rationale |
|----------|-----------|
| Stateless JWT (no sessions) | Eliminates session fixation/hijacking; horizontal scaling friendly |
| Refresh token rotation | Prevents replay attacks; old token revoked on each refresh |
| AES-GCM with random IV per field | Non-deterministic encryption prevents frequency analysis |
| JPA `AttributeConverter` for PHI | Transparent encryption; business code never handles ciphertext |
| `Propagation.REQUIRES_NEW` for audit | Audit logs persist even if the main transaction rolls back |
| Soft deletes (`deleted` flag) | HIPAA §164.530(j) mandates 6-year data retention |
| `ddl-auto: validate` | Schema changes only through version-controlled Flyway migrations |
| BCrypt cost factor 14 | ~1 sec per hash; strong brute-force resistance for HIPAA §164.312(d) |

---

## 3. Code Style — Google Java Style Guide

Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) with the adaptations listed below.

### 3.1 Formatting

- **Indentation:** 4 spaces (no tabs).
- **Column limit:** 120 characters.
- **Braces:** K&R style (opening brace on the same line).
- **One statement per line.** No multi-statement lines.
- **Blank lines:** One blank line between methods. Two blank lines between sections in a class (fields → constructors → methods).
- **Imports:** No wildcard imports (`*`). Organize in this order:
  1. `com.harak.pms.*` (project)
  2. Third-party libraries
  3. `java.*` / `javax.*` / `jakarta.*`
  4. Static imports last

### 3.2 Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Packages | All lowercase, singular nouns | `com.harak.pms.patient` |
| Classes | `UpperCamelCase` | `PatientService` |
| Methods | `lowerCamelCase`, verb-first | `createPatient()`, `findByUsername()` |
| Constants | `UPPER_SNAKE_CASE` | `MAX_FAILED_ATTEMPTS` |
| Local variables | `lowerCamelCase` | `accessToken`, `clientIp` |
| Type parameters | Single uppercase letter or `UpperCamelCase` | `<T>`, `<ResponseT>` |
| DTOs (request) | `VerbEntityRequest` | `CreatePatientRequest` |
| DTOs (response) | `EntityResponse` | `PatientResponse` |
| Exceptions | `EntityNotFoundException` | `PatientNotFoundException` |
| Database columns | `snake_case` | `medical_record_number` |
| REST paths | Lowercase, plural nouns, kebab-case | `/api/patients`, `/api/auth/change-password` |

### 3.3 Class Member Ordering

Follow Google's recommended ordering within a class:

1. Static constants (`private static final`)
2. Instance fields (injected dependencies first, then state)
3. Constructors (use Lombok `@RequiredArgsConstructor` — see §4)
4. Public methods
5. Package-private / protected methods
6. Private methods

---

## 4. Lombok Usage — Prefer Lombok for All Boilerplate

Use [Project Lombok](https://projectlombok.org/) to eliminate boilerplate. **Never** write code by hand that Lombok can generate.

### 4.1 Required Lombok Annotations by Class Type

| Class Type | Annotations |
|------------|-------------|
| **JPA Entity** | `@Entity`, `@Table`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder` |
| **Service** | `@Service`, `@RequiredArgsConstructor`, `@Slf4j` |
| **Controller** | `@RestController`, `@RequestMapping`, `@RequiredArgsConstructor` |
| **Configuration** | `@Configuration`, `@RequiredArgsConstructor` |
| **Component** | `@Component`, `@RequiredArgsConstructor`, `@Slf4j` |
| **Filter** | `extends OncePerRequestFilter`, `@Component`, `@Slf4j` |
| **DTO (request/response)** | Java `record` (Lombok not needed — records are immutable by design) |

### 4.2 Lombok Rules

- **Always** use `@RequiredArgsConstructor` for constructor injection. Never write constructors manually for Spring beans.
- **Always** use `@Slf4j` for logging. Never declare `private static final Logger log = ...` manually.
- **Always** use `@Builder` on JPA entities to create instances in services.
- **Always** use `@Getter` and `@Setter` on JPA entities. Never write getters/setters by hand.
- **Never** use `@Data` on JPA entities (it generates `equals()`/`hashCode()` based on all fields, which breaks Hibernate proxies).
- **Use** `@Builder.Default` when an entity field needs a default value (e.g., `private boolean deleted = false`).
- **Use** Java `record` for DTOs (request/response objects) — they are inherently immutable and concise.

### 4.3 Example: Entity

```java
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Convert(converter = PhiEncryptionConverter.class)
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;
}
```

### 4.4 Example: Service

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {
    private final PatientRepository patientRepository;
    // constructor auto-generated by @RequiredArgsConstructor
}
```

---

## 5. Javadoc — Comprehensive Documentation on All Public Methods

**Every public method, class, and interface must have a Javadoc comment.** This is non-negotiable for HIPAA-regulated code where auditability and traceability are required.

### 5.1 Javadoc Rules

1. **All public classes** must have a class-level Javadoc describing purpose and HIPAA relevance (if applicable).
2. **All public methods** must have a Javadoc with:
   - A summary sentence (one line, ends with a period).
   - `@param` for every parameter.
   - `@return` describing the return value (omit for `void`).
   - `@throws` for every checked and significant unchecked exception.
3. **Private methods** should have a short `//` comment if their purpose is not obvious, but do not require full Javadoc.
4. **Records** should have Javadoc on the record declaration describing the DTO's purpose. Individual record components use `@param` in the record Javadoc.
5. **Repository interfaces** — document custom query methods; standard Spring Data derived queries do not require Javadoc.

### 5.2 Javadoc Format

Follow Google Java Style Guide §7 (Javadoc):

```java
/**
 * Creates a new patient record with encrypted PHI fields.
 *
 * <p>The medical record number is checked for uniqueness by scanning all
 * active (non-deleted) patients, since MRN is encrypted with AES-GCM
 * (non-deterministic) and cannot be queried directly via SQL.
 *
 * @param request the validated patient creation request containing PHI fields.
 * @return the persisted {@link Patient} entity with encrypted fields.
 * @throws IllegalArgumentException if a patient with the given MRN already exists.
 */
public Patient createPatient(CreatePatientRequest request) { ... }
```

### 5.3 Javadoc on Records

```java
/**
 * Request DTO for creating a new patient record.
 *
 * <p>All PHI fields are validated before reaching the service layer.
 * SSN is validated against the {@code XXX-XX-XXXX} format.
 *
 * @param firstName           the patient's first name (1–100 characters).
 * @param lastName            the patient's last name (1–100 characters).
 * @param ssn                 the patient's SSN in {@code XXX-XX-XXXX} format.
 * @param email               the patient's email address (optional).
 * @param dateOfBirth         the patient's date of birth in {@code YYYY-MM-DD} format.
 * @param medicalRecordNumber the unique medical record number (1–50 characters).
 */
public record CreatePatientRequest( ... ) { }
```

### 5.4 Javadoc on Controller Endpoints

```java
/**
 * Retrieves a patient record by its unique identifier.
 *
 * <p>The SSN is masked in the response (only last 4 digits visible).
 * Access is restricted to ADMIN, DOCTOR, and NURSE roles.
 * The access event is recorded in the audit trail.
 *
 * @param id          the UUID of the patient to retrieve.
 * @param httpRequest the HTTP request, used to extract the client IP for audit logging.
 * @return the patient data with masked SSN, wrapped in a {@code 200 OK} response.
 * @throws PatientNotFoundException if no active patient exists with the given ID.
 */
@GetMapping("/{id}")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
public ResponseEntity<PatientResponse> getPatientById(@PathVariable UUID id,
                                                       HttpServletRequest httpRequest) { ... }
```

---

## 6. HIPAA Compliance Rules for Code Generation

These rules are **mandatory**. Any code generated for this project must comply with the HIPAA Security Rule. Violations can result in regulatory fines.

### 6.1 PHI Encryption (§164.312(a)(2)(iv))

- **Every** field containing Protected Health Information (names, SSN, DOB, email, MRN, addresses, phone numbers, diagnoses, medications, etc.) **must** use `@Convert(converter = PhiEncryptionConverter.class)` on the JPA entity.
- **Never** store PHI as plaintext in the database.
- **Never** log PHI values. Log only entity IDs, action names, and usernames.
- **Never** include PHI in exception messages that could be returned to the client.
- **Never** include full SSN in API responses. Always mask to `***-**-XXXX` format using the `PatientResponse.maskSsn()` pattern.
- When creating a new entity with PHI fields, apply `@Convert(converter = PhiEncryptionConverter.class)` to **every** PHI column — do not forget any.

### 6.2 Access Control (§164.312(a)(1))

- **Every** REST endpoint that accesses PHI must have a `@PreAuthorize` annotation specifying the required roles.
- Use `hasAnyAuthority(...)` or `hasAuthority(...)` — not `hasRole()` (our roles include the `ROLE_` prefix in the database).
- The principle of **least privilege** applies: only grant access to the roles that genuinely need it.
- Admin-only operations (delete, disable user) must use `hasAuthority('ROLE_ADMIN')`.
- **Never** create an endpoint that exposes PHI without authentication and authorization.

### 6.3 Audit Logging (§164.312(b))

- **Every** controller action that creates, reads, updates, or deletes PHI must log an audit event using `AuditService.logEvent()`.
- **Every** authentication event (success, failure, lockout, token refresh, logout, password change) must be audit-logged.
- **Every** authorization failure (access denied) must be audit-logged.
- Audit log calls must include:
  - `action` — a descriptive verb string (e.g., `PATIENT_CREATED`, `LOGIN_FAILURE`)
  - `entityType` — the type of entity affected (`PATIENT`, `USER`, `TOKEN`, `ENDPOINT`)
  - `entityId` — the UUID of the affected entity (if applicable, else `null`)
  - `performedBy` — the authenticated username
  - `ipAddress` — the client IP (use `X-Forwarded-For` header fallback via `getClientIp()` helper)
  - `detail` — a human-readable description of what happened
- **Never** include PHI values in audit log detail strings. Log field names, not field values (e.g., "Updated fields: [firstName, email]", not "Updated firstName to John").
- Audit logs must use `Propagation.REQUIRES_NEW` to ensure persistence even if the main transaction fails.

### 6.4 Authentication (§164.312(d))

- Passwords must be hashed with **BCrypt at cost factor 14** or higher.
- Password complexity: minimum 8 characters, at least 1 uppercase, 1 lowercase, 1 digit, 1 special character.
- Account lockout: lock after **5 failed attempts** for **15 minutes**.
- JWT access tokens must expire in **15 minutes** or less.
- JWT refresh tokens must implement **rotation** (revoke old, issue new on each use).
- JWT signing must use **HS512** with a key of at least **64 bytes (512 bits)**.
- **Never** return detailed authentication error messages. Use generic messages like "Invalid username or password".

### 6.5 Automatic Logoff (§164.312(a)(2)(iii))

- Access tokens expire after 15 minutes. Do not increase this value.
- On password change, revoke **all** refresh tokens for the user.
- On logout, revoke **all** refresh tokens for the user.
- On account disable, revoke **all** refresh tokens for the user.

### 6.6 Transmission Security (§164.312(e)(1))

- HSTS header must be enabled with `max-age=31536000` and `includeSubDomains`.
- CSP header must restrict to `default-src 'self'`.
- `X-Frame-Options: DENY` must be set.
- CORS must be restricted to specific allowed origins — **never** use `*`.
- CSRF is disabled because we use stateless JWT authentication (no cookies carrying auth state).

### 6.7 Error Handling

- **Never** expose stack traces, internal exception messages, or binding errors to the client.
- `server.error.include-message`, `include-binding-errors`, and `include-stacktrace` must all be set to `never`.
- Use `GlobalExceptionHandler` (`@RestControllerAdvice`) to catch and sanitize all exceptions.
- Generic catch-all handler must return `"An unexpected error occurred"` and log the full exception server-side.

### 6.8 Data Integrity (§164.312(c)(1))

- All request DTOs must use Jakarta Bean Validation annotations (`@NotBlank`, `@Size`, `@Pattern`, `@Email`).
- Controller methods must annotate request bodies with `@Valid`.
- Hibernate `ddl-auto` must be set to `validate` — **never** use `update` or `create` in production.
- All schema changes must go through Flyway migration files in `src/main/resources/db/migration/`.
- Flyway `validate-on-migrate` must be `true`.

### 6.9 Soft Deletes (§164.530(j))

- Patient records must use soft deletes (`deleted = true`), never physical deletes.
- All repository queries must filter by `deleted = false` (e.g., `findByIdAndDeletedFalse()`).
- If a new entity holds PHI, it must also implement the soft-delete pattern.

---

## 7. Spring Boot & Spring Security Patterns

### 7.1 Dependency Injection

- **Always** use constructor injection via Lombok `@RequiredArgsConstructor`.
- **Never** use `@Autowired` on fields.
- **Never** use setter injection.
- Dependencies must be declared as `private final` fields.

```java
// ✅ Correct
@Service
@RequiredArgsConstructor
public class PatientService {
    private final PatientRepository patientRepository;
    private final AuditService auditService;
}

// ❌ Wrong — never do this
@Service
public class PatientService {
    @Autowired
    private PatientRepository patientRepository;
}
```

### 7.2 Transaction Management

- Service methods that modify data must be annotated with `@Transactional`.
- Read-only service methods must use `@Transactional(readOnly = true)`.
- Audit logging uses `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
- **Never** put `@Transactional` on controller methods.

### 7.3 REST Controller Conventions

- Use `@RestController` (not `@Controller`).
- Use `@RequestMapping("/api/<resource>")` at the class level for the base path.
- Return `ResponseEntity<T>` from all controller methods for explicit HTTP status control.
- Use appropriate HTTP status codes:
  - `201 Created` for POST that creates a resource.
  - `200 OK` for GET, PUT.
  - `204 No Content` for DELETE.
  - `400 Bad Request` for validation errors.
  - `401 Unauthorized` for authentication failures.
  - `403 Forbidden` for authorization failures.
  - `404 Not Found` for missing resources.
  - `429 Too Many Requests` for rate limiting.
- Use `@Valid` on all `@RequestBody` parameters.

### 7.4 Repository Conventions

- Extend `JpaRepository<Entity, UUID>`.
- Use method-name-derived queries where possible (e.g., `findByIdAndDeletedFalse`).
- Use `@Query` with JPQL for complex queries.
- Use `@Modifying` with `@Query` for UPDATE/DELETE operations.
- Add `@Repository` annotation only if needed for clarity; Spring Data auto-detects repositories.

### 7.5 Configuration Properties

- Use `@Value("${property.key}")` for simple values.
- Externalize secrets via environment variables with `${ENV_VAR}` syntax.
- **Never** hard-code secrets, passwords, or encryption keys.
- Provide sensible defaults for non-sensitive config: `@Value("${key:defaultValue}")`.
- **Never** provide a default for secrets — force the deployer to set them.

### 7.6 Exception Hierarchy

```
RuntimeException
├── PatientNotFoundException              → 404
├── IllegalArgumentException              → 400 (business rule violations)
├── BadCredentialsException (Spring)      → 401
└── LockedException (Spring)              → 403
```

- Create domain-specific exceptions extending `RuntimeException` for each module.
- Name them `<Entity>NotFoundException`.
- Register handlers in `GlobalExceptionHandler`.

---

## 8. Database Conventions

### 8.1 Schema

- All table and column names use **`snake_case`**.
- Primary keys are `UUID` type — generated in application code with `UUID.randomUUID()`, not database-generated sequences.
- Timestamp columns use `TIMESTAMP` type and `Instant` in Java.
- Boolean columns use `BOOLEAN` with explicit `NOT NULL DEFAULT` values.
- PHI columns use `VARCHAR(255)` to accommodate encrypted ciphertext length.

### 8.2 Flyway Migrations

- File naming: `V<number>__<description>.sql` (double underscore).
- Each migration must be **idempotent-safe** — use `IF NOT EXISTS` where applicable.
- Include a comment at the top of each migration file explaining what it does and its HIPAA relevance.
- **Never** modify an already-applied migration. Always create a new version.
- Test migrations on a fresh database before committing.

### 8.3 JPA Entity Mapping

- Use `@Column(nullable = false)` to enforce NOT NULL in validation (mirrors DB constraint).
- Use `@Column(name = "snake_case_name")` when the Java field name differs.
- Use `@Column(columnDefinition = "UUID")` for UUID primary keys.
- Use `@PrePersist` to set `createdAt` timestamps.
- Use `@PreUpdate` to set `updatedAt` timestamps.
- Use `@Convert(converter = PhiEncryptionConverter.class)` on all PHI fields.

---

## 9. Testing Guidelines

- Place tests in `src/test/java/com/harak/pms/` mirroring the main source structure.
- Use Spring Boot test slices:
  - `@WebMvcTest` for controller tests.
  - `@DataJpaTest` for repository tests.
  - `@SpringBootTest` for integration tests.
- Test HIPAA-critical behavior:
  - PHI fields are encrypted in the database (read raw DB and verify ciphertext).
  - Unauthorized users receive `403 Forbidden`.
  - Unauthenticated users receive `401 Unauthorized`.
  - Account lockout triggers after 5 failed attempts.
  - Audit events are created for all PHI access.
  - SSN is masked in API responses.
  - Expired tokens are rejected.
- Use Spring Modulith's `@ApplicationModuleTest` to verify module boundary enforcement.

---

## 10. Security Checklist for New Features

When adding a new feature, verify:

- [ ] All PHI fields use `@Convert(converter = PhiEncryptionConverter.class)`.
- [ ] API responses mask or omit sensitive data (SSN, full DOB if needed).
- [ ] Controller endpoints have `@PreAuthorize` with appropriate roles.
- [ ] Every create/read/update/delete action is audit-logged via `AuditService.logEvent()`.
- [ ] Audit log detail does **not** contain PHI values, only field names or entity IDs.
- [ ] Request DTOs use Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Pattern`, `@Email`).
- [ ] Request bodies are annotated with `@Valid`.
- [ ] Exceptions are handled in `GlobalExceptionHandler` — no stack traces leak.
- [ ] No PHI is logged via `log.info()`, `log.debug()`, or `log.warn()`.
- [ ] New database columns are added via a Flyway migration, not Hibernate auto-DDL.
- [ ] Service methods use `@Transactional` / `@Transactional(readOnly = true)`.
- [ ] Dependencies are injected via `@RequiredArgsConstructor` (constructor injection).
- [ ] Public methods have comprehensive Javadoc with `@param`, `@return`, `@throws`.
- [ ] Soft delete pattern is used for any entity containing PHI.

---

## 11. Files and Directories to Never Modify Manually

- `target/` — build output (Maven-managed).
- `flyway_schema_history` table — Flyway-managed.
- Applied migration files in `src/main/resources/db/migration/V*` — create new versions instead.
- `.env` files containing secrets — never commit to version control.

---

## 12. Quick Reference: Annotation Cheat Sheet

```java
// === Entity ===
@Entity @Table(name = "...")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Id @Column(columnDefinition = "UUID")
@Convert(converter = PhiEncryptionConverter.class)  // PHI fields
@Builder.Default                                     // fields with defaults

// === Service ===
@Slf4j @Service @RequiredArgsConstructor
@Transactional
@Transactional(readOnly = true)
@Transactional(propagation = Propagation.REQUIRES_NEW)  // audit only

// === Controller ===
@RestController @RequestMapping("/api/...")  @RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Valid @RequestBody
@PathVariable @RequestParam

// === DTO (record) ===
@NotBlank @Size @Pattern @Email  // Jakarta Validation

// === Filter ===
@Slf4j @Component
extends OncePerRequestFilter

// === Config ===
@Configuration @RequiredArgsConstructor
@EnableMethodSecurity
@Bean

// === Scheduled ===
@EnableScheduling               // on main Application class
@Scheduled(cron = "...")
@Scheduled(fixedRate = ...)
```

---

## 13. Reference Links

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [HIPAA Security Rule — 45 CFR §164.312](https://www.ecfr.gov/current/title-45/subtitle-A/subchapter-C/part-164/subpart-C/section-164.312)
- [NIST SP 800-38D — AES-GCM](https://csrc.nist.gov/publications/detail/sp/800-38d/final)
- [OWASP Cheat Sheet — Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Cheat Sheet — Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [Spring Modulith Reference](https://docs.spring.io/spring-modulith/reference/)
- [Project Lombok Features](https://projectlombok.org/features/)
- [Bucket4j — Rate Limiting](https://bucket4j.com/)
- [Flyway Documentation](https://documentation.red-gate.com/flyway)

