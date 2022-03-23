package driveServer;

import java.net.*;
import java.io.*;
import java.util.Properties;

// Primary server
public class Server {
    private static int serverPort = 6000;
    public static String usersFile = "driveServer/users.properties";
    public static Properties users = new Properties();

    public static void main(String args[]) {
        int num = 0;
        try (ServerSocket listenSocket = new ServerSocket(serverPort)) {
            System.out.println("Listening On -> " + listenSocket);
            System.out.println("### - ucDrive Server Info - ###");
            try (InputStream in = new FileInputStream(usersFile)) {
                Server.users.load(in);
                in.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            while (true) {
                Socket clientSocket = listenSocket.accept(); // BLOQUEANTE
                num++;
                System.out.println("[" + num + "] " + "New Connection -> " + clientSocket);
                new Connection(clientSocket, num);
            }
        } catch (IOException e) {
            System.out.println("Listen:" + e.getMessage());
        }
    }
}

// Thread to deal with each user
class Connection extends Thread {
    DataInputStream in;
    DataOutputStream out;
    Socket clientSocket;
    int thread_number;
    boolean auth = false;
    Properties users = Server.users;
    String username;
    String currentDir = "home";

    public Connection(Socket aClientSocket, int num) {
        try {
            thread_number = num;
            clientSocket = aClientSocket;
            in = new DataInputStream(clientSocket.getInputStream());
            out = new DataOutputStream(clientSocket.getOutputStream());
            this.start();
        } catch (IOException e) {
            System.out.println("Connection:" + e.getMessage());
        }
    }

    public void run() {
        try {
            while (true) {
                if (auth)
                    out.writeUTF("true");
                else {
                    out.writeUTF("false");
                    authentication();
                }

                out.writeUTF(currentDir);

                String command = in.readUTF();
                switch (command) {
                    case "passwd":
                        changePasswd();
                        break;

                    case "ls":
                        listFiles();
                        break;

                    case "cd":
                        if (new File("driveServer/Users/" + currentDir).isDirectory())
                            return;
                        break;
                }
            }
        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }

    private void authentication() {
        try {
            while (true) {
                username = in.readUTF();
                String password = in.readUTF();

                if (users.getProperty(username + ".password") != null
                        && password.equals(users.getProperty(username + ".password"))) {
                    out.writeUTF("true");
                    break;
                } else
                    out.writeUTF("false");
            }

            File home = new File("driveServer/Users/" + username + "/home");
            if (!home.exists() && !home.isDirectory())
                home.mkdirs();

            auth = true;
        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }

    private void changePasswd() {
        try {
            String currentPasswd = in.readUTF();
            if (currentPasswd.equals(users.getProperty(username + ".password"))) {
                out.writeUTF("true");
                String newPasswd = in.readUTF();
                String rePasswd = in.readUTF();
                if (newPasswd.equals(rePasswd))
                    try (OutputStream o = new FileOutputStream(Server.usersFile)) {
                        users.setProperty(username + ".password", newPasswd);
                        users.store(o, null);
                        o.close();
                        out.writeUTF("passwd: password updated successfully");
                        auth = false;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                else
                    out.writeUTF("passwd: passwords do not match");
            } else {
                out.writeUTF("false");
                out.writeUTF("passwd: wrong password");
            }
        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }

    private void listFiles() {
        try {
            File dir = new File("driveServer/Users/" + username + "/" + currentDir);
            String[] fileList = dir.list();
            String directories = "";
            for (int i = 0; i < fileList.length; i++) {
                if (new File("driveServer/Users/" + username + "/" + currentDir + "/" + fileList[i])
                        .isDirectory()) {
                    fileList[i] = "/" + fileList[i];
                }
                directories += fileList[i] + " ";
            }
            out.writeUTF(directories);
        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }
}
