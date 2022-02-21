PRAGMA foreign_keys=ON;

DROP TABLE IF EXISTS movies;
DROP TABLE IF EXISTS screenings;
DROP TABLE IF EXISTS theaters;
DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS customers;

CREATE TABLE movies (
  imdb          TEXT,
  title         TEXT,
  year          INT,
  running_time  INT,

  PRIMARY KEY  (imdb)
);

CREATE TABLE screenings (
  screening_id    TEXT DEFAULT (lower(hex(randomblob(16)))),
  start_time      DATETIME,
  imdb            TEXT,
  theater_id      TEXT,

  FOREIGN KEY (imdb) REFERENCES movies(imdb),
  FOREIGN KEY (theater_id) REFERENCES theaters(theater_id),

  PRIMARY KEY  (screening_id)
);


CREATE TABLE theaters (
  theater_id    TEXT DEFAULT (lower(hex(randomblob(16)))),
  theater_name  TEXT UNIQUE,
  capacity      INT,
  
  PRIMARY KEY  (theater_id)
);

CREATE TABLE customers (
  customer_id   TEXT DEFAULT (lower(hex(randomblob(16)))),  
  customer_name TEXT,
  username      TEXT UNIQUE,
  password      TEXT,

  PRIMARY KEY  (customer_id)
);


CREATE TABLE tickets (
  ticket_id       TEXT DEFAULT (lower(hex(randomblob(16)))),
  screening_id    TEXT CHECK,
  customer_id     TEXT,

  FOREIGN KEY (screening_id) REFERENCES screenings(screening_id),
  FOREIGN KEY (customer_id) REFERENCES customers(customer_id),

  PRIMARY KEY  (ticket_id)
);


INSERT INTO customers (customer_name, username, password)
VALUES
  ("Elham", "Elly",        "password"),
  ("Erik",  "CoolGuy69",   "password"),
  ("Lars",  "Lassemannen", "password");

INSERT INTO movies (imdb, title, year, running_time)
VALUES
  ("tt0485947", "Mr. Nobody", 2009, 140),
  ("tt0109830", "Forrest Gump", 1994, 142),
  ("tt0111161", "The Shawshank Redemption", 1994, 142);

INSERT INTO theaters (theater_name, capacity)
VALUES
  ("Filmstaden", 47),
  ("Royal", 237);

INSERT INTO screenings (start_time, imdb, theater_id)
VALUES
  ("20220208 06:00:00 PM", "tt0485947", (SELECT theater_id FROM theaters WHERE theater_name = "Filmstaden")),
  ("20220208 06:00:00 PM", "tt0109830", (SELECT theater_id FROM theaters WHERE theater_name = "Royal")),
  ("20220209 05:00:00 PM", "tt0111161", (SELECT theater_id FROM theaters WHERE theater_name = "Royal"));
