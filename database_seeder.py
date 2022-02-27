import sqlite3

def main():
    db = sqlite3.connect("./server_project/app/src/main/resources/database.sqlite")

    cursor = db.cursor()
    cursor.execute("SELECT * FROM individuals")
    db.commit()

    data = cursor.fetchall()

    for row in data:
        print(row)

if __name__ == "__main__":
    main()
