package driveClient;

import java.net.*;
import java.util.Scanner;
import java.io.*;

public class Client {
    private static int serversocket = 6000;
    private static String serverhostname = "0.0.0.0";

    public static void main(String args[]) {
        // Socket creation
        try (Socket s = new Socket(serverhostname, serversocket)) {
            System.out.println("### - Connected to ucDrive - ####");

            // Input and output stream
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            try (Scanner sc = new Scanner(System.in)) {
                while (true) {
                    // Get username
                    System.out.print("Username: ");
                    String username = sc.nextLine();
                    out.writeUTF(username);

                    // Get password
                    System.out.print("Password: ");
                    String password = sc.nextLine();
                    out.writeUTF(password);

                    // Server auth
                    String auth = in.readUTF();
                    if (auth == "true")
                        break;
                    else
                        System.out.print("Not valid!");
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