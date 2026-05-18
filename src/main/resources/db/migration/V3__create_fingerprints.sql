CREATE TABLE IF NOT EXISTS fingerprints (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id          BIGINT NOT NULL,
    finger_position     ENUM(
                            'RIGHT_THUMB','RIGHT_INDEX','RIGHT_MIDDLE','RIGHT_RING','RIGHT_LITTLE',
                            'LEFT_THUMB','LEFT_INDEX','LEFT_MIDDLE','LEFT_RING','LEFT_LITTLE'
                        ) NOT NULL,
    template_encrypted  LONGBLOB NOT NULL,
    quality_score       TINYINT UNSIGNED NOT NULL,
    enrollment_source   ENUM('AT_VISIT','CAMPAIGN','TRANSFER') NOT NULL,
    enrolled_by         VARCHAR(100),
    enrolled_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_patient_finger (patient_id, finger_position),
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE
) ENGINE=InnoDB;
