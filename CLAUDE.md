# CLAUDE.md — BioAFIS Biometric Fingerprint Matching Service

> This file is the single source of truth for AI-assisted development of BioAFIS.
> Read it entirely before writing any code. Every decision here has a reason.

---

## 1. Project Identity

**Product:** BioAFIS — centralized biometric fingerprint matching microservice  
**Owner:** AIRB (Advanced IT & Research Burundi)  
**Purpose:** Store and serve fingerprint matching (1:1 verification + 1:N identification + deduplication) for health information systems in Burundi  
**Primary consumers:** SIDAInfo (HIV EMR), IBIPIMO (LIS) — via REST API  
**Reference document:** `docs/BioAFIS_PRD_v1.1.docx`

---

## 2. Non-Negotiable Constraints

These are hard rules. Never deviate from them, even if an alternative seems simpler.

### 2.1 Technology Stack (fixed)
| Layer | Technology | Version | Reason |
|---|---|---|---|
| Language | **Kotlin** | 1.9+ | AIRB team competency; JVM for SourceAFIS |
| Framework | **Spring Boot** | 3.3+ | Production-grade, maintainable |
| Biometric engine | **SourceAFIS** | 3.18+ | Only open-source AFIS with Java support |
| Database | **MySQL** | 8.0 | Consistent with SIDAInfo/IBIPIMO stack |
| Migration | **Flyway** | latest | Automated, versioned schema |
| API docs | **SpringDoc / OpenAPI 3** | latest | Auto-generated, always in sync |
| Containerization | **Docker + Docker Compose** | — | Single `docker compose up` deployment |
| Build | **Gradle (Kotlin DSL)** | — | Not Maven |
| Java target | **JVM 17** | — | LTS, Spring Boot 3 requirement |

**Never** introduce: Maven, Spring MVC XML config, JPA XML mapping, Lombok (use Kotlin data classes), any proprietary biometric SDK.

### 2.2 Security (non-negotiable)
- Templates stored **AES-256-GCM encrypted** in database at all times
- **Original fingerprint images are NEVER persisted** — extract template, discard image immediately
- All API endpoints require `Authorization: Bearer {API_KEY}` — no endpoint is public except `/actuator/health`
- API keys stored as **BCrypt hash** in DB — plain key shown once at creation, never again
- **No external network calls** from the application — fully self-contained, air-gap compatible
- All SQL via JPA/repositories — no raw string-concatenated queries

### 2.3 Architecture (non-negotiable)
- **Layered architecture only**: Controller → Service → Repository. Never skip layers.
- Controllers are thin: validation + delegation only, zero business logic
- Services hold all business logic — they are the only layer that touches SourceAFIS
- Repositories extend `JpaRepository` — no custom JDBC unless absolutely necessary
- **No static state** except the template cache (which has its own class)
- **No `@Autowired` field injection** — constructor injection only (Kotlin primary constructors)

---

## 3. Module Structure

