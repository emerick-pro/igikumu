CREATE TABLE IF NOT EXISTS audit_logs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT,
    operation           VARCHAR(50) NOT NULL,
    uid                 VARCHAR(100),
    ip_address          VARCHAR(45),
    response_ms         INT,
    http_status         SMALLINT,
    error_code          VARCHAR(50),
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED;
