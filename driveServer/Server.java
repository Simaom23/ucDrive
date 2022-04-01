package driveServer;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Properties;

// Primary server
public class Server {
    public static InetAddress serverAddress;
    public static int serverPort;
    public static int dataPort;
    private static int udpPort;
    private static InetAddress secondaryAddress;
    private static int secondaryPort;
    public static String usersFile = "driveServer/users.properties";
    public static String confFile = "driveServer/conf.properties";
    public static Properties users = new Properties();
    public static Properties conf = new Properties();

    public static void main(String args[]) {
        try (InputStream in = new FileInputStream(confFile)) {
            Server.conf.load(in);
            in.close();
            serverAddress = InetAddress.getByName(conf.getProperty("primary.address"));
            serverPort = Integer.parseInt(conf.getProperty("primary.port"));
            dataPort = Integer.parseInt(conf.getProperty("primary.port")) + 1;
            udpPort = Integer.parseInt(conf.getProperty("primary.udp"));
            secondaryAddress = InetAddress.getByName(conf.getProperty("secondary.address"));
            secondaryPort = Integer.parseInt(conf.getProperty("secondary.udp"));
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Listen:" + e.getMessage());
        }

        int num = 0;

        while (true) {
            checkSecondary();
            serverOnline();

            try (ServerSocket listenSocket = new ServerSocket(serverPort, 50, serverAddress)) {
                sendBackup(usersFile);
                sendBackup(confFile);
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
                    num++;
                    System.out
                            .println("[" + num + "] " + "New Connection:\n-> Command Socket: " + clientCommandSocket);
                    new Connection(clientCommandSocket, num);
                }
            } catch (IOException e) {
                System.out.println("Listen:" + e.getMessage());
            }
        }
    }

    public static synchronized void receiveBackup() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket aSocket = new DatagramSocket(udpPort + 2)) {
                    try {
                        // Receive File Name
                        byte[] fileNameBuff = new byte[1024];
                        DatagramPacket fileNamePacket = new DatagramPacket(fileNameBuff,
                                fileNameBuff.length);
                        aSocket.receive(fileNamePacket);
                        byte[] data = fileNamePacket.getData();
                        String fileName = new String(data, 0, fileNamePacket.getLength());
                        int port = fileNamePacket.getPort();

                        // Send ACKN
                        String s = "ACKN";
                        data = s.getBytes();
                        DatagramPacket ackn = new DatagramPacket(data, data.length, secondaryAddress,
                                port);
                        aSocket.send(ackn);

                        // Receive File Size
                        byte[] fileSizeBuff = new byte[1024];
                        DatagramPacket fileSizePacket = new DatagramPacket(fileSizeBuff,
                                fileSizeBuff.length);
                        aSocket.receive(fileSizePacket);
                        data = fileSizePacket.getData();
                        String sizeString = new String(data, 0, fileSizePacket.getLength());

                        // Send ACKN
                        s = "ACKN";
                        data = s.getBytes();
                        ackn = new DatagramPacket(data, data.length, secondaryAddress,
                                port);
                        aSocket.send(ackn);

                        // Create File Stream
                        long fileLength = Long.parseLong(sizeString);
                        File f = new File("backupServer/" + fileName);
                        FileOutputStream out = new FileOutputStream(f);
                        BufferedOutputStream bos = new BufferedOutputStream(out);

                        // Receive File via UDP
                        long received = 0;
                        byte[] message = new byte[1024];
                        while (received != fileLength) {
                            // Receive Data
                            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
                            aSocket.receive(receivedPacket);
                            byte[] d = new byte[receivedPacket.getLength()];
                            System.arraycopy(receivedPacket.getData(), receivedPacket.getOffset(), d, 0,
                                    receivedPacket.getLength());
                            bos.write(d);
                            received += d.length;

                            // Send ACKN
                            s = "ACKN";
                            data = s.getBytes();
                            ackn = new DatagramPacket(data, data.length, secondaryAddress,
                                    port);
                            aSocket.send(ackn);
                        }
                        bos.flush();
                        out.close();

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.exit(1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static synchronized void sendBackup(String f) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket aSocket = new DatagramSocket()) {
                    File newFile = new File(f);
                    boolean reachable = false;
                    String[] fileDir = f.split("/");
                    String fileName = fileDir[fileDir.length - 1];
                    long fileLength = newFile.length();
                    while (!reachable) {
                        // Send File Name
                        byte[] b = fileName.getBytes();
                        DatagramPacket fileNamePacket = new DatagramPacket(b, b.length, secondaryAddress,
                                secondaryPort + 2);
                        aSocket.send(fileNamePacket);

                        try {
                            aSocket.setSoTimeout(100);
                            // Receive ACKN
                            byte[] acknBuff = new byte[1024];
                            DatagramPacket ackn = new DatagramPacket(acknBuff,
                                    acknBuff.length);
                            aSocket.receive(ackn);
                            ackn.getData();
                        } catch (IOException e) {
                            continue;
                        }

                        // Send File Size
                        while (true) {
                            String size = Long.toString(fileLength);
                            b = size.getBytes();
                            DatagramPacket fileSizePacket = new DatagramPacket(b, b.length, secondaryAddress,
                                    secondaryPort + 2);
                            aSocket.send(fileSizePacket);
                            try {
                                aSocket.setSoTimeout(100);
                                // Receive ACKN
                                byte[] acknBuff = new byte[1024];
                                DatagramPacket ackn = new DatagramPacket(acknBuff,
                                        acknBuff.length);
                                aSocket.receive(ackn);
                                ackn.getData();
                                break;
                            } catch (IOException e) {
                                aSocket.send(fileSizePacket);
                                continue;
                            }
                        }

                        // Create File Stream
                        FileInputStream fis = new FileInputStream(f);
                        BufferedInputStream bis = new BufferedInputStream(fis);

                        // Send File via UDP
                        long sent = 0;
                        int byteSize = 1024;
                        while (sent != fileLength) {
                            if (fileLength - sent >= byteSize)
                                sent += byteSize;
                            else {
                                byteSize = (int) (fileLength - sent);
                                sent = fileLength;
                            }

                            // Send Data
                            byte[] data = new byte[byteSize];
                            bis.read(data);
                            DatagramPacket sendPacket = new DatagramPacket(data, data.length, secondaryAddress,
                                    secondaryPort + 2);
                            aSocket.send(sendPacket);

                            while (true) {
                                byte[] acknBuff = new byte[1024];
                                DatagramPacket ackn = new DatagramPacket(acknBuff, acknBuff.length);
                                try {
                                    aSocket.setSoTimeout(100);
                                    // Receive ACKN
                                    aSocket.receive(ackn);
                                    ackn.getData();
                                    break;
                                } catch (SocketTimeoutException e) {
                                    aSocket.send(sendPacket);
                                    continue;
                                }
                            }
                        }
                        fis.close();
                        reachable = true;
                    }
                } catch (IOException e) {
                    System.out.println(e);
                }
            }
        }).start();
    }

    private static void checkSecondary() {
        try (DatagramSocket aSocket = new DatagramSocket(udpPort)) {
            int notReachable = 0;
            while (notReachable < 5) {
                String str = "PING";
                byte[] buf = new byte[1024];
                buf = str.getBytes();

                // Create a datagram packet to send as an UDP packet
                DatagramPacket ping = new DatagramPacket(buf, buf.length, secondaryAddress, secondaryPort);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void serverOnline() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket aSocket = new DatagramSocket(udpPort)) {
                    while (true) {
                        byte[] buffer = new byte[1024];
                        DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                        aSocket.receive(reply);
                        String str = "PONG";
                        buffer = str.getBytes();
                        InetAddress host = reply.getAddress();
                        DatagramPacket request = new DatagramPacket(buffer, buffer.length, host, secondaryPort);
                        aSocket.send(request);
                    }
                } catch (IOException e) {
                    System.out.println("IO:" + e);
                }
            }
        }).start();
    }
}