```
bioafis/
├── CLAUDE.md                          ← you are here
├── docker-compose.yml
├── docker-compose.override.yml        ← local dev overrides (gitignored)
├── .env.example
├── Dockerfile
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    └── main/
        ├── kotlin/com/airb/bioafis/
        │   ├── BioAfisApplication.kt
        │   ├── config/
        │   │   ├── SecurityConfig.kt        ← API key filter bean
        │   │   ├── CacheConfig.kt           ← template cache bean
        │   │   └── OpenApiConfig.kt         ← Swagger metadata
        │   ├── security/
        │   │   ├── ApiKeyFilter.kt          ← Bearer token extraction + tenant resolution
        │   │   ├── TenantContext.kt         ← ThreadLocal tenant holder
        │   │   └── AuditLogger.kt           ← writes to audit_logs table
        │   ├── domain/
        │   │   ├── model/
        │   │   │   ├── Tenant.kt
        │   │   │   ├── Patient.kt
        │   │   │   ├── Fingerprint.kt
        │   │   │   ├── DeduplicationJob.kt
        │   │   │   └── AuditLog.kt
        │   │   └── enums/
        │   │       ├── FingerPosition.kt    ← RIGHT_INDEX, LEFT_THUMB, etc.
        │   │       ├── EnrollmentSource.kt  ← AT_VISIT, CAMPAIGN, TRANSFER
        │   │       └── JobStatus.kt         ← PENDING, RUNNING, DONE, FAILED
        │   ├── repository/
        │   │   ├── TenantRepository.kt
        │   │   ├── PatientRepository.kt
        │   │   ├── FingerprintRepository.kt
        │   │   ├── DeduplicationJobRepository.kt
        │   │   └── AuditLogRepository.kt
        │   ├── matching/
        │   │   ├── TemplateCache.kt         ← in-memory SourceAFIS cache (see §5)
        │   │   ├── TemplateEncryption.kt    ← AES-256-GCM encrypt/decrypt
        │   │   └── SourceAfisEngine.kt      ← thin wrapper around SourceAFIS calls
        │   ├── service/
        │   │   ├── EnrollmentService.kt
        │   │   ├── VerificationService.kt
        │   │   ├── IdentificationService.kt
        │   │   ├── DeduplicationService.kt
        │   │   ├── TenantService.kt
        │   │   └── EnrollmentStatsService.kt
        │   ├── api/
        │   │   ├── v1/
        │   │   │   ├── PatientController.kt
        │   │   │   └── DeduplicationController.kt
        │   │   └── admin/
        │   │       ├── TenantAdminController.kt
        │   │       ├── CacheAdminController.kt
        │   │       └── EnrollmentStatsAdminController.kt
        │   └── dto/
        │       ├── request/
        │       │   ├── EnrollRequest.kt
        │       │   ├── VerifyRequest.kt
        │       │   └── IdentifyRequest.kt
        │       └── response/
        │           ├── EnrollResponse.kt
        │           ├── VerifyResponse.kt
        │           ├── IdentifyResponse.kt
        │           └── ErrorResponse.kt
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            └── db/migration/
                ├── V1__create_tenants.sql
                ├── V2__create_patients.sql
                ├── V3__create_fingerprints.sql
                ├── V4__create_audit_logs.sql
                └── V5__create_dedup_jobs.sql
```

---

## 4. Database Schema (Flyway migrations — source of truth)

### Rules for migrations
- One concern per migration file
- Never modify an existing migration file — always add a new `Vn__description.sql`
- Always include `CREATE TABLE IF NOT EXISTS`
- All timestamps in UTC, type `DATETIME(3)` (millisecond precision)
- All foreign keys explicit with `ON DELETE CASCADE` or `ON DELETE RESTRICT` as appropriate

### Core tables

```sql
-- V1: tenants
CREATE TABLE tenants (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    api_key_hash        VARCHAR(255) NOT NULL UNIQUE,       -- BCrypt hash
    is_admin            BOOLEAN NOT NULL DEFAULT FALSE,
    config_json         JSON,                               -- threshold, max_fingers, etc.
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

-- V2: patients
CREATE TABLE patients (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    uid                 VARCHAR(100) NOT NULL,              -- caller-provided UID (preserved from SIDAInfo)
    tenant_id           BIGINT NOT NULL,
    facility_code       VARCHAR(50),
    enrollment_status   ENUM('PENDING','ENROLLED','QUALITY_FAILED','PARTIAL') NOT NULL DEFAULT 'PENDING',
    enrolled_at         DATETIME(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_uid_tenant (uid, tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT
);

-- V3: fingerprints
CREATE TABLE fingerprints (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id          BIGINT NOT NULL,
    finger_position     ENUM(
                            'RIGHT_THUMB','RIGHT_INDEX','RIGHT_MIDDLE','RIGHT_RING','RIGHT_LITTLE',
                            'LEFT_THUMB','LEFT_INDEX','LEFT_MIDDLE','LEFT_RING','LEFT_LITTLE'
                        ) NOT NULL,
    template_encrypted  LONGBLOB NOT NULL,                 -- AES-256-GCM encrypted SourceAFIS template
    quality_score       TINYINT UNSIGNED NOT NULL,         -- 0-100
    enrollment_source   ENUM('AT_VISIT','CAMPAIGN','TRANSFER') NOT NULL,
    enrolled_by         VARCHAR(100),                      -- user ID of enrolling agent
    enrolled_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_patient_finger (patient_id, finger_position),
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE
);

-- V4: audit_logs (append-only, no updates, no deletes)
CREATE TABLE audit_logs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT,
    operation           VARCHAR(50) NOT NULL,              -- ENROLL, VERIFY, IDENTIFY, DEDUP_START, etc.
    uid                 VARCHAR(100),
    ip_address          VARCHAR(45),
    response_ms         INT,
    http_status         SMALLINT,
    error_code          VARCHAR(50),
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED;

-- V5: dedup_jobs
CREATE TABLE dedup_jobs (
    id                  VARCHAR(36) PRIMARY KEY,           -- UUID
    tenant_id           BIGINT NOT NULL,
    status              ENUM('PENDING','RUNNING','DONE','FAILED') NOT NULL DEFAULT 'PENDING',
    scope_facility      VARCHAR(50),                       -- null = entire tenant
    progress_pct        TINYINT UNSIGNED NOT NULL DEFAULT 0,
    result_count        INT,
    started_at          DATETIME(3),
    completed_at        DATETIME(3),
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);
```

