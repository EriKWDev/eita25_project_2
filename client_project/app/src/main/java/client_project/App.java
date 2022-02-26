package client_project;

import java.io.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.HashMap;
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
  public static void mainLoop(BufferedReader read, PrintWriter out, BufferedReader in) throws JSONException {
    String msg = "";
    String session = "";
    int nonce = -1;
    // Console console = System.console();

    try {
      System.out.println("username: ");
      var username = read.readLine();
      System.out.println("password: ");

      // char[] password = console.readPassword();
      var password = read.readLine();
      // System.out.println("Yeyeyeyeye " + username + " " + password);

      // Request a new session
      out.println(new JSONObject().put("kind", "NEW_SESSION").toString());
      out.flush();

      JSONObject newSessionResult = new JSONObject(in.readLine());

      session = newSessionResult.getString("session");
      nonce = newSessionResult.getInt("nonce");

      nonce++;
      out.println(
          new JSONObject()
              .put("kind", "LOGIN")
              .put("session", session)
              .put("nonce", nonce)
              .put("username", username)
              .put("password", password));
      out.flush();

    } catch (IOException e) {
      e.printStackTrace();
    }

    for (;;) {
      System.out.print(">");

      try {
        msg = read.readLine();

        if (msg.equalsIgnoreCase("quit")) {
          break;
        }

        System.out.print("sending '" + msg + "' to server...");

        out.println(msg);
        out.flush();

        System.out.println("done");
        System.out.println("received '" + in.readLine() + "' from server\n");

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

      mainLoop(read, out, in);

      in.close();
      out.close();
      read.close();
      socket.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
