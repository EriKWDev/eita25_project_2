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


*:PATIENT: Your own only
  NURSE, DOCTOR: Records you are assigned to or that belong to your division
  GOVERNMENT: All records