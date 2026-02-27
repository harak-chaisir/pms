# Patient Management System (PMS)

A HIPAA-compliant Patient Management System built with **Spring Boot 4.0**, **Spring Security**, **PostgreSQL**, and **Spring Modulith**. This application demonstrates how to build a healthcare backend that satisfies the HIPAA Security Rule's administrative, physical, and technical safeguards.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Architecture Overview](#architecture-overview)
- [HIPAA Compliance Overview](#hipaa-compliance-overview)
  - [1. Encryption of PHI at Rest](#1-encryption-of-phi-at-rest)
  - [2. Access Controls](#2-access-controls)
  - [3. Audit Controls](#3-audit-controls)
  - [4. Authentication](#4-authentication)
  - [5. Automatic Logoff](#5-automatic-logoff)
  - [6. Transmission Security](#6-transmission-security)
  - [7. Integrity Controls](#7-integrity-controls)
  - [8. Data Minimization & PHI Exposure Prevention](#8-data-minimization--phi-exposure-prevention)
  - [9. Brute-Force & Abuse Prevention](#9-brute-force--abuse-prevention)
  - [10. Secure Error Handling](#10-secure-error-handling)
  - [11. Data Retention & Soft Deletes](#11-data-retention--soft-deletes)
  - [12. Database Schema Versioning](#12-database-schema-versioning)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Environment Variables](#environment-variables)
  - [Running the Application](#running-the-application)
- [API Endpoints](#api-endpoints)
- [Key Configuration Reference](#key-configuration-reference)

---

## Tech Stack

| Layer            | Technology                     |
|------------------|--------------------------------|
| Language         | Java 25                       |
| Framework        | Spring Boot 4.0.3             |
| Architecture     | Spring Modulith               |
| Security         | Spring Security + JWT (HS512) |
| Database         | PostgreSQL 16                 |
| ORM              | Spring Data JPA / Hibernate   |
| Migrations       | Flyway                        |
| Rate Limiting    | Bucket4j                      |
| Encryption       | AES-256-GCM (javax.crypto)    |
| Password Hashing | BCrypt (cost factor 14)       |
| Build            | Maven                         |
| Containerization | Docker Compose                |

---

## Architecture Overview

The application follows a **modular monolith** architecture using Spring Modulith, with clear boundaries between modules:

```
com.harak.pms
├── audit/       — Audit logging for all security-relevant events
├── common/      — Global exception handling, shared utilities
├── encryption/  — AES-256-GCM encryption engine for PHI fields
├── patient/     — Patient CRUD, the core domain (contains PHI)
├── security/    — Spring Security config, JWT, rate limiting, CORS
│   └── jwt/     — JWT provider, refresh tokens, scheduled cleanup
└── user/        — User management, registration, login attempts
```

Each module has its own responsibility and communicates through well-defined interfaces. Spring Modulith enforces module boundaries at compile/test time.

---

## HIPAA Compliance Overview

The HIPAA Security Rule (45 CFR Part 164, Subpart C) requires covered entities and business associates to implement safeguards for electronic Protected Health Information (ePHI). Below is a detailed explanation of every safeguard implemented in this system, mapped to the relevant HIPAA sections.

---

### 1. Encryption of PHI at Rest

**HIPAA Reference:** §164.312(a)(2)(iv)

> **HIPAA Requirement:** "Implement a mechanism to encrypt and decrypt electronic protected health information."

**What is PHI?**
Protected Health Information includes any individually identifiable health information: names, SSNs, dates of birth, email addresses, and medical record numbers. In our `patients` table, **every** PHI column is encrypted before it reaches the database.

**How it works:**

The encryption pipeline has three components:

1. **`StringEncryptor`** — The core encryption engine
   - Uses **AES-256-GCM** (Galois/Counter Mode), an authenticated encryption algorithm
   - AES-256 provides confidentiality; GCM provides both authentication and integrity verification
   - A **random 12-byte Initialization Vector (IV)** is generated for each encryption operation, meaning the same plaintext encrypted twice will produce different ciphertexts (non-deterministic encryption). This prevents pattern analysis attacks
   - The ciphertext format is: `Base64(IV[12 bytes] + ciphertext + GCM auth tag[16 bytes])`
   - The encryption key is a **256-bit (32-byte) key** loaded from the `PHI_ENCRYPTION_KEY` environment variable. If this key is missing or the wrong size, the application **refuses to start** — there is no fallback to unencrypted operation

2. **`PhiEncryptionConverter`** — JPA `AttributeConverter` bridge
   - Implements `AttributeConverter<String, String>` so encryption/decryption is **transparent** to the application code
   - When Hibernate writes a field to the database → `convertToDatabaseColumn()` encrypts it
   - When Hibernate reads a field from the database → `convertToEntityAttribute()` decrypts it
   - No business logic code ever sees or handles ciphertext

3. **`Patient` entity** — Annotated PHI fields
   - Every PHI field uses `@Convert(converter = PhiEncryptionConverter.class)`:
     - `firstName`, `lastName`, `ssn`, `email`, `dateOfBirth`, `medicalRecordNumber`
   - This means the raw database contains only ciphertext for these columns

**Legacy data handling:**
The `decrypt()` method includes graceful fallback logic for legacy unencrypted data. If the data is too short to be AES-GCM ciphertext or fails Base64 decoding, it is returned as-is with a warning log. This allows a migration from unencrypted to encrypted data without downtime.

**Why AES-256-GCM?**
- AES-256 is approved by NIST (SP 800-38D) and meets HIPAA encryption requirements
- GCM mode provides **authenticated encryption** — if anyone tampers with the ciphertext, decryption will fail with an integrity error rather than silently producing garbage
- The random IV ensures semantic security: identical plaintext values produce different ciphertexts, preventing frequency analysis

---

### 2. Access Controls

**HIPAA Reference:** §164.312(a)(1)

> **HIPAA Requirement:** "Implement technical policies and procedures for electronic information systems that maintain ePHI to allow access only to those persons or software programs that have been granted access rights."

**Role-Based Access Control (RBAC):**

The system defines four roles, each with different levels of access:

| Role          | Patient Create | Patient Read | Patient Update | Patient Delete | User Admin |
|---------------|:--------------:|:------------:|:--------------:|:--------------:|:----------:|
| `ROLE_ADMIN`  | ✅             | ✅           | ✅             | ✅ (soft)      | ✅         |
| `ROLE_DOCTOR` | ✅             | ✅           | ✅             | ❌             | ❌         |
| `ROLE_NURSE`  | ✅             | ✅           | ✅             | ❌             | ❌         |
| `ROLE_USER`   | ❌             | ❌           | ❌             | ❌             | ❌         |

**How it's enforced:**

- **Method-level security** via `@PreAuthorize` annotations on every controller endpoint:
  ```java
  @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
  ```
- `@EnableMethodSecurity` is enabled in `SecurityConfig`
- The `SecurityFilterChain` requires authentication for all endpoints except `/api/auth/**`
- If a user tries to access an endpoint they lack the role for, the `CustomAccessDeniedHandler` logs the attempt to the audit trail and returns a `403 Forbidden` response

**User account management:**
- Admins can disable user accounts via `PUT /api/users/{id}/disable`
- Disabling a user also revokes all their refresh tokens, immediately cutting off access
- Disabled accounts cannot authenticate (checked via `UserDetailsService` → `user.isEnabled()`)

---

### 3. Audit Controls

**HIPAA Reference:** §164.312(b)

> **HIPAA Requirement:** "Implement hardware, software, and/or procedural mechanisms that record and examine activity in information systems that contain or use ePHI."

This is one of the most critical HIPAA requirements. The system implements a comprehensive audit trail:

**Audited events:**

| Event                      | Description                                     | Where Logged               |
|----------------------------|-------------------------------------------------|---------------------------|
| `LOGIN_SUCCESS`            | Successful user authentication                  | `AuthenticationAuditListener` |
| `LOGIN_FAILURE`            | Failed login attempt (bad credentials)          | `AuthenticationAuditListener` |
| `ACCOUNT_LOCKED`           | Account locked after 5 failed attempts          | `LoginAttemptService`      |
| `USER_REGISTERED`          | New user account created                        | `UserController`           |
| `USER_DISABLED`            | Admin disabled a user account                   | `AdminUserController`      |
| `TOKEN_REFRESH`            | JWT refresh token rotated                       | `UserController`           |
| `TOKEN_VALIDATION_FAILURE` | Invalid/expired JWT presented                   | `JwtAuthenticationFilter`  |
| `LOGOUT`                   | User logged out (tokens revoked)                | `UserController`           |
| `PASSWORD_CHANGED`         | User changed their password                     | `UserController`           |
| `ACCESS_DENIED`            | User tried to access unauthorized resource      | `CustomAccessDeniedHandler`|
| `PATIENT_CREATED`          | New patient record created                      | `PatientController`        |
| `PATIENT_VIEWED`           | Patient record viewed (by ID or MRN)            | `PatientController`        |
| `PATIENT_LIST_VIEWED`      | Patient list retrieved (with pagination info)   | `PatientController`        |
| `PATIENT_UPDATED`          | Patient record updated (lists changed fields)   | `PatientController`        |
| `PATIENT_DELETED`          | Patient record soft-deleted                     | `PatientController`        |

**Audit log structure:**

Each audit record captures:
- **`id`** — Unique UUID for the log entry
- **`action`** — What happened (e.g., `PATIENT_VIEWED`)
- **`entityType`** — What type of entity was affected (`USER`, `PATIENT`, `TOKEN`, `ENDPOINT`)
- **`entityId`** — The UUID of the affected entity (if applicable)
- **`performedBy`** — Username of the person who performed the action
- **`ipAddress`** — Client IP address (supports `X-Forwarded-For` for proxied requests)
- **`detail`** — Free-text detail (e.g., "Updated fields: [firstName, email]")
- **`timestamp`** — Exact time of the event

**Implementation details:**
- The `AuditService` uses `@Transactional(propagation = Propagation.REQUIRES_NEW)`, meaning audit logs are saved in a **separate transaction**. Even if the main business transaction rolls back, the audit log is still persisted. This is critical — you must never lose audit records
- Authentication events are captured via Spring Security's event system (`AuthenticationSuccessEvent`, `AuthenticationFailureBadCredentialsEvent`) using `@EventListener`, which is cleaner than interceptors and covers all authentication paths

---

### 4. Authentication

**HIPAA Reference:** §164.312(d)

> **HIPAA Requirement:** "Implement procedures to verify that a person or entity seeking access to ePHI is the one claimed."

**JWT-based stateless authentication:**

The system uses JSON Web Tokens (JWT) for authentication instead of server-side sessions:

1. **Login flow:**
   - User submits credentials to `POST /api/auth/login`
   - `AuthenticationManager` validates credentials using `DaoAuthenticationProvider`
   - On success, the server issues:
     - An **access token** (JWT, signed with HS512, 15-minute expiry)
     - A **refresh token** (opaque UUID, stored in database, 24-hour expiry)

2. **Token structure (Access Token):**
   - Signed with **HMAC-SHA512** using a 512-bit secret key
   - Contains: `sub` (username), `role`, `iss` (issuer: `pms-api`), `iat`, `exp`
   - Validated on every request by `JwtAuthenticationFilter`
   - The signing key must be at least 64 bytes; the application refuses to start with a shorter key

3. **Refresh token rotation:**
   - When a client uses a refresh token to get a new access token, the old refresh token is **immediately revoked** and a new one is issued
   - This is called **token rotation** and prevents replay attacks — if an attacker steals a refresh token, the legitimate user's next refresh will invalidate the stolen token

4. **Password security:**
   - Passwords are hashed with **BCrypt at cost factor 14** (approximately 1 second per hash on modern hardware)
   - Cost factor 14 was chosen because it's significantly above the minimum (10) and provides strong protection against offline brute-force attacks while still being usable
   - Password complexity requirements: minimum 8 characters, at least one uppercase, one lowercase, one digit, and one special character
   - Users cannot change their password to the same password they already have

5. **Account lockout (§164.312(d)):**
   - After **5 consecutive failed login attempts**, the account is locked for **15 minutes**
   - The `LoginAttemptService` tracks attempts and sets `lock_expires_at` on the user record
   - Locked accounts are rejected at the Spring Security `UserDetailsService` level (the `accountNonLocked` flag)
   - Successful login resets the failed attempt counter to 0
   - Account lock events are audit-logged

---

### 5. Automatic Logoff

**HIPAA Reference:** §164.312(a)(2)(iii)

> **HIPAA Requirement:** "Implement electronic procedures that terminate an electronic session after a predetermined time of inactivity."

- Access tokens expire after **15 minutes** (`jwt.expiration-ms: 900000`)
- After expiration, the token is rejected by `JwtAuthenticationFilter` and the user must use their refresh token to obtain a new access token
- Refresh tokens expire after **24 hours**
- A scheduled task (`SecurityMaintenanceTask`) purges expired and revoked refresh tokens daily at 2:00 AM
- On password change, **all refresh tokens for that user are revoked**, forcing re-authentication on all devices
- On logout, **all refresh tokens for that user are revoked**

---

### 6. Transmission Security

**HIPAA Reference:** §164.312(e)(1)

> **HIPAA Requirement:** "Implement technical security measures to guard against unauthorized access to ePHI that is being transmitted over an electronic communications network."

**HTTP security headers** configured in `SecurityConfig`:

| Header                       | Value                        | Purpose                                                                |
|------------------------------|------------------------------|------------------------------------------------------------------------|
| `Strict-Transport-Security`  | `max-age=31536000; includeSubDomains` | Forces HTTPS for 1 year; prevents SSL stripping attacks      |
| `Content-Security-Policy`    | `default-src 'self'`         | Prevents XSS by restricting resource loading to the same origin        |
| `X-Frame-Options`            | `DENY`                       | Prevents clickjacking by forbidding the page from being embedded in iframes |

**CORS configuration:**
- Only `https://localhost:3000` is allowed as an origin (restrict to your frontend domain in production)
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`
- Allowed headers: `Authorization`, `Content-Type`
- Credentials are allowed (for cookie/auth header forwarding)
- Preflight cache: 1 hour

**Stateless session management:**
- `SessionCreationPolicy.STATELESS` — no server-side session is ever created
- This eliminates session fixation and session hijacking attack vectors

> **Note:** In production, the application **must** be deployed behind a TLS-terminating reverse proxy (e.g., Nginx, AWS ALB) to provide encryption in transit. The HSTS header ensures the browser will only communicate over HTTPS after the first connection.

---

### 7. Integrity Controls

**HIPAA Reference:** §164.312(c)(1)

> **HIPAA Requirement:** "Implement policies and procedures to protect ePHI from improper alteration or destruction."

- **AES-GCM authenticated encryption** ensures data integrity. The 128-bit GCM authentication tag is appended to every ciphertext. If anyone modifies the encrypted data in the database (even a single bit), decryption will fail with an authentication error rather than silently producing corrupted data
- **Input validation** is enforced on all request DTOs using Jakarta Bean Validation:
  - Patient SSN must match `XXX-XX-XXXX` pattern
  - Date of birth must match `YYYY-MM-DD` pattern
  - Email must be a valid email format
  - All required fields are validated with `@NotBlank` and `@Size`
  - Password complexity is enforced via regex pattern
- **Database schema validation** — Hibernate is configured with `ddl-auto: validate`, meaning it verifies the schema matches the entities at startup but **never** modifies it. Schema changes are only applied through Flyway migrations
- **Flyway `validate-on-migrate: true`** — ensures migration checksums haven't been tampered with

---

### 8. Data Minimization & PHI Exposure Prevention

> **HIPAA Principle:** Limit PHI disclosure to the minimum necessary for the intended purpose.

**SSN masking in API responses:**
- The `PatientResponse` record masks SSNs before returning them to the client: `***-**-1234`
- Only the last 4 digits are visible; full SSN is never exposed via the API
- The masking happens in the `PatientResponse.from()` factory method — it is impossible to accidentally return the full SSN through this DTO

**Server error information suppression:**
```yaml
server:
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
```
- Stack traces, internal error messages, and binding errors are **never** exposed to the client
- The `GlobalExceptionHandler` returns sanitized error responses for all exception types
- Generic exceptions return: `"An unexpected error occurred"` — no internal details leaked

**Null field suppression:**
- Jackson is configured with `default-property-inclusion: non_null`, so null fields are omitted from JSON responses, reducing unnecessary data exposure

---

### 9. Brute-Force & Abuse Prevention

The system implements defense in depth against authentication attacks:

**Layer 1: Account lockout (per-user)**
- 5 failed attempts → 15-minute lockout
- Tracked at the database level (survives application restarts)
- Audit-logged when triggered

**Layer 2: IP-based rate limiting (per-IP)**
- All `/api/auth/**` endpoints are rate-limited to **20 requests per minute per IP** using Bucket4j
- Uses a token bucket algorithm with greedy refill
- Returns `429 Too Many Requests` with a `Retry-After: 60` header when exceeded
- The rate limit bucket cache is cleared every 10 minutes by `SecurityMaintenanceTask` to prevent unbounded memory growth
- Rate limiting operates at a different layer than account lockout — it protects against credential stuffing attacks that target many accounts from a single IP

**Layer 3: JWT validation**
- Invalid or expired tokens are rejected immediately with a `401 Unauthorized` response
- Failed token validations are audit-logged as `TOKEN_VALIDATION_FAILURE`

---

### 10. Secure Error Handling

The `GlobalExceptionHandler` ensures that all error responses follow a consistent, sanitized format:

| Exception Type                     | HTTP Status | Response                                                    |
|------------------------------------|-------------|-------------------------------------------------------------|
| `MethodArgumentNotValidException`  | 400         | Field-by-field validation errors (no internal details)      |
| `IllegalArgumentException`         | 400         | The exception message (used for business rule violations)   |
| `BadCredentialsException`          | 401         | `"Invalid username or password"` (generic, no user enumeration) |
| `LockedException`                  | 403         | `"Account is locked due to too many failed login attempts"` |
| `PatientNotFoundException`         | 404         | The exception message                                       |
| `Exception` (catch-all)            | 500         | `"An unexpected error occurred"` (logged server-side only)  |

**Why this matters:** Information leakage through error messages can help attackers enumerate users, discover system internals, or craft targeted attacks. Every error response is designed to give the client enough information to fix their request without revealing system implementation details.

---

### 11. Data Retention & Soft Deletes

> **HIPAA Requirement (§164.530(j)):** Records must be retained for 6 years from the date of creation or the date when it was last in effect.

- Patient records are **never physically deleted**. The `DELETE /api/patients/{id}` endpoint performs a **soft delete** by setting `deleted = true`
- All queries filter out soft-deleted records: `findByIdAndDeletedFalse()`, `findAllByDeletedFalse()`
- This ensures compliance with HIPAA's data retention requirements and allows recovery of accidentally deleted records
- Only users with `ROLE_ADMIN` can perform deletes
- Soft-delete actions are audit-logged

---

### 12. Database Schema Versioning

All database changes are managed through **Flyway migrations**, providing:

- **Reproducibility** — the exact schema can be recreated from version-controlled migration scripts
- **Auditability** — every schema change is tracked with a version number, timestamp, and checksum
- **Tamper detection** — `validate-on-migrate: true` ensures nobody has modified a previously applied migration
- **Baseline support** — `baseline-on-migrate: true` allows Flyway to work with existing databases

**Migration history:**

| Version | Description                          | HIPAA Relevance                                  |
|---------|--------------------------------------|--------------------------------------------------|
| V1      | Initial schema (users, patients, audit_logs) | Core tables with PHI-encrypted-at-app-layer design |
| V2      | Spring Modulith event tables         | Event-driven architecture support                |
| V3      | Refresh tokens table                 | Secure token storage with revocation support     |
| V4      | Account lockout columns              | §164.312(d) — brute-force protection             |
| V5      | Alter patients DOB to varchar        | Support for encrypted DOB storage                |
| V6      | Add audit log detail columns         | Enhanced audit trail with IP and detail fields   |

---

## Project Structure

```
src/main/java/com/harak/pms/
│
├── PmsApplication.java                      # Entry point, @EnableScheduling
│
├── audit/
│   ├── AuditLog.java                        # JPA entity for audit_logs table
│   ├── AuditLogRepository.java              # Spring Data repository
│   ├── AuditService.java                    # Centralized audit logging (REQUIRES_NEW txn)
│   └── AuthenticationAuditListener.java     # Captures Spring Security auth events
│
├── common/
│   └── GlobalExceptionHandler.java          # Sanitized error responses for all exceptions
│
├── encryption/
│   ├── StringEncryptor.java                 # AES-256-GCM encrypt/decrypt engine
│   └── PhiEncryptionConverter.java          # JPA AttributeConverter for transparent PHI encryption
│
├── patient/
│   ├── Patient.java                         # JPA entity — all PHI fields use @Convert
│   ├── PatientController.java               # REST endpoints with RBAC + audit logging
│   ├── PatientService.java                  # Business logic, soft deletes
│   ├── PatientRepository.java               # Data access with soft-delete filtering
│   ├── PatientResponse.java                 # DTO with SSN masking
│   ├── CreatePatientRequest.java            # Validated DTO for patient creation
│   ├── UpdatePatientRequest.java            # Validated DTO for patient updates
│   └── PatientNotFoundException.java        # Domain exception
│
├── security/
│   ├── SecurityConfig.java                  # Filter chain, CORS, HSTS, CSP, stateless sessions
│   ├── AuthenticationConfig.java            # UserDetailsService, AuthenticationProvider
│   ├── PasswordConfig.java                  # BCrypt with cost factor 14
│   ├── JwtAuthenticationFilter.java         # Extracts and validates JWT on every request
│   ├── RateLimitFilter.java                 # IP-based rate limiting for auth endpoints
│   ├── CustomAuthenticationEntryPoint.java  # Sanitized 401 responses
│   ├── CustomAccessDeniedHandler.java       # Audit-logged 403 responses
│   ├── AuthResponse.java                    # JWT response DTO
│   ├── RefreshTokenRequest.java             # Refresh/logout request DTO
│   └── jwt/
│       ├── JwtProvider.java                 # HS512 token generation & validation
│       ├── RefreshToken.java                # JPA entity for refresh_tokens table
│       ├── RefreshTokenRepository.java      # Data access with revocation queries
│       ├── RefreshTokenService.java         # Token rotation, revocation logic
│       └── SecurityMaintenanceTask.java     # Scheduled cleanup of tokens & rate-limit buckets
│
└── user/
    ├── User.java                            # JPA entity with lockout fields
    ├── UserController.java                  # Auth endpoints: register, login, refresh, logout
    ├── AdminUserController.java             # Admin endpoint to disable users
    ├── UserService.java                     # User CRUD, password change logic
    ├── UserRepository.java                  # Data access
    ├── LoginAttemptService.java             # Account lockout tracking (5 attempts / 15 min)
    ├── UserRegistrationRequest.java         # Validated DTO with password complexity rules
    ├── UserLoginRequest.java                # Login request DTO
    └── ChangePasswordRequest.java           # Password change request DTO
```

---

## Getting Started

### Prerequisites

- **Java 25** (or later)
- **Maven 3.9+**
- **Docker** and **Docker Compose** (for PostgreSQL)

### Environment Variables

| Variable            | Required | Description                                                      | How to Generate                   |
|---------------------|----------|------------------------------------------------------------------|-----------------------------------|
| `DB_USERNAME`       | Yes      | PostgreSQL username                                              | —                                 |
| `DB_PASSWORD`       | Yes      | PostgreSQL password                                              | —                                 |
| `PHI_ENCRYPTION_KEY`| Yes      | Base64-encoded 256-bit AES key for PHI encryption                | `openssl rand -base64 32`         |
| `JWT_SECRET`        | Yes      | Base64-encoded 512-bit key for JWT signing                       | `openssl rand -base64 64`         |

> ⚠️ **Never commit these values to version control.** Use environment variables, a secrets manager (e.g., AWS Secrets Manager, HashiCorp Vault), or a `.env` file excluded from Git.

### Running the Application

1. **Start PostgreSQL:**
   ```bash
   docker compose up -d
   ```

2. **Set environment variables:**
   ```bash
   export DB_USERNAME=root
   export DB_PASSWORD=password
   export PHI_ENCRYPTION_KEY=$(openssl rand -base64 32)
   export JWT_SECRET=$(openssl rand -base64 64)
   ```

3. **Run the application:**
   ```bash
   ./mvnw spring-boot:run
   ```

   The application will:
   - Connect to PostgreSQL on `localhost:5432`
   - Run Flyway migrations to set up the schema
   - Validate the encryption key and JWT secret
   - Start listening on port `8080`

---

## API Endpoints

### Authentication

| Method | Endpoint                  | Auth Required | Description                        |
|--------|---------------------------|:-------------:|------------------------------------|
| POST   | `/api/auth/register`      | No            | Register a new user                |
| POST   | `/api/auth/login`         | No            | Login and receive JWT tokens       |
| POST   | `/api/auth/refresh`       | No            | Refresh access token (rotates refresh token) |
| POST   | `/api/auth/logout`        | No            | Revoke all refresh tokens for user |
| PUT    | `/api/auth/change-password`| Yes           | Change password (revokes all tokens) |

### Patients

| Method | Endpoint                    | Required Role                          | Description             |
|--------|-----------------------------|----------------------------------------|-------------------------|
| POST   | `/api/patients`             | ADMIN, DOCTOR, NURSE                   | Create a patient        |
| GET    | `/api/patients/{id}`        | ADMIN, DOCTOR, NURSE                   | Get patient by ID       |
| GET    | `/api/patients`             | ADMIN, DOCTOR, NURSE                   | List patients (paged)   |
| GET    | `/api/patients/mrn/{mrn}`   | ADMIN, DOCTOR, NURSE                   | Get patient by MRN      |
| PUT    | `/api/patients/{id}`        | ADMIN, DOCTOR, NURSE                   | Update patient          |
| DELETE | `/api/patients/{id}`        | ADMIN only                             | Soft-delete patient     |

### Administration

| Method | Endpoint                     | Required Role | Description            |
|--------|------------------------------|:-------------:|------------------------|
| PUT    | `/api/users/{id}/disable`    | ADMIN         | Disable a user account |

---

## Key Configuration Reference

```yaml
# application.yml — annotated

jwt:
  secret: "${JWT_SECRET}"              # HS512 signing key (≥64 bytes, Base64)
  expiration-ms: 900000                # 15 min — HIPAA automatic logoff
  refresh-expiration-ms: 86400000      # 24 hours
  issuer: pms-api                      # JWT issuer claim for validation

phi:
  encryption-key: "${PHI_ENCRYPTION_KEY}"  # AES-256 key (exactly 32 bytes, Base64)

rate-limit:
  auth:
    requests-per-minute: 20            # Per-IP limit on /api/auth/** endpoints

spring:
  jpa:
    hibernate:
      ddl-auto: validate               # Never auto-modify schema
    show-sql: false                     # Don't log SQL (could leak PHI in logs)
    open-in-view: false                 # Prevent lazy-loading outside transactions
  jackson:
    default-property-inclusion: non_null  # Don't expose null fields

server:
  error:
    include-message: never             # Don't leak error messages
    include-binding-errors: never      # Don't leak validation internals
    include-stacktrace: never          # Don't leak stack traces
```

---

## HIPAA Compliance Checklist Summary

| HIPAA Section          | Requirement                | Implementation                                              | Status |
|------------------------|----------------------------|-------------------------------------------------------------|--------|
| §164.312(a)(1)         | Access Control              | RBAC with 4 roles, method-level `@PreAuthorize`             | ✅     |
| §164.312(a)(2)(iii)    | Automatic Logoff            | 15-min JWT expiry, token rotation, revocation on logout     | ✅     |
| §164.312(a)(2)(iv)     | Encryption at Rest          | AES-256-GCM on all PHI fields via JPA converter             | ✅     |
| §164.312(b)            | Audit Controls              | Comprehensive audit trail for all security-relevant events  | ✅     |
| §164.312(c)(1)         | Integrity Controls          | GCM auth tags, input validation, Flyway checksums           | ✅     |
| §164.312(d)            | Authentication              | JWT + BCrypt(14) + account lockout after 5 failures         | ✅     |
| §164.312(e)(1)         | Transmission Security       | HSTS, CSP, X-Frame-Options, restrictive CORS                | ✅     |
| §164.530(j)            | Data Retention              | Soft deletes, no physical record destruction                | ✅     |

---

> **Disclaimer:** This project implements many of the technical safeguards required by HIPAA. However, HIPAA compliance is a comprehensive program that also includes administrative safeguards (policies, training, risk assessments), physical safeguards (facility access controls), and organizational requirements (BAAs, breach notification procedures). This application addresses the **technical** layer — a complete compliance program requires additional administrative and organizational measures.









