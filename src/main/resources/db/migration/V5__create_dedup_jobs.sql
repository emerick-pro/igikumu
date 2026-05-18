CREATE TABLE IF NOT EXISTS dedup_jobs (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    status              ENUM('PENDING','RUNNING','DONE','FAILED') NOT NULL DEFAULT 'PENDING',
    scope_facility      VARCHAR(50),
    progress_pct        TINYINT UNSIGNED NOT NULL DEFAULT 0,
    result_count        INT,
    started_at          DATETIME(3),
    completed_at        DATETIME(3),
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
) ENGINE=InnoDB;
