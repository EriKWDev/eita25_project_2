rm ./server_project/app/src/main/resources/database.sqlite
sqlite3 ./server_project/app/src/main/resources/database.sqlite < schema.sql
python database_seeder.py