---

## 5. The Template Cache — Most Critical Component

The cache is the performance heart of the system. Get it right.

```kotlin
// matching/TemplateCache.kt
@Component
class TemplateCache(
    private val fingerprintRepository: FingerprintRepository,
    private val encryption: TemplateEncryption,
) {
    // tenantId → uid → list of (fingerPosition, FingerprintTemplate)
    private val cache = ConcurrentHashMap<Long, ConcurrentHashMap<String, List<CachedTemplate>>>()
    private val lock = ReentrantReadWriteLock()

    data class CachedTemplate(
        val fingerprintId: Long,
        val position: FingerPosition,
        val template: FingerprintTemplate,  // SourceAFIS object, never serialized here
    )

    @PostConstruct
    fun warmUp() {
        // Load ALL tenants, ALL patients, ALL fingers at startup
        // Log progress every 10,000 templates
        // This is acceptable: ~200K templates × 1KB = ~200MB RAM
    }

    // Read operations: use read lock (concurrent)
    fun getCandidates(tenantId: Long): Map<String, List<CachedTemplate>>

    // Write operations: use write lock (exclusive)
    fun put(tenantId: Long, uid: String, templates: List<CachedTemplate>)
    fun remove(tenantId: Long, uid: String)

    // Admin: full reload without restart
    fun reload() // acquires write lock, clears, re-loads from DB
}
```