// Thread to deal with each user
class Connection extends Thread {
    DataInputStream in;
    DataOutputStream out;
    Socket clientCommandSocket;
    int thread_number;
    boolean auth = false;
    Properties users = Server.users;
    String username;
    String currentDir = "home";
    Properties userConfig = new Properties();
    String root = System.getProperty("user.dir").replace("\\", "/") + "/driveServer/Users";

    public Connection(Socket commandClientSocket, int num) {
        try {
            thread_number = num;
            clientCommandSocket = commandClientSocket;
            in = new DataInputStream(clientCommandSocket.getInputStream());
            out = new DataOutputStream(clientCommandSocket.getOutputStream());

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

            File conf = new File(root + "/" + username + "/" + username + ".properties");
            if (!conf.exists() && !conf.isFile()) {
                try (OutputStream output = new FileOutputStream(
                        root + "/" + username + "/" + username + ".properties")) {
                    userConfig.setProperty("currentDir", "home");
                    userConfig.store(output, null);
                } catch (IOException io) {
                    io.printStackTrace();
                }
            } else {
                try (InputStream input = new FileInputStream(root + "/" + username + "/" + username + ".properties")) {
                    userConfig.load(input);
                    currentDir = userConfig.getProperty("currentDir");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

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
            } else if (newDir.equals("/") || newDir.equals("home") || newDir.equals("."))
                currentDir = "home";
            else if (!newDir.equals("home") && !newDir.equals(".") && !newDir.equals("..")) {
                newDir.replace("[/\\<>:\"|?*]", "%20");
                File dir = new File(root + "/" + username + "/home/" + newDir);
                if (dir.isDirectory())
                    currentDir = currentDir + "/" + newDir;
            }
            try (OutputStream output = new FileOutputStream(
                    root + "/" + username + "/" + username + ".properties")) {
                userConfig.setProperty("currentDir", currentDir);
                userConfig.store(output, null);
            } catch (IOException io) {
                io.printStackTrace();
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
                        try (ServerSocket dataSocket = new ServerSocket(Server.dataPort, 50, Server.serverAddress)) {
                            Socket dataClientSocket = dataSocket.accept();
                            OutputStream outData;
                            outData = dataClientSocket.getOutputStream();
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
                        try (ServerSocket dataSocket = new ServerSocket(Server.dataPort, 50, Server.serverAddress)) {
                            Socket dataClientSocket = dataSocket.accept();
                            InputStream inData;
                            inData = dataClientSocket.getInputStream();
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
                            Server.sendBackup(root + "/" + username + "/" + currentDir + "/"
                                    + fileName[fileName.length - 1]);
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
