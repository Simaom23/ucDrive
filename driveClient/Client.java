package driveClient;

import java.net.*;
import java.util.Scanner;
import java.io.*;

public class Client {
    private static int serversocket = 6000;
    private static String serverhostname = "0.0.0.0";
    public static final String GREEN = "\u001B[32m";
    public static final String RESET = "\u001B[0m";
    public static DataInputStream in;
    public static DataOutputStream out;
    public static String currentDir = System.getProperty("user.dir").replace("\\", "/");

    public static void main(String args[]) {
        // Socket creation
        try (Socket s = new Socket(serverhostname, serversocket)) {
            System.out.println("Welcome to ucDrive 1.0");

            // Input and output stream
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());

            try (Scanner sc = new Scanner(System.in)) {
                while (true) {
                    String checkAuth = in.readUTF();
                    if (checkAuth.equals("false"))
                        authentication(sc);

                    String currentDir = in.readUTF();
                    System.out.print(currentDir + "> ");

                    String[] command = sc.nextLine().trim().split("\\s+");
                    if (command.length > 1 && command[1].charAt(0) == '-') {
                        command[0] = command[0] + " " + command[1];
                        for (int i = 2; i < command.length; i++)
                            command[i - 1] = command[i];
                    }

                    out.writeUTF(command[0]);
                    switch (command[0]) {
                        case "passwd":
                            changePasswd(sc);
                            break;

                        case "ls":
                            listDriveFiles(sc);
                            break;

                        case "cd":
                            out.writeUTF(command[1]);
                            break;

                        case "cd -p":
                            changeUserDir(sc, command[1]);
                            break;

                        case "ls -p":
                            listUserFiles(sc);
                            break;
                    }

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

    private static void authentication(Scanner sc) {
        try {
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
        } catch (IOException e) {
            System.out.println("IO:" + e.getMessage());
        }
    }

    private static void changePasswd(Scanner sc) {
        try {
            System.out.print("Enter current Password: ");
            String password = sc.nextLine();
            out.writeUTF(password);
            String check = in.readUTF();
            if (check.equals("true")) {
                System.out.print("Enter new Password: ");
                password = sc.nextLine();
                out.writeUTF(password);
                System.out.print("Retype new Password: ");
                password = sc.nextLine();
                out.writeUTF(password);
            }
            String changePass = in.readUTF();
            System.out.println(changePass);
        } catch (IOException e) {
            System.out.println("IO:" + e.getMessage());
        }
    }

    private static void listDriveFiles(Scanner sc) {
        try {
            String files = in.readUTF();
            String[] fileList = files.split(" ");
            for (String file : fileList) {
                if (file.charAt(0) == '/')
                    System.out.println(GREEN + file.substring(1) + RESET);
                else
                    System.out.println(file);
            }
        } catch (IOException e) {
            System.out.println("IO:" + e.getMessage());
        }
    }

    private static void listUserFiles(Scanner sc) {
        File cur = new File(currentDir);
        String[] fileList = cur.list();
        for (int i = 0; i < fileList.length; i++) {
            if (new File(currentDir + "/" + fileList[i])
                    .isDirectory())
                fileList[i] = "/" + fileList[i];
        }
        for (String file : fileList) {
            if (file.charAt(0) == '/')
                System.out.println(GREEN + file.substring(1) + RESET);
            else
                System.out.println(file);
        }
    }

    private static void changeUserDir(Scanner sc, String newDir) {
        if (newDir.equals("..")) {
            String[] directory = currentDir.split("/");
            String current = "";
            if (directory.length != 1) {
                for (int i = 0; i < directory.length - 1; i++) {
                    if (i != directory.length - 2)
                        current += directory[i] + "/";
                    else
                        current += directory[i];
                }
                currentDir = current;
                if (currentDir.charAt(currentDir.length() - 1) == ':')
                    currentDir = currentDir + "/";
            }
        } else if (newDir.equals("/"))
            currentDir = "C:/";
        else if (!newDir.equals(".")) {
            String specialChars = "/\\<>:\"|?*";
            if (!specialChars.contains(Character.toString(newDir.charAt(0)))) {
                File dir = new File(currentDir + "/" + newDir);
                if (dir.isDirectory())
                    currentDir = currentDir + "/" + newDir;
            }
        }
    }
}
