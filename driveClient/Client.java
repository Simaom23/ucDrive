package driveClient;

import java.net.*;
import java.util.Scanner;
import java.io.*;

public class Client {
    private static int serversocket = 6000;

    public static void main(String args[]) {
        // args[0] <- hostname of destination
        if (args.length == 0) {
            System.out.println("java Client hostname");
            System.exit(0);
        }

        // Socket creation
        try (Socket s = new Socket(args[0], serversocket)) {
            System.out.println("### - Connected to ucDrive - ####");

            // Input and output stream
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            try (Scanner sc = new Scanner(System.in)) {
                while (true) {
                    // Get username
                    System.out.print("Username: ");
                    String username = sc.nextLine();

                    // Get password
                    System.out.print("Password: ");
                    String password = sc.nextLine();

                    // Send username and password to server
                    out.writeUTF(username);
                    out.writeUTF(password);

                    // Server auth
                    String auth = in.readUTF();
                    if (auth == "true")
                        break;
                }
            }

        } catch (UnknownHostException e) {
            System.out.println("Sock:" + e.getMessage());
        } catch (EOFException e) {
            System.out.println("EOF:" + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO:" + e.getMessage());
        }
    }
}