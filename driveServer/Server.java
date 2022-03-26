package driveServer;

import java.net.*;
import java.nio.file.Files;
import java.io.*;
import java.util.Properties;

// Primary server
public class Server {
    private static int serverPort = 6000;
    private static int dataPort = 6500;
    public static String usersFile = "driveServer/users.properties";
    public static Properties users = new Properties();

    public static void main(String args[]) {
        int num = 0;
        try (ServerSocket listenSocket = new ServerSocket(serverPort)) {
            try (ServerSocket dataSocket = new ServerSocket(dataPort)) {
                System.out.println("Listening On -> " + listenSocket);
                System.out.println("### - ucDrive Server Info - ###");
                try (InputStream in = new FileInputStream(usersFile)) {
                    Server.users.load(in);
                    in.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                while (true) {
                    Socket clientCommandSocket = listenSocket.accept();
                    Socket clientDataSocket = dataSocket.accept(); // BLOQUEANTE
                    num++;
                    System.out.println("[" + num + "] " + "New Connection:\n-> Command Socket: " + clientCommandSocket
                            + "\n-> Data Socket: " + clientDataSocket);
                    new Connection(clientCommandSocket, clientDataSocket, num);
                }
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
    InputStream inData;
    OutputStream outData;
    Socket clientCommandSocket;
    Socket clientDataSocket;
    int thread_number;
    boolean auth = false;
    Properties users = Server.users;
    String username;
    String currentDir = "home";

    public Connection(Socket commandClientSocket, Socket dataClientSocket, int num) {
        try {
            thread_number = num;
            clientCommandSocket = commandClientSocket;
            in = new DataInputStream(clientCommandSocket.getInputStream());
            out = new DataOutputStream(clientCommandSocket.getOutputStream());
            inData = dataClientSocket.getInputStream();
            outData = dataClientSocket.getOutputStream();
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
                        changeDir();
                        break;

                    case "get":
                        getFile();
                        break;

                    case "put":
                        putFile();
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

    private void changeDir() {
        try {
            String newDir = in.readUTF();
            if (newDir.equals("..") && !currentDir.equals("home")) {
                String[] directory = currentDir.split("/");
                String current = "";
                for (int i = 0; i < directory.length - 1; i++) {
                    if (i != directory.length - 2)
                        current += directory[i] + "/";
                    else
                        current += directory[i];
                }
                currentDir = current;
            } else if (newDir.equals("/") || newDir.equals("home"))
                currentDir = "home";
            else if (!newDir.equals("home") && !newDir.equals(".")) {
                String specialChars = "/\\<>:\"|?*";
                if (!specialChars.contains(Character.toString(newDir.charAt(0)))) {
                    File dir = new File("driveServer/Users/" + username + "/home/" + newDir);
                    if (dir.isDirectory())
                        currentDir = currentDir + "/" + newDir;
                }
            }

        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }

    private void getFile() {
        try {
            String file = in.readUTF();
            File f = new File("driveServer/Users/" + username + "/" + currentDir + "/" + file);
            if (Files.isReadable(f.toPath())) {
                out.writeUTF("true");
                long fileLength = f.length();
                out.writeUTF(Long.toString(fileLength));
                FileInputStream send = new FileInputStream(
                        "driveServer/Users/" + username + "/" + currentDir + "/" + file);
                BufferedInputStream bis = new BufferedInputStream(send);
                byte[] b;
                int byteSize = 1024;
                long sent = 0;
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
                }
                send.close();
            } else
                out.writeUTF("get: file does not exist");
        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }

    private void putFile() {
        try {
            String file = in.readUTF();
            Long fileSize = Long.parseLong(in.readUTF());
            String[] fileName = file.split("/");
            byte[] b = new byte[1024];
            FileOutputStream newFile = new FileOutputStream(
                    "driveServer/Users/" + username + "/" + currentDir + "/" + fileName[fileName.length - 1]);
            BufferedOutputStream bos = new BufferedOutputStream(newFile);
            int read = 0;
            long bytesRead = 0;
            while (bytesRead != fileSize) {
                read = inData.read(b);
                bytesRead += read;
                bos.write(b, 0, read);
            }
            bos.flush();
            newFile.close();
        } catch (

        IOException e) {
            System.out.println("IO:" + e.getMessage());
        }
    }
}
