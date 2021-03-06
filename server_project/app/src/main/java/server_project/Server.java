package server_project;

import java.io.*;
import java.net.*;
import javax.net.*;
import javax.net.ssl.*;

import org.json.*;
import org.mindrot.jbcrypt.BCrypt;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Server implements Runnable {
  private ServerSocket serverSocket = null;
  private static int numConnectedClients = 0;

  private Map<String, Integer> sessionNonceMap = new HashMap<>();
  private Map<String, String> sessionSSNMap = new HashMap<>();
  private Map<String, String> sessionTypeMap = new HashMap<>();
  private Map<String, String> sessionIDMap = new HashMap<>();

  private Random random = new Random();

  Connection connection;
  FileOutputStream logfile = new FileOutputStream("server.log", true);

  public Server(ServerSocket ss) throws IOException, SQLException {
    serverSocket = ss;

    String databaseString = "jdbc:sqlite:src/main/resources/database.sqlite";
    connection = DriverManager.getConnection(databaseString);

    log("Server Started");
    newListener();
  }

  public void send(PrintWriter out, JSONObject data) throws JSONException {
    data.put("timestamp", new Date().toString());

    out.println(data.toString());
    out.flush();
  }

  public String hash(String password, String salt) {
    return BCrypt.hashpw(password, BCrypt.gensalt(14) + salt);
  }

  public void log(String message) {
    String actualMessage = new Date().toString() + " | " + message;
    System.out.println(actualMessage);

    // Write to logfile.....
    try {
      logfile.write((actualMessage + "\n").getBytes());
    } catch (IOException e) {
      System.out.println("[ERROR] Could not write to logfile.");
      e.printStackTrace();
    }
  }

  public boolean checkNonce(JSONObject data) throws JSONException {
    String session = data.getString("session");
    int nonce = data.getInt("nonce");

    if (!sessionNonceMap.containsKey(session)) {
      log("[ERROR] Incorrect session from client.");
      return false;
    }

    if (nonce != sessionNonceMap.get(session) + 1) {
      log("[ERROR] Incorrect nonce from client.");
      return false;
    }

    sessionNonceMap.put(session, nonce);
    return true;
  }

  public void handleClientMessage(PrintWriter out, String message) {
    // message = "{ \"kind\": \"NEW_SESSION\" }"

    try {
      JSONObject data = new JSONObject(message);
      String kind = data.getString("kind");
      log("Received request of kind: '" + kind + "'");

      switch (kind) {
        case "NEW_SESSION":
          String newSession = "" + (10000 + random.nextInt(99999));

          // Assure newSession doesn't already exist!
          while (sessionNonceMap.containsKey(newSession)) {
            newSession = "" + (10000 + random.nextInt(99999));
          }

          int newNonce = random.nextInt(1000000);
          sessionNonceMap.put(newSession, newNonce);

          log("Created new session '" + newSession + "'");

          send(out, new JSONObject()
              .put("kind", "NEW_SESSION_RESPONSE")
              .put("session", newSession)
              .put("nonce", newNonce));

          break;

        case "LOGIN":
          if (!checkNonce(data)) {
            log("Nonce check failed");
            send(out, new JSONObject()
                .put("kind", "ERROR")
                .put("message", "Incorrect nonce."));
            return;
          }

          String username = data.getString("username");
          String password = data.getString("password");
          String type = data.getString("type");
          PreparedStatement statement = null;

          switch (type) {
            case "PATIENT":
              statement = connection
                  .prepareStatement("SELECT * FROM individuals JOIN patients WHERE ssn=? AND patient_ssn=?");
              statement.setString(1, username);
              statement.setString(2, username);
              break;

            case "NURSE":
              statement = connection
                  .prepareStatement("SELECT * FROM individuals JOIN nurses WHERE ssn=? AND nurse_ssn=?");
              statement.setString(1, username);
              statement.setString(2, username);
              break;

            case "DOCTOR":
              statement = connection
                  .prepareStatement("SELECT * FROM individuals JOIN doctors WHERE ssn=? AND doctor_ssn=?");
              statement.setString(1, username);
              statement.setString(2, username);
              break;

            case "GOVERNMENT":
              statement = connection
                  .prepareStatement("SELECT * FROM individuals JOIN government_agencies WHERE ssn=? AND agency_ssn=?");
              statement.setString(1, username);
              statement.setString(2, username);
              break;

            default:
              send(out, new JSONObject()
                  .put("kind", "LOGIN_FAILED")
                  .put("message", "Login failed. Unkown type " + type));

              return;
          }

          var sqlRresult = statement.executeQuery();

          if (!sqlRresult.next()) {
            log("Session '" + data.get("session") + "' failed login attempt.");

            send(out, new JSONObject()
                .put("kind", "LOGIN_FAILED")
                .put("message", "Login failed."));
            return;
          }

          String name = sqlRresult.getString("individual_name");
          String hashedPasswordInDatabase = sqlRresult.getString("password");
          int loginAttempts = sqlRresult.getInt("login_attempts");

          log("Login attempts: " + loginAttempts);

          if (loginAttempts >= 5) {
            log("Session '" + data.get("session") + "' has too many failed login attempts.");

            send(out, new JSONObject()
                .put("kind", "LOGIN_FAILED")
                .put("message", "Too many failed attempts. Contact a system administrator."));
            return;
          }

          var success = BCrypt.checkpw(password, hashedPasswordInDatabase);

          if (!success) {
            log("Session '" + data.get("session") + "' failed login attempt.");

            send(out, new JSONObject()
                .put("kind", "LOGIN_FAILED")
                .put("message", "Login failed."));

            statement = connection.prepareStatement("UPDATE individuals SET login_attempts = ? WHERE ssn IN(?)");
            statement.setInt(1, loginAttempts + 1);
            statement.setString(2, username);
            statement.execute();
            return;
          }

          log(String.format("Session '%s' logged in successfully (id: %s).", data.get("session"),
              sessionIDMap.get(data.getString("session"))));

          statement = connection.prepareStatement("UPDATE individuals SET login_attempts = 0 WHERE ssn IN(?)");
          statement.setString(1, username);
          statement.execute();

          String id = "";

          switch (type) {
            case "PATIENT":
              id = sqlRresult.getString("patient_id");
              break;

            case "NURSE":
              id = sqlRresult.getString("nurse_id");
              break;

            case "DOCTOR":
              id = sqlRresult.getString("doctor_id");
              break;

            case "GOVERNMENT":
              id = sqlRresult.getString("agency_id");
              break;
          }

          sessionIDMap.put(data.getString("session"), id);

          var response = new JSONObject()
              .put("kind", "LOGIN_SUCCESS")
              .put("name", name)
              .put("id", id);

          sessionSSNMap.put(data.getString("session"), username);
          sessionTypeMap.put(data.getString("session"), type);

          switch (type) {
            case "NURSE":
            case "DOCTOR":
              response.put("division", sqlRresult.getString("division_id"));
              break;

            case "GOVERNMENT":
              response.put("agency", sqlRresult.getString("agency_id"));
              break;

            default:
            case "PATIENT":
              break;
          }

          send(out, response);
          break;

        case "LIST_RECORDS":
          if (!checkNonce(data)) {
            log("Nonce check failed");
            send(out, new JSONObject()
                .put("kind", "ERROR")
                .put("message", "Incorrect nonce."));
            return;
          }

          PreparedStatement recordStatement = null;

          List<JSONObject> records = new ArrayList<>();

          id = sessionIDMap.get(data.getString("session"));

          switch (sessionTypeMap.get(data.getString("session"))) {
            case "PATIENT":
              recordStatement = connection
                  .prepareStatement("SELECT * FROM medical_records WHERE patient_id=?");
              recordStatement.setString(1, id);
              break;

            case "DOCTOR":
              recordStatement = connection.prepareStatement(
                  "SELECT * FROM medical_records WHERE doctor_id=? OR division_id IN (SELECT division_id FROM doctors WHERE doctor_id=?)");
              recordStatement.setString(1, id);
              recordStatement.setString(2, id);
              break;

            case "NURSE":
              recordStatement = connection.prepareStatement(
                  "SELECT * FROM medical_records WHERE nurse_id=? OR division_id IN (SELECT division_id FROM nurses WHERE nurse_id=?)");
              recordStatement.setString(1, id);
              recordStatement.setString(2, id);
              break;

            case "GOVERNMENT":
              recordStatement = connection.prepareStatement("SELECT * FROM medical_records");
            default:
              break;
          }

          var recordsResult = recordStatement.executeQuery();
          while (recordsResult.next()) {
            records.add(new JSONObject()
                .put("data", recordsResult.getString("medical_data"))
                .put("patient_id", recordsResult.getString("patient_id"))
                .put("record_id", recordsResult.getString("record_id"))
                .put("doctor_id", recordsResult.getString("doctor_id"))
                .put("division_id", recordsResult.getString("division_id"))
                .put("nurse_id", recordsResult.getString("nurse_id")));
          }

          log("A list of records has been sent to " + sessionIDMap.get(data.getString("session")));
          send(out, new JSONObject()
              .put("kind", "LIST_RECORDS_RESPONSE")
              .put("records", records));

          break;

        case "UPDATE_RECORD":
          if (!checkNonce(data)) {
            log("Nonce check failed");
            send(out, new JSONObject()
                .put("kind", "ERROR")
                .put("message", "Incorrect nonce."));
            return;
          }

          var updaterType = sessionTypeMap.get(data.getString("session"));
          var updateRecordID = data.getString("record_id");

          switch (updaterType) {
            case "NURSE":
              var checkIDStatementNurse = connection
                  .prepareStatement("SELECT * FROM medical_records WHERE record_id=?");
              checkIDStatementNurse.setString(1, updateRecordID);

              var checkIDResultNurse = checkIDStatementNurse.executeQuery();

              if (!checkIDResultNurse.getString("nurse_id")
                  .equalsIgnoreCase(sessionIDMap.get(data.getString("session")))) {

                send(out, new JSONObject()
                    .put("kind", "UPDATE_RECORD_FAILED")
                    .put("message", "You do not have the required level of access to update that record."));
                log(String.format("[ERROR] A nurse with id %s tried to edit a record (%s) but was denied.",
                    sessionIDMap.get(data.getString("session")), updateRecordID));
                return;
              }

              break;

            case "DOCTOR":
              var checkIDStatementDoctor = connection
                  .prepareStatement("SELECT * FROM medical_records WHERE record_id=?");
              checkIDStatementDoctor.setString(1, updateRecordID);

              var checkIDResultDoctor = checkIDStatementDoctor.executeQuery();

              if (!checkIDResultDoctor.getString("doctor_id")
                  .equalsIgnoreCase(sessionIDMap.get(data.getString("session")))) {

                send(out, new JSONObject()
                    .put("kind", "UPDATE_RECORD_FAILED")
                    .put("message", "You do not have the required level of access to update that record."));

                log(String.format("[ERROR] A doctor with id %s tried to edit a record (%s) but was denied.",
                    sessionIDMap.get(data.getString("session")), updateRecordID));
                return;
              }
              break;

            default:
              send(out, new JSONObject()
                  .put("kind", "UPDATE_RECORD_FAILED")
                  .put("message", "You do not have the required level of access to update that record."));
              log(String.format("%s who isn't a doctor or nurse tried to update a record (%s) but was denied.",
                  sessionIDMap.get(data.getString("session")), updateRecordID));
              return;
          }

          var updateStatement = connection
              .prepareStatement("UPDATE medical_records SET medical_data=? WHERE record_id=?");

          updateStatement.setString(1, data.getString("medical_data"));
          updateStatement.setString(2, updateRecordID);

          try {
            updateStatement.executeUpdate();
          } catch (SQLException e) {
            send(out, new JSONObject()
                .put("kind", "UPDATE_RECORD_FAILED")
                .put("message", "An error occurred while updating record."));
            log("[ERROR] Could not create record: " + e.getMessage());
            return;
          }

          log(String.format("%s updated a record with id %s",
              sessionIDMap.get(data.getString("session")), updateRecordID));

          send(out, new JSONObject()
              .put("kind", "UPDATE_RECORD_SUCCESS"));

          break;

        case "CREATE_RECORD":
          if (!checkNonce(data)) {
            log("Nonce check failed");
            send(out, new JSONObject()
                .put("kind", "ERROR")
                .put("message", "Incorrect nonce."));
            return;
          }

          var creatorType = sessionTypeMap.get(data.getString("session"));

          switch (creatorType) {

            case "DOCTOR":
              break;

            default:
              send(out, new JSONObject()
                  .put("kind", "CREATE_RECORD_FAILED")
                  .put("message", "You do not have the required level of access to create a record."));
              return;
          }

          var currentHighestIDResult = connection
              .createStatement()
              .executeQuery("SELECT record_id FROM medical_records ORDER BY record_id DESC LIMIT 1");

          String currentHighestID = currentHighestIDResult.getString("record_id").split("r")[1];
          var newID = "r" + (Integer.parseInt(currentHighestID) + 1);

          var createStatement = connection.prepareStatement(
              "INSERT INTO medical_records (record_id, patient_id, doctor_id, nurse_id, division_id, medical_data) VALUES (?, ?, ?, ?, (SELECT division_id FROM doctors WHERE doctor_id=?), ?)");

          createStatement.setString(1, newID);
          createStatement.setString(2, data.getString("patient_id"));
          createStatement.setString(3, sessionIDMap.get(data.getString("session")));
          createStatement.setString(4, data.getString("nurse_id"));
          createStatement.setString(5, sessionIDMap.get(data.getString("session")));
          createStatement.setString(6, data.getString("medical_data"));

          try {
            createStatement.execute();
          } catch (SQLException e) {
            send(out, new JSONObject()
                .put("kind", "CREATE_RECORD_FAILED")
                .put("message", "An error occurred while creating record."));

            log("[ERROR] Error while creating record: " + e.getMessage());
            return;
          }

          log(String.format("%s created a record with id %s for patient %s",
              sessionIDMap.get(data.getString("session")),
              newID,
              data.getString("patient_id")));

          send(out, new JSONObject()
              .put("kind", "CREATE_RECORD_SUCCESS"));

          break;

        case "DELETE_RECORD":
          if (!checkNonce(data)) {
            log("Nonce check failed");
            send(out, new JSONObject()
                .put("kind", "ERROR")
                .put("message", "Incorrect nonce."));
            return;
          }

          var deleteType = sessionTypeMap.get(data.getString("session"));
          var deleteResponse = new JSONObject()
              .put("kind", "DELETE_RECORD_SUCCESS");

          var deleteRecordID = data.getString("record_id");

          switch (deleteType) {
            case "GOVERNMENT":
              break;

            default:
              send(out, new JSONObject()
                  .put("kind", "DELETE_RECORD_FAILED")
                  .put("message", "You do not have the required level of access to delete that record."));
              log(String.format("%s who isn't a government agency tried to delete a record (%s) but was denied.",
                  sessionIDMap.get(data.getString("session")), deleteRecordID));
              return;
          }

          var s = connection.prepareStatement("DELETE FROM medical_records WHERE record_id=?");
          s.setString(1, deleteRecordID);

          try {
            s.execute();
          } catch (SQLException e) {
            send(out, new JSONObject()
                .put("kind", "DELETE_RECORD_FAILED")
                .put("message", "An error occured while attempting to delete record."));
            log("[ERROR] A record could not be deleted: " + e.getMessage());
            return;
          }

          log(String.format("Record with ID '%s' has been deleted by %s",
              deleteRecordID,
              sessionIDMap.get(data.getString("session"))));

          send(out, deleteResponse);
          break;

        default:
          log("[ERROR] Could not handle request.");
          break;
      }
    } catch (JSONException | SQLException e) {
      log("[ERROR] Error occured while processing request '" + message.toString() + "'");
      log("[ERROR] " + e.getMessage());
      for (var trace : e.getStackTrace()) {
        log("[ERROR]    " + trace);
      }
    }
  }

  public void run() {
    try {
      SSLSocket socket = (SSLSocket) serverSocket.accept();

      newListener();

      SSLSession session = socket.getSession();
      Certificate[] cert = session.getPeerCertificates();
      String subject = ((X509Certificate) cert[0]).getSubjectX500Principal().getName();

      numConnectedClients++;

      log("[TLS] client connected");
      log("[TLS] client name (cert subject DN field): " + subject);
      log("[TLS] " + numConnectedClients + " concurrent connection(s)");

      PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

      String clientMsg = null;
      while ((clientMsg = in.readLine()) != null) {
        handleClientMessage(out, clientMsg);
      }

      in.close();
      out.close();
      socket.close();
      numConnectedClients--;

      log("[TLS] client disconnected");
      log(numConnectedClients + " concurrent connection(s)\n");
    } catch (IOException e) {
      log("[TLS] client died: " + e.getMessage());
      e.printStackTrace();
      return;
    }
  }

  private void newListener() {
    (new Thread(this)).start();
  } // calls run()

  public static ServerSocketFactory getServerSocketFactory(String type) {
    if (type.equals("TLSv1.2")) {
      SSLServerSocketFactory ssf = null;

      try { // set up key manager to perform server authentication
        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");
        char[] password = "password".toCharArray();

        // keystore password (storepass)
        ks.load(new FileInputStream("src/main/resources/serverkeystore"), password);

        // truststore password (storepass)
        ts.load(new FileInputStream("src/main/resources/servertruststore"), password);

        kmf.init(ks, password); // certificate password (keypass)
        tmf.init(ts); // possible to use keystore as truststore here
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        ssf = ctx.getServerSocketFactory();

        return ssf;
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      return ServerSocketFactory.getDefault();
    }
    return null;
  }
}
