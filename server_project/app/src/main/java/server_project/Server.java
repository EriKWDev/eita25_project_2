package server_project;

import java.io.*;
import java.net.*;
import javax.net.*;
import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

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

    /*
     * Statement statement = connection.createStatement(
     * ResultSet.TYPE_FORWARD_ONLY,
     * ResultSet.CONCUR_READ_ONLY);
     * 
     * ResultSet rs = statement.executeQuery("SELECT * FROM individuals");
     * 
     * while (rs.next()) {
     * System.out.println(rs.getString("individual_name"));
     * }
     */

    newListener();
  }

  public String hash(String password, String salt) {
    return "" + (password + salt).hashCode();
  }

  public void handleClientMessage(PrintWriter out, String message) {
    var messages = message.split(";");

    if (messages[0].compareTo("NEW_SESSION") == 0) {

      System.out.println("Received new session request.");

      out.println("SESSION;" + random.nextInt(10000, 99999));
      out.flush();
      return;

    } else if (messages[0].compareTo("LOGIN") == 0) {

      System.out.println("Received login request.");

      // SELECT *
      // FROM individuals
      // WHERE ssn = username AND password = hashed_password

      String username = messages[1];
      String password = messages[2];

      System.out.println(username);
      System.out.println(password);

      String hashedPassword = hash(password, username);
      System.out.println("Hashed: " + hashedPassword);

      return;
    }

    String rev = new StringBuilder(message).reverse().toString();
    System.out.println("received '" + message + "' from client");
    System.out.print("sending '" + rev + "' to client...");

    out.println(rev);
    out.flush();

    System.out.println("done\n");
  }

  public void run() {
    try {
      SSLSocket socket = (SSLSocket) serverSocket.accept();

      newListener();

      SSLSession session = socket.getSession();
      Certificate[] cert = session.getPeerCertificates();
      String subject = ((X509Certificate) cert[0]).getSubjectX500Principal().getName();

      numConnectedClients++;

      System.out.println("client connected");
      System.out.println("client name (cert subject DN field): " + subject);
      System.out.println(numConnectedClients + " concurrent connection(s)\n");

      PrintWriter out = null;
      BufferedReader in = null;

      out = new PrintWriter(socket.getOutputStream(), true);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

      String clientMsg = null;
      while ((clientMsg = in.readLine()) != null) {
        handleClientMessage(out, clientMsg);
      }

      in.close();
      out.close();
      socket.close();
      numConnectedClients--;

      System.out.println("client disconnected");
      System.out.println(numConnectedClients + " concurrent connection(s)\n");
    } catch (IOException e) {
      System.out.println("Client died: " + e.getMessage());
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
