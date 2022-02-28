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
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Server implements Runnable {
  private ServerSocket serverSocket = null;
  private static int numConnectedClients = 0;

  private Map<String, Integer> sessionNonceMap = new HashMap<>();
  private Random random = new Random();

  Connection connection;

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
    System.out.println(new Date().toString() + " | " + message);
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
            break;
          }

          String username = data.getString("username");
          String password = data.getString("password");

          var statement = connection.prepareStatement("SELECT * FROM individuals WHERE ssn=?");
          statement.setString(1, username);
          var result = statement.executeQuery();

          String name = null;
          String hashedPasswordInDatabase = null;
          int loginAttempts = -1;

          while (result.next()) {
            name = result.getString("individual_name");
            hashedPasswordInDatabase = result.getString("password");
            loginAttempts = result.getInt("login_attempts");
            break;
          }

          log("Login attempts: " + loginAttempts);

          if (loginAttempts > 5) {
            log("Session '" + data.get("session") + "' has too many failed login attempts.");

            send(out, new JSONObject()
                .put("kind", "LOGIN_FAILED")
                .put("message", "Too many failed attempts. Contact a system administrator."));
            break;
          }

          if (name == null || hashedPasswordInDatabase == null) {
            log("Session '" + data.get("session") + "' failed login attempt.");

            send(out, new JSONObject()
                .put("kind", "LOGIN_FAILED")
                .put("message", "Login failed."));
            break;
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
            break;
          }

          log("Session '" + data.get("session") + "' logged in successfully.");

          statement = connection.prepareStatement("UPDATE individuals SET login_attempts = 0 WHERE ssn IN(?)");
          statement.setString(1, username);
          statement.execute();

          send(out, new JSONObject()
              .put("kind", "LOGIN_SUCCESS")
              .put("name", name));
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
