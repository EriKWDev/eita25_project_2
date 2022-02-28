package client_project;

import java.io.*;
import javax.net.ssl.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
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

  String individualName = null;
  String individualSSN = null;
  String individualAgency = null;
  String individualDivision = null;
  String individualType = null;
  String individualID = null;

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

    System.out.println(result);

    switch (result.getString("kind")) {
      case "LOGIN_FAILED":
        System.out.println();
        System.out.println(String.format("Login failed: %s", result.getString("message")));
        System.out.println();
        return false;

      case "LOGIN_SUCCESS":
        individualType = type;
        individualSSN = username;
        individualName = result.getString("name");
        individualID = result.getString("id");

        System.out.println();
        System.out.println(String.format("+====+ | Welcome back '%s'! | +====+", individualName));
        System.out.println();

        switch (type) {
          case "GOVERNMENT":
            individualAgency = result.getString("agency");
            break;

          case "NURSE":
          case "DOCTOR":
            individualDivision = result.getString("division");
            break;

          default:
            break;
        }
        break;

      default:
        return false;
    }

    return true;
  }

  public void printAbout() {
    System.out
        .println(String.format("You are signed in as '%s' (%s %s)", individualName, individualSSN, individualType));
    System.out.println("Your id is: " + individualID);
    switch (individualType) {

      case "NURSE":
      case "DOCTOR":
        System.out.println("Your hospital division is: " + individualDivision);
        break;

      case "GOVERNMENT":
        System.out.println("Your agency is: " + individualAgency);
        break;

      case "PATIENT":
      default:
        break;
    }
  }

  public void printRecords() throws JSONException, IOException, AssertionError {
    var result = sendReceive(new JSONObject().put("kind", "LIST_RECORDS"));
    var records = result.getJSONArray("records");

    assert (result.getString("kind").compareTo("LIST_RECORDS_RESPONSE") == 0);

    System.out.println("record (patient doctor nurse division): data");
    System.out.println("");

    for (int i = 0; i < records.length(); i++) {
      var record = records.getJSONObject(i);

      System.out.println(
          String.format("%s (%s %s %s %s): %s",
              record.getString("record_id"),
              record.getString("patient_id"),
              record.getString("doctor_id"),
              record.getString("nurse_id"),
              record.getString("division_id"),
              record.getString("data")));
    }
  }

  public void mainLoop() {

    try {
      var success = false;
      while (!success) {

        System.out.println("What type of login do you want to make?");
        System.out.println("You can type one of: PATIENT (default), NURSE, DOCTOR, GOVERNMENT");
        var type = read.readLine().toUpperCase();

        if (type.compareTo("") == 0) {
          type = "PATIENT";
        }

        System.out.println("ssn: (YYMMDD-XXXX)");
        var username = read.readLine();
        System.out.println("password: ");
        var password = read.readLine();
        success = login(username, password, type);
      }
    } catch (IOException | JSONException e) {
      e.printStackTrace();
      return;
    }

    String[] defaultHelpMessages = {
        "GENERAL:",
        "'help'               - Prints this help message.",
        "'quit'               - Signs you out and exits the application.",
        "'about'              - Prints information about your account.",
        "",
        "RECORDS",
        "'records'                                            - Lists records you have access to as <record_id> (<patient>): <record_entry>.",
        "'records update <record_id> <new_entry>'             - Edits record with ID <record_id>",
        "'records delete <record_id>'                         - Deletes record with ID <record_id>",
        "'records create <patient_id> <nurse_id> <new_entry>' - Creates record with ID <record_id>",
    };

    List<String> helpMessages = new ArrayList<String>();
    for (var message : defaultHelpMessages) {
      helpMessages.add(message);
    }

    String[] args;
    boolean done = false;

    System.out.println(String.format("You are signed in as a '%s'!", individualType));
    System.out.println("Type 'help' for help. 'quit' to exit.");

    while (!done) {
      System.out.print("\n> ");

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

          case "about":
            printAbout();
            break;

          case "records":
            printRecords();
            break;

          default:
            handled = false;
            break;
        }

        if (handled) {
          continue;
        }

        System.out.println("Couldn't find command '" + command + "'. Type 'help' for help.");
      } catch (IOException | JSONException | AssertionError e) {
        System.out.println("[ERROR] Something went wrong.");
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    String host = null;
    int port = 1337;

    if (args.length < 2) {
      System.out.println("Please provide host and port: <host> <port>");
      System.out.println("Example: localhost 1337");
      System.exit(-1);
    }

    try { /* get input parameters */
      host = args[0];
      port = Integer.parseInt(args[1]);
    } catch (IllegalArgumentException e) {
      System.exit(-1);
    }

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

      SSLSocketFactory factory = ctx.getSocketFactory();
      SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
      System.out.println("\nsocket before handshake:\n" + socket + "\n");

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
    } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
        | UnrecoverableKeyException | KeyManagementException e) {
      // TODO Auto-generated catch block
      System.out.println("[ERROR] " + e.getMessage());
      System.out.println("[INFO] Did you start the server correctly? Same port and address?");
    }
  }
}
