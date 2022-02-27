package client_project;

import java.io.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.KeyStore;
import java.security.cert.*;
import org.json.*;

/*
 * This example shows how to set up a key manager to perform client
 * authentication.
 *
 * This program assumes that the client is not inside a firewall.
 * The application can be modified to connect to a server outside
 * the firewall by following SSLSocketClientWithTunneling.java.
 */

public class App {

  String session = "";
  int nonce = -1;

  PrintWriter out;
  BufferedReader in;
  BufferedReader read;

  public App(PrintWriter out, BufferedReader in, BufferedReader read) {
    this.out = out;
    this.in = in;
    this.read = read;
  }

  public void getNewSessionAndNonce() throws JSONException, IOException {
    var result = sendReceive(new JSONObject().put("kind", "NEW_SESSION"));

    this.session = result.getString("session");
    this.nonce = result.getInt("nonce");
  }

  public void send(JSONObject data) throws JSONException {
    if (data.getString("kind").compareTo("NEW_SESSION") != 0) {
      this.nonce++;

      data.put("session", session);
      data.put("nonce", nonce);
    }

    data.put("timestamp", new Date().toString());

    out.println(data.toString());
    out.flush();
  }

  public JSONObject receive() throws JSONException, IOException {
    return new JSONObject(in.readLine());
  }

  public JSONObject sendReceive(JSONObject data) throws JSONException, IOException {
    send(data);
    return receive();
  }

  public boolean login(String username, String password) throws JSONException, IOException {
    return login(username, password, "PATIENT");
  }

  public boolean login(String username, String password, String type) throws JSONException, IOException {
    if (session == "" || nonce == -1) {
      getNewSessionAndNonce();
    }

    var result = sendReceive(new JSONObject()
        .put("kind", "LOGIN")
        .put("type", type)
        .put("username", username)
        .put("password", password));

    switch (result.getString("kind")) {
      case "LOGIN_FAILED":
        return false;

      case "LOGIN_SUCCESS":
        break;

      default:
        return false;
    }

    return true;
  }

  public void mainLoop() {

    try {
      var success = false;
      while (!success) {
        System.out.println("username: ");
        var username = read.readLine();
        System.out.println("password: ");
        var password = read.readLine();

        success = login(username, password);
      }
    } catch (IOException | JSONException e) {
      e.printStackTrace();
      return;
    }

    System.out.println("Welcome!");
    System.out.println("Type 'help' for help. 'quit' to exit.");

    String[] args;
    boolean done = false;

    String[] defaultHelpMessages = {
        "'help'    - Prints this help message.",
        "'quit'    - Signs you out and exits the application"
    };

    List<String> helpMessages = new ArrayList<String>();
    for (var message : defaultHelpMessages) {
      helpMessages.add(message);
    }

    while (!done) {
      System.out.print("> ");

      try {
        args = read.readLine().split(" ");

        var command = args[0].toLowerCase();
        var handled = true;

        // Default commands
        switch (command) {
          case "help":
            for (var message : helpMessages) {
              System.out.println(message);
            }

            break;
          case "quit":
            done = true;
            break;

          default:
            handled = false;
            break;
        }

        if (handled) {
          continue;
        }

        System.out.println("Couldn't find command '" + command + "'. Type 'help' for help.");
      } catch (IOException e) {
        e.printStackTrace();
      }

    }
  }

  public static void main(String[] args) throws Exception {
    String host = null;
    int port = 1337;

    for (int i = 0; i < args.length; i++) {
      System.out.println("args[" + i + "] = " + args[i]);
    }

    if (args.length < 2) {
      System.out.println("USAGE: java client host port");
      System.exit(-1);
    }

    try { /* get input parameters */
      host = args[0];
      port = Integer.parseInt(args[1]);
    } catch (IllegalArgumentException e) {
      System.out.println("USAGE: java client host port");
      System.exit(-1);
    }

    try {
      SSLSocketFactory factory = null;
      try {
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        char[] password = "password".toCharArray();

        // keystore password (storepass)
        ks.load(new FileInputStream("src/main/resources/clientkeystore"), password);

        // truststore password (storepass)
        ts.load(new FileInputStream("src/main/resources/clienttruststore"), password);

        kmf.init(ks, password); // user password (keypass)
        tmf.init(ts); // keystore can be used as truststore here
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        factory = ctx.getSocketFactory();

      } catch (Exception e) {
        throw new IOException(e.getMessage());
      }
      SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
      System.out.println("\nsocket before handshake:\n" + socket + "\n");

      /*
       * send http request
       *
       * See SSLSocketClient.java for more information about why
       * there is a forced handshake here when using PrintWriters.
       */

      socket.startHandshake();

      SSLSession session = socket.getSession();
      Certificate[] cert = session.getPeerCertificates();
      String subject = ((X509Certificate) cert[0]).getSubjectX500Principal().getName();

      System.out.println("certificate name (subject DN field) on certificate received from server:\n" + subject + "\n");
      System.out.println("socket after handshake:\n" + socket + "\n");
      System.out.println("secure connection established\n\n");

      BufferedReader read = new BufferedReader(new InputStreamReader(System.in));
      PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

      var app = new App(out, in, read);
      app.mainLoop();

      in.close();
      out.close();
      read.close();
      socket.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
