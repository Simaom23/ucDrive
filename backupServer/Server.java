package backupServer;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Properties;

// Primary server
public class Server {
    private static InetAddress serverAddress;
    private static int serverPort;
    private static int dataPort;
    private static InetAddress primaryAddress;
    private static int primaryPort;
    private static int secondaryUdp;
    public static String usersFile = "backupServer/users.properties";
    public static String confFile = "backupServer/conf.properties";
    public static Properties users = new Properties();
    public static Properties conf = new Properties();
    public static boolean primaryAlive = true;

    public static void main(String args[]) {
        try (InputStream in = new FileInputStream(confFile)) {
            Server.conf.load(in);
            in.close();
            serverAddress = InetAddress.getByName(conf.getProperty("secondary.address"));
            serverPort = Integer.parseInt(conf.getProperty("secondary.port"));
            dataPort = Integer.parseInt(conf.getProperty("secondary.port")) + 1;
            secondaryUdp = Integer.parseInt(conf.getProperty("secondary.udp"));
            primaryAddress = InetAddress.getByName(conf.getProperty("primary.address"));
            primaryPort = Integer.parseInt(conf.getProperty("primary.udp"));
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Listen:" + e.getMessage());
        }

        int num = 0;
        while (true) {
            try (ServerSocket listenSocket = new ServerSocket(serverPort, 50, serverAddress)) {
                try (ServerSocket dataSocket = new ServerSocket(dataPort, 50, serverAddress)) {
                    checkPrimary();
                    continuousCheck(listenSocket, dataSocket);

                    try (InputStream in = new FileInputStream(usersFile)) {
                        Server.users.load(in);
                        in.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    System.out.println("Backup Server Listening On -> " + listenSocket);
                    System.out.println("### - ucDrive Server Info - ###");

                    while (!primaryAlive) {
                        try {
                            Socket clientCommandSocket = listenSocket.accept();
                            Socket clientDataSocket = dataSocket.accept();
                            num++;
                            System.out
                                    .println("[" + num + "] " + "New Connection:\n-> Command Socket: "
                                            + clientCommandSocket
                                            + "\n-> Data Socket: " + clientDataSocket);
                            new Connection(clientCommandSocket, clientDataSocket, num);

                        } catch (SocketException e) {
                            continue;
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Listen:" + e.getMessage());
                }
            } catch (IOException e) {
                System.out.println("Listen:" + e.getMessage());
            }
        }
    }

    private static void copyServerImage() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket aSocket = new DatagramSocket(secondaryUdp)) {
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void checkPrimary() {
        try (DatagramSocket aSocket = new DatagramSocket(secondaryUdp)) {
            int notReachable = 0;
            while (notReachable < 5) {
                String str = "PING";
                byte[] buf = new byte[1024];
                buf = str.getBytes();

                // Create a datagram packet to send as an UDP packet
                DatagramPacket ping = new DatagramPacket(buf, buf.length, primaryAddress, primaryPort);
                // Send the Ping datagram to the specified server
                aSocket.send(ping);

                // Try to receive the packet - but it can fail (timeout)
                try {
                    // Set up the timeout 1000 ms = 1 sec
                    aSocket.setSoTimeout(1000);

                    // Set up an UPD packet for recieving
                    DatagramPacket response = new DatagramPacket(new byte[1024], 1024);

                    // Try to receive the response from the ping
                    aSocket.receive(response);
                } catch (IOException e) {
                    // Print which packet has timed out
                    notReachable++;
                    System.out.println("Timeout " + notReachable);
                    continue;
                }
                notReachable = 0;
            }
            primaryAlive = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void continuousCheck(ServerSocket listenSocket, ServerSocket dataSocket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket aSocket = new DatagramSocket(secondaryUdp)) {
                    int reachable = 0;
                    while (reachable < 5) {
                        byte[] buffer = new byte[1024];
                        DatagramPacket reply;
                        try {
                            aSocket.setSoTimeout(1000);
                            reply = new DatagramPacket(buffer, buffer.length);
                            aSocket.receive(reply);
                        } catch (IOException e) {
                            reachable = 0;
                            continue;
                        }
                        String str = "PONG";
                        buffer = str.getBytes();
                        DatagramPacket request = new DatagramPacket(buffer, buffer.length, primaryAddress, primaryPort);
                        aSocket.send(request);
                        reachable++;
                        System.out.println("Primary Alive " + reachable);
                    }
                    primaryAlive = true;
                    listenSocket.close();
                    dataSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
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
    String root = System.getProperty("user.dir").replace("\\", "/") + "/driveServer/Users";

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

            File home = new File(root + "/" + username + "/home");
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
            File dir = new File(root + "/" + username + "/" + currentDir);
            String[] fileList = dir.list();
            String directories = "";
            for (int i = 0; i < fileList.length; i++) {
                fileList[i] = fileList[i].replace(" ", "%20");
                if (new File(root + "/" + username + "/" + currentDir + "/" + fileList[i])
                        .isDirectory())
                    fileList[i] = "/" + fileList[i];
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
                newDir.replace("[/\\<>:\"|?*]", "%20");
                File dir = new File(root + "/" + username + "/home/" + newDir);
                if (dir.isDirectory())
                    currentDir = currentDir + "/" + newDir;
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
            File f = new File(root + "/" + username + "/" + currentDir + "/" + file);
            if (Files.isReadable(f.toPath())) {
                out.writeUTF("true");
                long fileLength = f.length();
                out.writeUTF(Long.toString(fileLength));
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            FileInputStream send = new FileInputStream(
                                    root + "/" + username + "/" + currentDir + "/" + file);
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
                            out.writeUTF(currentDir);
                        } catch (EOFException e) {
                            System.out.println("EOF:" + e);
                        } catch (IOException e) {
                            System.out.println("IO:" + e);
                        }
                    }
                }).start();
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
            String answer = in.readUTF();
            if (!answer.equals("cancel")) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Long fileSize = Long.parseLong(answer);
                            String[] fileName = file.split("/");
                            byte[] b = new byte[1024];
                            FileOutputStream newFile = new FileOutputStream(
                                    root + "/" + username + "/" + currentDir + "/"
                                            + fileName[fileName.length - 1]);
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
                            out.writeUTF(currentDir);
                        } catch (IOException e) {
                            System.out.println("IO:" + e.getMessage());
                        }
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("IO:" + e.getMessage());
        }
    }
}