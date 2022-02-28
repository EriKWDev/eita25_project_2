# About
EITA25 Project 2

## Running
Start the server by going into the `server_project` directory and run the file `run.sh` (or call `gradle run`)

Start the client by going into the `client_project` directory and run the file `run.sh` (or call `gradle run localhost 1337`)

IMPORTANT: Please make sure to start the server BEFORE the client.

## Usage
Once the server is started and you have started a client, you will be prompted to login.

You can login as either a `PATIENT`, `NURSE`, `DOCTOR` or `GOVERNMENT`.

Here are some accounts types, their ssn and passwords:
```
TYPE         SSN           PASSWORD
PATIENT      123456-0001   password001!
NURSE        123456-0002   password002!
DOCTOR       123456-0004   password004!
GOVERNMENT   123456-0007   password007!
```

Once signed in, you can type `help` to list available commands.

Some of them include:
`quit` - Exits the client
`about` - Prints information about your account
`records` - Lists all records you have access to*
`records update <record_id>` - Updates a record with a certain ID
`records create` - starts wizard to create new record
`records delete <record_id>` - Deletes a record with a certain ID

*:PATIENT: Your own only
  NURSE, DOCTOR: Records you are assigned to or that belong to your division
  GOVERNMENT: All records

Commands will only work if your currently logged in account has the right priveleges.

A logfile of everything being logged in the server is saved inside the `server_project/app/server.log` file.
More user's and their passwords can be found inside the `schema.sql` file or the `database_seeder.py` file.

If you need to reset the database, run the `reset_database.sh` file from the root directory of the project.

## Versions
The code in this project has been tried and verified with the following versions of `java`, `sqlite3` and `gradle`:
```log
» java --version                                                                          
openjdk 17.0.2 2022-01-18
OpenJDK Runtime Environment 21.9 (build 17.0.2+8)
OpenJDK 64-Bit Server VM 21.9 (build 17.0.2+8, mixed mode, sharing)

» sqlite3 --version                                                                         
3.36.0 2021-06-18 18:36:39 5c9a6c06871cb9fe42814af9c039eb6da5427a6ec28f187af7ebfb62eafaalt1

» gradle --version                                                                         
------------------------------------------------------------
Gradle 7.4
------------------------------------------------------------

Build time:   2022-02-08 09:58:38 UTC
Revision:     f0d9291c04b90b59445041eaa75b2ee744162586

Kotlin:       1.5.31
Groovy:       3.0.9
Ant:          Apache Ant(TM) version 1.10.11 compiled on July 10 2021
JVM:          17.0.2 (Red Hat, Inc. 17.0.2+8)
OS:           Linux 5.16.10-200.fc35.x86_64 amd64
```
