package driveClient;

import java.net.*;
import java.util.Scanner;
import java.io.*;

public class Client {
    private static int serversocket = 6000;
    private static String serverhostname = "0.0.0.0";
    public static final String GREEN = "\u001B[32m";
    public static final String RESET = "\u001B[0m";

    public static void main(String args[]) {
        // Socket creation
        try (Socket s = new Socket(serverhostname, serversocket)) {
            System.out.println("Welcome to ucDrive 1.0");

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
                    if (auth.equals("true")) {
                        System.out.println("\nAuthenticated!");
                        break;
                    } else
                        System.out.println("Not valid!\n");
                }

                String currentDir = in.readUTF();
                System.out.print(currentDir + "> ");
                while (true) {
                    String command = sc.nextLine();
                    out.writeUTF(command);
                    switch (command) {
                        case "ls":
                            String files = in.readUTF();
                            String[] fileList = files.split(" ");
                            for (String file : fileList) {
                                if (file.charAt(0) == '/')
                                    System.out.print(GREEN + file.substring(1) + RESET + "\t");
                                else
                                    System.out.print(file + "\t");
                            }
                            System.out.println();
                            break;

                        case "cd":
                            break;
                    }
                    currentDir = in.readUTF();
                    System.out.print(currentDir + "> ");
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