package driveClient;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Scanner;

public class Client {
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";
    private static String config = "driveClient/settings.properties";
    private static String currentDir = System.getProperty("user.dir").replace("\\", "/");
    private static Properties server = new Properties();
    private static String serverAddress;
    private static int serverPort;
    private static String primaryAddress;
    private static int primaryPort;
    private static String secondaryAddress;
    private static int secondaryPort;
    private static DataInputStream in;
    private static DataOutputStream out;
    private static boolean primary = true;
    private static boolean exit = false;

    public static void main(String args[]) throws InterruptedException {

        // Read config file
        try (InputStream conf = new FileInputStream(config)) {
            server.load(conf);
            conf.close();
            primaryAddress = server.getProperty("primary.server");
            primaryPort = Integer.parseInt(server.getProperty("primary.port"));
            secondaryAddress = server.getProperty("secondary.server");
            secondaryPort = Integer.parseInt(server.getProperty("secondary.port"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        serverAddress = primaryAddress;
        serverPort = primaryPort;

        // Socket creation
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                try (Socket c = new Socket(serverAddress, serverPort)) {
                    System.out.println("Welcome to ucDrive 1.0");

                    // Input and output stream
                    in = new DataInputStream(c.getInputStream());
                    out = new DataOutputStream(c.getOutputStream());

                    while (true) {

                        // Authenticates user
                        String checkAuth = in.readUTF();
                        if (checkAuth.equals("false"))
                            authentication(sc);

                        // Shows current directory in terminal
                        String currentDir = in.readUTF();
                        System.out.print(currentDir + "> ");

                        // Parse input command
                        String[] command = sc.nextLine().trim().split("\\s+");
                        if (command.length > 1) {
                            if (command[1].charAt(0) == '-') {
                                command[0] = command[0] + " " + command[1];
                                command[1] = "";
                                for (int i = 2; i < command.length; i++)
                                    if (i != command.length - 1)
                                        command[1] += command[i] + " ";
                                    else
                                        command[1] += command[i];
                            } else {
                                for (int i = 2; i < command.length; i++)
                                    command[1] = command[1] + " " + command[i];
                            }
                            command[1].replace("[/\\<>:\"|?*]", "%20");
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

                            case "get":
                                getFile(command[1]);
                                break;

                            case "put":
                                putFile(command[1]);
                                break;

                            case "server -primary":
                                changeServer(sc, "primary");
                                break;

                            case "server -secondary":
                                out.writeUTF(command[0]);
                                changeServer(sc, "secondary");
                                break;

                            case "exit":

                                exit = true;
                                break;
                        }
                        if (exit)
                            break;

                    }

                } catch (UnknownHostException e) {
                    System.out.println("Sock: " + e.getMessage());
                } catch (EOFException e) {
                    System.out.println("EOF: " + e.getMessage());
                } catch (IOException e) {
                    
                    // Switch servers automatically when they are down
                    if (primary) {
                        primary = false;
                        serverAddress = secondaryAddress;
                        serverPort = secondaryPort;
                    } else {
                        primary = true;
                        serverAddress = primaryAddress;
                        serverPort = primaryPort;
                    }
                    System.out.println("Switching server...");
                    Thread.sleep(250);
                }
                if (exit)
                    break;
            }
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

    // Receives files and directories names from server and prints them accordingly
    private static void listDriveFiles(Scanner sc) {
        try {

            // Receive file list from server
            String files = in.readUTF();

            // Print files
            String[] fileList = files.split(" ");
            for (String file : fileList) {
                file = file.replace("%20", " ");
                if (file.charAt(0) == '/') {
                    System.out.println(GREEN + file.substring(1) + RESET);
                } else
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
            newDir.replace("[/\\<>:\"|?*]", "%20");
            File dir = new File(currentDir + "/" + newDir);
            if (dir.isDirectory())
                currentDir = currentDir + "/" + newDir;
        }
    }

    private static void getFile(String file) {
        try {

            // Sends name file to the server and waits for the confirmation
            out.writeUTF(file);
            String answer = in.readUTF();
            if (answer.equals("true")) {
                Long fileSize = Long.parseLong(in.readUTF());

                // Create new thread that will receive all the data from the server
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try (Socket d = new Socket(serverAddress, serverPort + 1)) {
                            InputStream inData;
                            inData = d.getInputStream();
                            String[] fileName = file.split("/");
                            byte[] b = new byte[1024];
                            FileOutputStream newFile = new FileOutputStream(
                                    currentDir + "/" + fileName[fileName.length - 1]);
                            BufferedOutputStream bos = new BufferedOutputStream(newFile);
                            int read = 0;
                            long bytesRead = 0;
                            // int lastProgress = 0;
                            while (bytesRead != fileSize) {
                                read = inData.read(b);
                                bytesRead += read;
                                bos.write(b, 0, read);

                                /*
                                 * Progress bar without threading
                                 * int progress = (int) ((bytesRead * 100) / fileSize);
                                 * if (lastProgress != progress) {
                                 * System.out.print(GREEN +
                                 * "Downloading " + fileName[fileName.length - 1] + " [" + RESET);
                                 * for (int i = 0; i < (int) (progress / 2); i++)
                                 * System.out.print(GREEN + "#" + RESET);
                                 * System.out.print(GREEN + "] " + progress + "%\r" + RESET);
                                 * lastProgress = progress;
                                 * }
                                 */
                            }
                            System.out.println(GREEN + "\n\n" + fileName[fileName.length - 1] + " Downloaded!" + RESET);
                            bos.flush();
                            newFile.close();

                            // Read new directory and display it
                            String dir = in.readUTF();
                            System.out.print(dir + "> ");
                        } catch (IOException e) {
                            System.out.println("IO:" + e.getMessage());
                        }
                    }
                }).start();
            } else
                System.out.println(answer);
        } catch (IOException e) {
            System.out.println("IO:" + e.getMessage());
        }
    }

    private static void putFile(String file) {
        try {

            // Send the file name and size of the file
            out.writeUTF(file);
            File f = new File(currentDir + "/" + file);
            if (Files.isReadable(f.toPath())) {
                long fileLength = f.length();
                out.writeUTF(Long.toString(fileLength));

                // Create new thread that will upload all the data to the server
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                        try (Socket d = new Socket(serverAddress, serverPort + 1)) {
                            OutputStream outData;
                            outData = d.getOutputStream();
                            FileInputStream send = new FileInputStream(currentDir + "/" + file);
                            BufferedInputStream bis = new BufferedInputStream(send);
                            byte[] b;
                            int byteSize = 1024;
                            long sent = 0;
                            // int lastProgress = 0;
                            while (sent < fileLength) {
                                if (fileLength - sent >= byteSize)
                                    sent += byteSize;
                                else {
                                    byteSize = (int) (fileLength - sent);
                                    sent = fileLength;
                                }
                                b = new byte[byteSize];
                                bis.read(b, 0, b.length);
                                outData.write(b, 0, b.length);

                                /*
                                 * Progress bar without threading
                                 * int progress = (int) ((sent * 100) / fileLength);
                                 * if (lastProgress != progress) {
                                 * System.out.print(GREEN +
                                 * "Uploading " + file + " [" + RESET);
                                 * for (int i = 1; i < (int) (progress / 2); i++)
                                 * System.out.print(GREEN + "#" + RESET);
                                 * System.out.print(GREEN + "] " + progress + "%\r" + RESET);
                                 * lastProgress = progress;
                                 * }
                                 */
                            }
                            System.out.println(GREEN + "\n\n" + file + " Uploaded!" + RESET);
                            send.close();

                            // Read new directory and display it
                            String dir = in.readUTF();
                            System.out.print(dir + "> ");
                        } catch (EOFException e) {
                            System.out.println("EOF:" + e);
                        } catch (IOException e) {
                            System.out.println("IO:" + e);
                        }
                    }
                }).start();
            } else {
                out.writeUTF("cancel");
                System.out.println("put: file does not exist");
            }
        } catch (

        IOException e) {
            System.out.println("IO:" + e);
        }
    }

    // Updates the config file with the new data
    private static void changeServer(Scanner sc, String s) {
        System.out.print("Enter " + s + " server address: ");
        String newAddress = sc.nextLine();
        System.out.print("Enter " + s + " server port: ");
        String newPort = sc.nextLine();
        try (OutputStream o = new FileOutputStream(config)) {
            server.setProperty(s + ".address", newAddress);
            server.setProperty(s + ".port", newPort);
            server.store(o, null);
            o.close();
            System.out.println("server: " + s + " server updated successfully");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
