package server_project;

import java.io.*;
import java.net.*;
import javax.net.*;
import javax.net.ssl.*;

import org.json.*;

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

    newListener();
  }

  public void send(PrintWriter out, JSONObject data) throws JSONException {
    data.put("timestamp", new Date().toString());

    out.println(data.toString());
    out.flush();
  }

  public String hash(String password, String salt) {
    return "" + (password + salt).hashCode();
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

          // log(username);
          // log(password);

          String hashedPassword = hash(password, username);
          log("Hashed Password: " + hashedPassword);

          var success = true;

          if (!success) {
            log("Session '" + data.get("session") + "' failed login attempt.");

            send(out, new JSONObject()
                .put("kind", "LOGIN_FAILED")
                .put("message", "Login failed."));
            break;
          }

          log("Session '" + data.get("session") + "' logged in successfully.");

          send(out, new JSONObject()
              .put("kind", "LOGIN_SUCCESS"));
          break;

        default:
          log("[ERROR] Could not handle request.");
          break;
      }
    } catch (JSONException e) {
      e.printStackTrace();
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