**Cache rules:**
- `FingerprintMatcher` is created per-request (it's the probe side), **not** cached
- `FingerprintTemplate` objects for candidates ARE cached — expensive to deserialize
- Always update cache immediately after DB write — no eventual consistency
- `warmUp()` failure must cause application startup failure (throw exception, don't swallow)
- Log cache size at startup: `"Cache loaded: X templates for Y patients across Z tenants"`

---

## 6. API Design Rules

### 6.1 URL structure
```
/api/v1/patients/enroll           POST  — enroll patient
/api/v1/patients/{uid}/enroll     PUT   — re-enroll (update existing)
/api/v1/patients/verify           POST  — 1:1 verify
/api/v1/patients/identify         POST  — 1:N identify
/api/v1/patients/{uid}            GET   — enrollment status
/api/v1/patients/{uid}            DELETE — delete patient + templates
/api/v1/dedup/jobs                POST  — start dedup job
/api/v1/dedup/jobs/{jobId}        GET   — job status
/api/v1/dedup/jobs/{jobId}/results GET  — paginated results

/admin/tenants                    GET, POST
/admin/tenants/{id}               DELETE
/admin/cache/reload               POST
/admin/enrollment/stats           GET   — ?tenant_id=&facility=
/admin/enrollment/pending         GET   — ?tenant_id=&facility=
```

### 6.2 DTOs — always use data classes, always validate

```kotlin
// dto/request/EnrollRequest.kt
data class EnrollRequest(
    @field:Size(max = 100)
    val uid: String?,                           // null = generate UUID

    @field:NotEmpty
    @field:Size(max = 10)
    val fingers: List<FingerData>,

    @field:Size(max = 50)
    val facilityCode: String? = null,

    val enrollmentSource: EnrollmentSource = EnrollmentSource.AT_VISIT,

    @field:Size(max = 100)
    val enrolledBy: String? = null,
)

data class FingerData(
    @field:NotNull
    val position: FingerPosition,

    @field:NotBlank
    val imageBase64: String,                    // raw BMP/PNG as Base64

    val imageFormat: ImageFormat = ImageFormat.BMP,
)
```

### 6.3 Error responses — always structured

```kotlin
// dto/response/ErrorResponse.kt
data class ErrorResponse(
    val code: String,           // e.g. "QUALITY_TOO_LOW"
    val message: String,        // human-readable
    val details: Map<String, Any>? = null,  // e.g. { "finger": "RIGHT_INDEX", "score": 32 }
    val timestamp: Instant = Instant.now(),
)
```

**Standard error codes:**
```
INVALID_IMAGE          400  — cannot decode image
INVALID_POSITION       400  — unknown FingerPosition
QUALITY_TOO_LOW        422  — quality < tenant threshold (include actual score in details)
PATIENT_NOT_FOUND      404
UID_CONFLICT           409  — UID exists under different tenant
UNAUTHORIZED           401
FORBIDDEN              403
CACHE_NOT_READY        503  — cache still loading
INTERNAL_ERROR         500
```

### 6.4 Response envelope — never wrap in extra layers

```kotlin
// ✅ CORRECT — return the DTO directly
@PostMapping("/enroll")
fun enroll(@RequestBody @Valid req: EnrollRequest): ResponseEntity<EnrollResponse>

// ❌ WRONG — don't add a generic wrapper
@PostMapping("/enroll")
fun enroll(...): ResponseEntity<ApiResponse<EnrollResponse>>
```

---

## 7. SourceAFIS Integration Rules

```kotlin
// matching/SourceAfisEngine.kt
@Component
class SourceAfisEngine {

    // Extract template from raw image bytes
    // imageBytes: decoded from Base64 before calling this
    // Returns quality score (0-100) + serialized template bytes
    fun extractTemplate(imageBytes: ByteArray, dpi: Int = 500): ExtractionResult

    // 1:1 match — returns score 0.0–100.0
    fun match(probe: FingerprintTemplate, candidate: FingerprintTemplate): Double

    // Deserialize stored template bytes → FingerprintTemplate (for cache loading)
    fun deserialize(bytes: ByteArray): FingerprintTemplate

    // Serialize FingerprintTemplate → bytes (for DB storage before encryption)
    fun serialize(template: FingerprintTemplate): ByteArray
}

data class ExtractionResult(
    val template: FingerprintTemplate,
    val serialized: ByteArray,   // ready for encryption + DB storage
    val qualityScore: Int,       // 0-100
)
```

**SourceAFIS rules:**
- `FingerprintImage` constructor takes `width`, `height`, `pixels` (grayscale byte array) OR image bytes via `FingerprintImageOptions`
- Default DPI is 500 — always pass it explicitly; SecuGen Hamster Pro captures at 500 DPI
- Threshold for match decision: **40.0** default, configurable per tenant in `config_json`
- Never store `FingerprintTemplate` objects in DB — always serialize to bytes, then encrypt
- For 1:N search: create `FingerprintMatcher(probe)` once per request, then loop candidates

```kotlin
// Correct 1:N identification pattern in IdentificationService
fun identify(tenantId: Long, probe: FingerprintTemplate, maxResults: Int, threshold: Double): List<Candidate> {
    val matcher = FingerprintMatcher(probe)  // create ONCE
    return cache.getCandidates(tenantId)
        .flatMap { (uid, templates) ->
            templates.map { cached ->
                Candidate(uid, cached.position, matcher.match(cached.template))
            }
        }
        .filter { it.score >= threshold }
        .sortedByDescending { it.score }
        .take(maxResults)
}
```

---

## 8. Security Implementation

### 8.1 API Key Filter

```kotlin
// security/ApiKeyFilter.kt
@Component
class ApiKeyFilter(
    private val tenantRepository: TenantRepository,
    private val passwordEncoder: PasswordEncoder,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, ...) {
        // 1. Skip /actuator/health (only public endpoint)
        // 2. Extract "Bearer {key}" from Authorization header
        // 3. Hash check against tenant api_key_hash (BCrypt)
        // 4. If admin-only path: reject non-admin tenants with 403
        // 5. Put tenant in TenantContext (ThreadLocal)
        // 6. Proceed
    }
}
```

### 8.2 Tenant Isolation

Every service method that touches patient/fingerprint data **must** pass `tenantId` through to the repository query. No repository method returns data without a `tenantId` filter.

```kotlin
// ✅ CORRECT
fun findByUidAndTenantId(uid: String, tenantId: Long): Optional<Patient>

// ❌ WRONG — would leak data across tenants
fun findByUid(uid: String): Optional<Patient>
```

### 8.3 Encryption

```kotlin
// matching/TemplateEncryption.kt
@Component
class TemplateEncryption(
    @Value("\${bioafis.encryption.key}") private val keyBase64: String,
) {
    private val key: SecretKey by lazy {
        // Decode 32-byte Base64 key → AES SecretKey
        SecretKeySpec(Base64.getDecoder().decode(keyBase64), "AES")
    }

    // AES-256-GCM — generates random 12-byte IV per encryption
    // Output: IV (12 bytes) + GCM tag (16 bytes) + ciphertext
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}
```

---

## 9. Configuration (application.yml)

```yaml
# All sensitive values come from environment variables — no hardcoded secrets ever
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:bioafis}?useSSL=false&serverTimezone=UTC
    username: ${DB_USER:bioafis}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 5000

  jpa:
    hibernate:
      ddl-auto: validate          # Flyway manages schema — Hibernate only validates
    show-sql: false               # never true in production
    open-in-view: false           # always false

  flyway:
    enabled: true
    locations: classpath:db/migration

bioafis:
  encryption:
    key: ${TEMPLATE_ENCRYPTION_KEY}   # 32-byte Base64 AES key

  matching:
    default-threshold: ${MATCHING_THRESHOLD_DEFAULT:40.0}
    min-quality-score: ${MIN_QUALITY_SCORE:50}
    max-fingers-per-patient: ${MAX_FINGERS_PER_PATIENT:10}
    default-dpi: 500

  admin:
    api-key: ${ADMIN_API_KEY}          # hashed on first startup, stored in tenants table

  cache:
    reload-on-startup: ${CACHE_RELOAD_ON_STARTUP:true}

logging:
  pattern:
    console: '{"timestamp":"%d{ISO8601}","level":"%p","logger":"%c{1}","message":"%m"}%n'
  level:
    root: ${LOG_LEVEL:INFO}
    com.airb.bioafis: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      show-details: never           # don't expose internals publicly
```

---

## 10. Docker Setup

### docker-compose.yml (production)
```yaml
services:
  bioafis-app:
    build: .
    image: bioafis:latest
    restart: unless-stopped
    depends_on:
      bioafis-db:
        condition: service_healthy
    environment:
      DB_HOST: bioafis-db
      DB_PASSWORD: ${DB_PASSWORD}
      ADMIN_API_KEY: ${ADMIN_API_KEY}
      TEMPLATE_ENCRYPTION_KEY: ${TEMPLATE_ENCRYPTION_KEY}
    networks: [bioafis-net]
    expose: ["8080"]

  bioafis-db:
    image: mysql:8.0
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: ${DB_NAME:-bioafis}
      MYSQL_USER: ${DB_USER:-bioafis}
      MYSQL_PASSWORD: ${DB_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
    volumes:
      - bioafis_db_data:/var/lib/mysql
    networks: [bioafis-net]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
    # CRITICAL: DB port NOT exposed externally

  bioafis-nginx:
    image: nginx:alpine
    restart: unless-stopped
    ports: ["443:443", "80:80"]
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - /etc/ssl/bioafis:/etc/ssl/bioafis:ro
    depends_on: [bioafis-app]
    networks: [bioafis-net]

volumes:
  bioafis_db_data:

networks:
  bioafis-net:
    driver: bridge
```

### Dockerfile
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/bioafis-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=70.0", \
  "-jar", "app.jar"]
```

**Build command:**
```bash
./gradlew bootJar
docker compose up --build -d
```

---

## 11. Testing Rules

### 11.1 Test structure mirrors main
```
src/test/kotlin/com/airb/bioafis/
├── matching/
│   ├── SourceAfisEngineTest.kt       ← unit: template extraction + matching
│   └── TemplateCacheTest.kt          ← unit: cache ops with mock data
├── service/
│   ├── EnrollmentServiceTest.kt      ← unit: business rules (quality, UID logic)
│   ├── IdentificationServiceTest.kt  ← unit: threshold, ranking, multi-finger
│   └── DeduplicationServiceTest.kt   ← unit: job lifecycle
├── api/
│   ├── PatientControllerTest.kt      ← integration: full HTTP round-trip
│   └── TenantAdminControllerTest.kt  ← integration: admin auth
└── TestContainersConfig.kt           ← shared MySQL Testcontainer
```

### 11.2 Rules
- Unit tests: **JUnit 5 + Mockito-Kotlin** — mock repositories, not services
- Integration tests: **Spring Boot Test + Testcontainers (MySQL 8.0)**
- Never use H2 — MySQL 8.0 only, enum types and JSON columns differ
- Use `@TestPropertySource` to inject test secrets — never hardcode in test files
- Every service public method has a unit test
- Every API endpoint has an integration test covering: happy path + auth failure + validation failure
- Target: **> 80% line coverage** on `service/` and `matching/` packages

### 11.3 Test data conventions
```kotlin
// Use builder-style factories
object TestFixtures {
    fun enrollment(uid: String = "TEST-UID-001") = EnrollRequest(
        uid = uid,
        fingers = listOf(FingerData(FingerPosition.RIGHT_INDEX, sampleImageBase64())),
        enrollmentSource = EnrollmentSource.AT_VISIT,
    )

    fun sampleImageBase64(): String = // load from test/resources/sample_fingerprint.bmp
}
```

---

## 12. Logging & Observability

### 12.1 Logging rules
- Use **SLF4J with Logback** — never `println`, never `System.out`
- Log at correct levels:
  - `DEBUG`: internal flow, cache operations, matching scores
  - `INFO`: enrollments, identifications (include UID + tenant + response_ms)
  - `WARN`: quality rejections, cache misses, threshold near-misses
  - `ERROR`: exceptions, encryption failures, DB errors
- **Never log template bytes, image data, or raw biometric content**
- Log format: structured JSON (configured in application.yml above)

```kotlin
// ✅ CORRECT
logger.info("Identification complete: uid={} tenant={} score={} responseMs={}", uid, tenantId, score, ms)

// ❌ WRONG — never log biometric content
logger.debug("Template bytes: {}", templateBytes)
```

### 12.2 Micrometer metrics to expose
```kotlin
// Register these in services:
Counter.builder("bioafis.enroll.total").tag("tenant", tenantId).register(registry)
Counter.builder("bioafis.enroll.quality_failed").register(registry)
Timer.builder("bioafis.identify.duration").register(registry)
Gauge.builder("bioafis.cache.size") { cache.totalSize() }.register(registry)
```

---

## 13. Code Style & Conventions

### 13.1 Kotlin idioms — always use
```kotlin
// ✅ Data classes for DTOs and value objects
data class IdentifyResult(val uid: String, val score: Double, val position: FingerPosition)

// ✅ Sealed classes for typed results (no exceptions for expected failures)
sealed class MatchResult {
    data class Found(val uid: String, val score: Double) : MatchResult()
    object NotFound : MatchResult()
}

// ✅ Extension functions for mapping
fun Patient.toResponse() = PatientStatusResponse(uid = uid, status = enrollmentStatus, ...)

// ✅ Scope functions appropriately
patient.let { repo.save(it) }
config?.threshold ?: 40.0

// ❌ Never use !! (non-null assertion) — use ?: or let or require()
val threshold = config?.threshold ?: defaultThreshold
```

### 13.2 Naming
- Classes: `PascalCase`
- Functions/variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE` in companion objects
- DB column names: `snake_case`
- API JSON fields: `snake_case` (configure Jackson globally)
- Packages: `com.airb.bioafis.{module}`

### 13.3 Forbidden patterns
```kotlin
// ❌ Field injection
@Autowired lateinit var service: SomeService

// ✅ Constructor injection
@Service
class MyController(private val service: SomeService)

// ❌ Business logic in controller
@PostMapping("/enroll")
fun enroll(@RequestBody req: EnrollRequest): ResponseEntity<*> {
    val template = sourceAfis.extract(...)  // ← NO
}

// ❌ Raw SQL string concatenation
entityManager.createQuery("SELECT p FROM Patient p WHERE uid = '$uid'")  // SQL injection risk

// ❌ Storing image bytes in DB
fingerprint.imageBytes = req.imageBase64  // NEVER — only templates
```

---

## 14. Environment Variables Reference

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_HOST` | No | `bioafis-db` | MySQL host |
| `DB_PORT` | No | `3306` | MySQL port |
| `DB_NAME` | No | `bioafis` | Database name |
| `DB_USER` | No | `bioafis` | DB user |
| `DB_PASSWORD` | **Yes** | — | DB password |
| `DB_ROOT_PASSWORD` | **Yes** | — | MySQL root password (Docker only) |
| `ADMIN_API_KEY` | **Yes** | — | Master admin API key (plain text at startup) |
| `TEMPLATE_ENCRYPTION_KEY` | **Yes** | — | 32-byte Base64 AES-256 key |
| `MATCHING_THRESHOLD_DEFAULT` | No | `40.0` | Global default match threshold |
| `MIN_QUALITY_SCORE` | No | `50` | Minimum accepted quality at enrollment |
| `MAX_FINGERS_PER_PATIENT` | No | `10` | Maximum fingers per patient |
| `CACHE_RELOAD_ON_STARTUP` | No | `true` | Load all templates at boot |
| `LOG_LEVEL` | No | `INFO` | Root log level |
| `CORS_ALLOWED_ORIGINS` | No | `*` | Comma-separated allowed origins |

Generate `TEMPLATE_ENCRYPTION_KEY`:
```bash
openssl rand -base64 32
```

---

## 15. What Claude Should Always Do

When generating code for this project:

1. **Check the module structure** (§3) — put new files in the right package
2. **Check the DB schema** (§4) — never invent new columns without a Flyway migration
3. **Always use constructor injection** — never `@Autowired` on a field
4. **Always pass `tenantId`** to every repository method that touches patient data
5. **Always validate DTOs** with `@Valid` on controller parameters
6. **Always update the cache** after any DB write to fingerprints or patients
7. **Never store image bytes** — extract template immediately, discard image
8. **Always encrypt** before writing to `fingerprints.template_encrypted`
9. **Always decrypt** before passing to SourceAFIS
10. **Write the test** alongside the implementation — not "later"
11. **Return structured `ErrorResponse`** — never let exceptions propagate to the client as stack traces
12. **Use `sealed class`** for service return types when a "not found" or "quality failure" is a normal expected outcome (not exceptional)
13. **Log at INFO** for every enrollment and identification with timing

## 16. What Claude Should Never Do

- Add a new dependency without explaining why the existing stack cannot handle the need
- Use `Thread.sleep()` anywhere — use Spring's async mechanisms
- Add `@Transactional` to controller methods — only on service methods
- Return `null` from public service methods — use `Optional<T>` or sealed classes
- Skip Flyway — never use `ddl-auto: create` or `update` in any environment
- Hardcode any credential, key, or URL — always `${ENV_VAR}`
- Use `ObjectMapper` directly — configure it as a Spring bean and inject it
- Create a new endpoint without adding it to the OpenAPI docs via annotations
- Bypass the `ApiKeyFilter` for convenience during development

---

## 17. Glossary (for context in conversations)

| Term | Meaning |
|---|---|
| **SourceAFIS** | The open-source fingerprint matching library (Apache 2.0, Java/Kotlin) |
| **Template** | Mathematical representation of fingerprint minutiae — not the image |
| **Minutiae** | Ridge endings and bifurcations used for fingerprint matching |
| **1:1 Verification** | Compare probe against one specific patient by UID |
| **1:N Identification** | Search probe against all patients in the tenant |
| **Deduplication** | Find pairs of patients that are likely the same person |
| **Probe** | The freshly captured fingerprint being matched |
| **Candidate** | A stored patient template being compared against the probe |
| **SIDAInfo** | HIV EMR system — primary consumer of BioAFIS |
| **IBIPIMO** | Laboratory Information System — secondary consumer |
| **SecuGen** | Brand of fingerprint readers used across Burundian health facilities |
| **SgiBioSrv** | SecuGen WebAPI client — local Windows service on facility PCs (port 8000) |
| **M2Sys** | Previous biometric middleware (inaccessible — data lost at USAID contract end) |
| **AIRB** | Advanced IT & Research Burundi — builder and operator of BioAFIS |
| **Tenant** | A client application (SIDAInfo, IBIPIMO) with its own API key and isolated data |
| **UID** | Patient identifier from the calling HIS — preserved as-is, never regenerated |
| **Facility code** | Code identifying the health facility where enrollment happened |
| **AT_VISIT** | Enrollment happened during a normal patient consultation |
| **CAMPAIGN** | Enrollment happened during a dedicated biometric enrollment session |
| **QUALITY_FAILED** | Patient's fingerprint quality was below threshold after 3 attempts |

---

*Last updated: May 2026 — BioAFIS v1.0 development start*  
*Maintained by: AIRB Engineering Team (Emeric Mugiraneza — Technical Lead)*
