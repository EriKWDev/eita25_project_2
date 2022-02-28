import sqlite3
import bcrypt

def main():
    db = sqlite3.connect("./server_project/app/src/main/resources/database.sqlite")

    cursor = db.cursor()

    cursor.execute("""
      UPDATE individuals
        SET password = CASE ssn 
          WHEN '123456-0001' THEN ?
          WHEN '123456-0002' THEN ?
          WHEN '123456-0003' THEN ?
          WHEN '123456-0004' THEN ?
          WHEN '123456-0005' THEN ?
          WHEN '123456-0007' THEN ?
          ELSE password
          END
      WHERE ssn IN('123456-0001','123456-0002','123456-0003','123456-0004','123456-0005','123456-0007');
    """, [
      bcrypt.hashpw("password001!".encode("utf8"), bcrypt.gensalt(13, b"2a")).decode("utf8"),
      bcrypt.hashpw("password002!".encode("utf8"), bcrypt.gensalt(13, b"2a")).decode("utf8"),
      bcrypt.hashpw("password003!".encode("utf8"), bcrypt.gensalt(13, b"2a")).decode("utf8"),
      bcrypt.hashpw("password004!".encode("utf8"), bcrypt.gensalt(13, b"2a")).decode("utf8"),
      bcrypt.hashpw("password005!".encode("utf8"), bcrypt.gensalt(13, b"2a")).decode("utf8"),
      bcrypt.hashpw("password007!".encode("utf8"), bcrypt.gensalt(13, b"2a")).decode("utf8")
    ])

    db.commit()

if __name__ == "__main__":
    main()
