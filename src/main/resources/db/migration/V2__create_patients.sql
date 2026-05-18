CREATE TABLE IF NOT EXISTS patients (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    uid                 VARCHAR(100) NOT NULL,
    tenant_id           BIGINT NOT NULL,
    facility_code       VARCHAR(50),
    enrollment_status   ENUM('PENDING','ENROLLED','QUALITY_FAILED','PARTIAL') NOT NULL DEFAULT 'PENDING',
    enrolled_at         DATETIME(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_uid_tenant (uid, tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
