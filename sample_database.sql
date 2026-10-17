-- BloodBankManagementSystem: safe demo database
-- Fictional sample data only. Do NOT use real donor/patient information.
-- Intended for a fresh local MySQL demo setup; database name matches Java JDBC URL.

CREATE DATABASE IF NOT EXISTS blood_bank_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE blood_bank_db;

CREATE TABLE IF NOT EXISTS donors (
    donor_id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    gender VARCHAR(20) NOT NULL,
    phone VARCHAR(15) NOT NULL,
    blood_group VARCHAR(3) NOT NULL,
    city VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (donor_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS blood_stock (
    blood_group VARCHAR(3) NOT NULL,
    units_available INT NOT NULL DEFAULT 0,
    PRIMARY KEY (blood_group)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS donations (
    donation_id INT NOT NULL AUTO_INCREMENT,
    donor_id INT NOT NULL,
    units INT NOT NULL,
    donation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (donation_id),
    CONSTRAINT fk_donations_donor
      FOREIGN KEY (donor_id) REFERENCES donors(donor_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS blood_requests (
    request_id INT NOT NULL AUTO_INCREMENT,
    patient_name VARCHAR(100) NOT NULL,
    hospital_name VARCHAR(150) NOT NULL,
    blood_group VARCHAR(3) NOT NULL,
    units_requested INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id)
) ENGINE=InnoDB;

-- Seed stock; preserve existing stock if a row already exists.
INSERT INTO blood_stock (blood_group, units_available)
VALUES ('A+',8),('A-',3),('B+',7),('B-',2),('AB+',4),('AB-',1),('O+',10),('O-',3)
ON DUPLICATE KEY UPDATE blood_group = VALUES(blood_group);

-- Fictional donors; placeholder phone values are not contact numbers.
INSERT INTO donors (name, age, gender, phone, blood_group, city)
SELECT 'Demo Donor A',25,'Female','0000000001','A+','Chennai'
WHERE NOT EXISTS (SELECT 1 FROM donors WHERE phone='0000000001');

INSERT INTO donors (name, age, gender, phone, blood_group, city)
SELECT 'Demo Donor B',31,'Male','0000000002','O+','Coimbatore'
WHERE NOT EXISTS (SELECT 1 FROM donors WHERE phone='0000000002');

INSERT INTO donors (name, age, gender, phone, blood_group, city)
SELECT 'Demo Donor C',28,'Other','0000000003','B+','Madurai'
WHERE NOT EXISTS (SELECT 1 FROM donors WHERE phone='0000000003');

INSERT INTO donors (name, age, gender, phone, blood_group, city)
SELECT 'Demo Donor D',22,'Female','0000000004','AB+','Salem'
WHERE NOT EXISTS (SELECT 1 FROM donors WHERE phone='0000000004');

INSERT INTO donations (donor_id, units)
SELECT d.donor_id, 2 FROM donors d
WHERE d.phone='0000000001'
AND NOT EXISTS (SELECT 1 FROM donations x WHERE x.donor_id=d.donor_id);

INSERT INTO donations (donor_id, units)
SELECT d.donor_id, 1 FROM donors d
WHERE d.phone='0000000002'
AND NOT EXISTS (SELECT 1 FROM donations x WHERE x.donor_id=d.donor_id);

INSERT INTO blood_requests (patient_name,hospital_name,blood_group,units_requested,status)
SELECT 'Demo Patient A','Demo General Hospital','O+',2,'PENDING'
WHERE NOT EXISTS (
  SELECT 1 FROM blood_requests
  WHERE patient_name='Demo Patient A' AND hospital_name='Demo General Hospital'
    AND blood_group='O+' AND status='PENDING'
);

INSERT INTO blood_requests (patient_name,hospital_name,blood_group,units_requested,status)
SELECT 'Demo Patient B','Demo City Hospital','A+',1,'FULFILLED'
WHERE NOT EXISTS (
  SELECT 1 FROM blood_requests
  WHERE patient_name='Demo Patient B' AND hospital_name='Demo City Hospital'
    AND blood_group='A+' AND status='FULFILLED'
);

-- Verify imported demo data.
SELECT COUNT(*) AS donor_count FROM donors;
SELECT blood_group, units_available FROM blood_stock ORDER BY blood_group;
SELECT request_id, patient_name, blood_group, units_requested, status
FROM blood_requests ORDER BY request_id;
