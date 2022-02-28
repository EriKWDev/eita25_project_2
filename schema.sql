PRAGMA foreign_keys=ON;

DROP TABLE IF EXISTS hospital_divisions;

DROP TABLE IF EXISTS individuals;
DROP TABLE IF EXISTS patients;
DROP TABLE IF EXISTS nurses;
DROP TABLE IF EXISTS doctors;
DROP TABLE IF EXISTS government_agencies;

DROP TABLE IF EXISTS medical_records;


CREATE TABLE hospital_divisions (
  division_id   TEXT UNIQUE, 
  division_name TEXT,

  PRIMARY KEY  (division_id)
);


CREATE TABLE individuals (
  ssn               TEXT UNIQUE,  
  individual_name   TEXT,
  login_attempts    INT DEFAULT 0,

  password          TEXT,

  PRIMARY KEY  (ssn)
);

CREATE TABLE patients (
  patient_id   TEXT UNIQUE, 
  patient_ssn  TEXT,

  PRIMARY KEY  (patient_id),

  FOREIGN KEY  (patient_ssn) REFERENCES individuals(ssn)
);

CREATE TABLE nurses (
  nurse_id    TEXT UNIQUE, 
  nurse_ssn   TEXT,  
  division_id TEXT,

  PRIMARY KEY  (nurse_id),

  FOREIGN KEY (nurse_ssn) REFERENCES individuals(ssn),
  FOREIGN KEY (division_id) REFERENCES hospital_divisions(division_id)
);

CREATE TABLE doctors (
  doctor_id   TEXT UNIQUE, 
  doctor_ssn  TEXT,  
  division_id TEXT,

  PRIMARY KEY  (doctor_id),

  FOREIGN KEY (doctor_ssn) REFERENCES individuals(ssn),
  FOREIGN KEY (division_id) REFERENCES hospital_divisions(division_id)
);

CREATE TABLE government_agencies (
  agency_id  TEXT UNIQUE, 
  agency_ssn TEXT,  

  PRIMARY KEY  (agency_id),

  FOREIGN KEY (agency_ssn) REFERENCES individuals(ssn)
);



CREATE TABLE medical_records (
  record_id    TEXT UNIQUE,

  patient_id   TEXT,
  doctor_id    TEXT,
  nurse_id     TEXT,

  division_id  TEXT,

  medical_data TEXT,

  PRIMARY KEY  (record_id),

  FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
  FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id),
  FOREIGN KEY (nurse_id) REFERENCES nurses(nurse_id),
  FOREIGN KEY (division_id) REFERENCES hospital_divisions(division_id)
);


INSERT INTO individuals (ssn, individual_name, password)
VALUES
  ("123456-0001",  "Alex Sibzamini",     "password001!"),
  ("123456-0002",  "Emma Potatisodlare", "password002!"),
  ("123456-0003",  "Alice Pot. Atis",    "password003!"),
  ("123456-0004",  "Bob P. Tato",        "password004!"),
  ("123456-0005",  "Jonas Pommes",       "password005!"),
  ("123456-0006",  "Kalle Anka",         "password006!"),
  
  ("123456-0007",  "James Bond",         "password007!");


INSERT INTO patients (patient_id, patient_ssn)
VALUES
  ("p001", "123456-0001"),
  ("p002", "123456-0006");

INSERT INTO hospital_divisions (division_id, division_name)
VALUES
  ("d001", "Division 1"),
  ("d002", "Division 2");


INSERT INTO nurses (nurse_id, nurse_ssn, division_id)
VALUES
  ("n001", "123456-0002", "d001"),
  ("n002", "123456-0003", "d002");


INSERT INTO doctors (doctor_id, doctor_ssn, division_id)
VALUES
  ("dr001", "123456-0004", "d001"),
  ("dr002", "123456-0005", "d002");

INSERT INTO government_agencies (agency_id, agency_ssn)
VALUES
  ("a007", "123456-0007");

INSERT INTO medical_records (record_id, patient_id, doctor_id, nurse_id, division_id, medical_data)
VALUES
  ("r1", "p001", "dr001", "n001", "d001", "Tummy hurts"),
  ("r2", "p001", "dr002", "n002", "d002", "Tummy still hurts"),
  ("r3", "p002", "dr001", "n001", "d001", "Feet hurt"),
  ("r4", "p002", "dr001", "n002", "d001", "Had to amputate feet");


