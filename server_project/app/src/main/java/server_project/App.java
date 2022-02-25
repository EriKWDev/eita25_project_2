package server_project;

import javax.net.*;
import java.io.IOException;
import java.net.*;
import java.sql.SQLException;

import javax.net.ssl.*;

public class App {
  public static void main(String args[]) {
    System.out.println("\nServer Started\n");

    int port = 1337;
    if (args.length >= 1) {
      port = Integer.parseInt(args[0]);
    }

    String type = "TLSv1.2";

    try {
      ServerSocketFactory ssf = Server.getServerSocketFactory(type);
      ServerSocket ss = ssf.createServerSocket(port);
      ((SSLServerSocket) ss).setNeedClientAuth(true); // enables client authentication

      new Server(ss);

    } catch (IOException e) {
      System.out.println("Unable to start Server: " + e.getMessage());
      e.printStackTrace();
    } catch (SQLException e) {
      System.out.println("Unable to start connection to SQLite: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
