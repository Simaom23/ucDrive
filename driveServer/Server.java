package driveServer;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.Semaphore;

// Primary server
public class Server {
    public static InetAddress primaryAddress;
    public static int primaryPort;
    public static int primaryDataPort;
    private static int primaryUdpPort;

    public static InetAddress secondaryAddress;
    private static int secondaryPort;
    public static int secondaryDataPort;
    private static int secondaryUdpPort;

    private static String root = System.getProperty("user.dir").replace("\\", "/");
    public static String usersFile = "users.properties";
    public static String confFile = "conf.properties";
    public static Properties users = new Properties();
    public static Properties conf = new Properties();
    public static Semaphore semaphore = new Semaphore(1);

    public static void main(String args[]) {
        try (InputStream in = new FileInputStream(confFile)) {
            Server.conf.load(in);
            in.close();

            primaryAddress = InetAddress.getByName(conf.getProperty("primary.address"));
            primaryPort = Integer.parseInt(conf.getProperty("primary.port"));
            primaryDataPort = Integer.parseInt(conf.getProperty("primary.port")) + 1;
            primaryUdpPort = Integer.parseInt(conf.getProperty("primary.udp"));

            secondaryAddress = InetAddress.getByName(conf.getProperty("secondary.address"));
            secondaryPort = Integer.parseInt(conf.getProperty("secondary.port"));
            secondaryDataPort = Integer.parseInt(conf.getProperty("secondary.port")) + 1;
            secondaryUdpPort = Integer.parseInt(conf.getProperty("secondary.udp"));

        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Listen:" + e.getMessage());
        }

        int num = 0;

        ping();
        pong();

        try (ServerSocket listenSocket = new ServerSocket(primaryPort, 50, primaryAddress)) {
            sendBackup(confFile);
            sendBackup(usersFile);
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

    public static void receiveBackup() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket aSocket = new DatagramSocket(primaryUdpPort + 2)) {
                    while (true) {
                        try {
                            byte[] b;
                            DatagramPacket dp;
                            String s = "ACKN";

                            // Receive File Name
                            b = new byte[1024];
                            dp = new DatagramPacket(b,
                                    b.length);
                            aSocket.receive(dp);
                            b = dp.getData();
                            String fileName = new String(b, 0, dp.getLength());
                            int port = dp.getPort();

                            // Send ACKN
                            b = s.getBytes();
                            dp = new DatagramPacket(b, b.length, secondaryAddress,
                                    port);
                            aSocket.send(dp);

                            // Receive File Size
                            b = new byte[1024];
                            dp = new DatagramPacket(b,
                                    b.length);
                            aSocket.receive(dp);
                            b = dp.getData();
                            String sizeString = new String(b, 0, dp.getLength());

                            // Send ACKN
                            b = s.getBytes();
                            dp = new DatagramPacket(b, b.length, secondaryAddress,
                                    port);
                            aSocket.send(dp);

                            // Create File Stream
                            long fileLength = Long.parseLong(sizeString);
                            String[] dirs = fileName.split("/");
                            String dir = "";
                            for (int i = 0; i < dirs.length - 1; i++) {
                                dir += dirs[i] + "/";
                            }
                            File f = new File(dir);
                            if (!f.exists() && !f.isDirectory())
                                f.mkdirs();

                            FileOutputStream out = new FileOutputStream(root + "/" + fileName);
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
                                b = s.getBytes();
                                dp = new DatagramPacket(b, b.length, secondaryAddress,
                                        port);
                                aSocket.send(dp);
                            }
                            bos.flush();
                            out.close();

                        } catch (Exception ex) {
                            ex.printStackTrace();
                            System.exit(1);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // This is the function responsible for the file synchronization amidst the 2
    // servers
    public static void sendBackup(String f) {

        // Creates new thread to send the updated file to the secondary server
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    semaphore.acquire();
                    System.out.println("Acquired");
                } catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                try (DatagramSocket aSocket = new DatagramSocket()) {
                    byte[] b;
                    DatagramPacket dp;

                    // Send File Name
                    File newFile = new File(root + "/" + f);
                    while (true) {
                        b = f.getBytes();
                        dp = new DatagramPacket(b, b.length, secondaryAddress,
                                secondaryUdpPort + 2);
                        aSocket.send(dp);

                        try {
                            aSocket.setSoTimeout(100);
                            // Receive ACKN
                            b = new byte[1024];
                            dp = new DatagramPacket(b,
                                    b.length);
                            aSocket.receive(dp);

                            break;
                        } catch (IOException e) {
                            continue;
                        }
                    }

                    // Send File Size
                    long fileLength = newFile.length();
                    String size = Long.toString(fileLength);

                    while (true) {
                        b = size.getBytes();
                        dp = new DatagramPacket(b, b.length, secondaryAddress,
                                secondaryUdpPort + 2);
                        aSocket.send(dp);
                        try {
                            aSocket.setSoTimeout(100);
                            // Receive ACKN
                            b = new byte[1024];
                            dp = new DatagramPacket(b,
                                    b.length);
                            aSocket.receive(dp);
                            break;
                        } catch (IOException e) {
                            continue;
                        }
                    }

                    // Create File Stream
                    FileInputStream fis = new FileInputStream(f);
                    BufferedInputStream bis = new BufferedInputStream(fis);

                    // Send file via UDP
                    long sent = 0;
                    int byteSize = 1024;
                    while (sent != fileLength) {
                        if (fileLength - sent >= byteSize)
                            sent += byteSize;
                        else {
                            byteSize = (int) (fileLength - sent);
                            sent = fileLength;
                        }

                        // Send file data
                        byte[] data = new byte[byteSize];
                        bis.read(data);
                        DatagramPacket sendPacket = new DatagramPacket(data, data.length, secondaryAddress,
                                secondaryUdpPort + 2);
                        aSocket.send(sendPacket);

                        while (true) {
                            b = new byte[1024];
                            dp = new DatagramPacket(b, b.length);
                            try {
                                aSocket.setSoTimeout(100);
                                // Receive ACKN
                                aSocket.receive(dp);
                                break;
                            } catch (SocketTimeoutException e) {
                                aSocket.send(sendPacket);
                                continue;
                            }
                        }
                    }
                    fis.close();
                } catch (IOException e) {
                    System.out.println(e);
                }
                try {
                    semaphore.release();
                    System.out.println("Released");
                } catch (Exception e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        }).start();
    }

    private static void ping() {
        while (true) {
            try (DatagramSocket aSocket = new DatagramSocket(primaryUdpPort)) {

                receiveBackup();

                int notReachable = 0;
                while (notReachable < 5) {
                    String str = "PING";
                    byte[] buf = new byte[1024];
                    buf = str.getBytes();

                    // Create a datagram packet to send as an UDP packet
                    DatagramPacket ping = new DatagramPacket(buf, buf.length, secondaryAddress, secondaryUdpPort);
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
                InetAddress primaryAddressAux = primaryAddress;
                int primaryPortAux = primaryPort;
                int primaryDataPortAux = primaryDataPort;
                int primaryUdpPortAux = primaryUdpPort;

                primaryAddress = secondaryAddress;
                primaryPort = secondaryPort;
                primaryDataPort = secondaryDataPort;
                primaryUdpPort = secondaryUdpPort;

                secondaryAddress = primaryAddressAux;
                secondaryPort = primaryPortAux;
                secondaryDataPort = primaryDataPortAux;
                secondaryUdpPort = primaryUdpPortAux;

                continue;
            }
            break;
        }
    }

    private static void pong() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket aSocket = new DatagramSocket(primaryUdpPort)) {
                    while (true) {
                        byte[] buffer = new byte[1024];
                        DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                        aSocket.receive(reply);
                        String str = "pong";
                        buffer = str.getBytes();
                        InetAddress host = reply.getAddress();
                        DatagramPacket request = new DatagramPacket(buffer, buffer.length, host, secondaryUdpPort);
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
    String root = System.getProperty("user.dir").replace("\\", "/") + "/Users";

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

    // Handles all the requests from the client and parses them
    public void run() {
        try {
            while (true) {

                // Check if the user is logged in
                if (auth)
                    out.writeUTF("true");
                else {
                    out.writeUTF("false");
                    authentication();
                }

                // Send the client the current directory
                out.writeUTF(currentDir);

                // Receive command from client
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

                // Receives username from client and password from client
                username = in.readUTF();
                String password = in.readUTF();

                // Checks the credentials and responds to the client
                if (users.getProperty(username + ".password") != null
                        && password.equals(users.getProperty(username + ".password"))) {
                    out.writeUTF("true");
                    break;
                } else
                    out.writeUTF("false");
            }

            // Load all the data about the user that logged in previously
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

            // Gets current password to check if it is right
            String currentPasswd = in.readUTF();
            if (currentPasswd.equals(users.getProperty(username + ".password"))) {

                // Respond to client saying the password is correct
                out.writeUTF("true");

                // Reads new password and verification and parses them responding to the client
                // in the end
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
        Server.sendBackup("users.properties");
    }

    private void listFiles() {
        try {

            // Get file list and parse it
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

            // Return the file list to the client
            out.writeUTF(directories);

        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }

    private void changeDir() {
        try {
            // Receive new directory from client
            String newDir = in.readUTF();

            if (newDir.equals(""))
                return;

            // Parses new directory with due restrictions
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

            Server.sendBackup("Users/" + username + "/" + username + ".properties");
        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }

    private void getFile() {
        try {
            // Receive filename
            String file = in.readUTF();
            if (!file.equals("")) {
                File f = new File(root + "/" + username + "/" + currentDir + "/" + file);
                if (Files.isReadable(f.toPath())) {

                    // Gives response to client saying it is downloadable and its size
                    out.writeUTF("true");
                    long fileLength = f.length();
                    out.writeUTF(Long.toString(fileLength));

                    // Creates new thread that will send the file to the client
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try (ServerSocket dataSocket = new ServerSocket(Server.primaryDataPort, 50,
                                    Server.primaryAddress)) {
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

            // Receive file name and the confirmation to upload
            String file = in.readUTF();
            String answer = in.readUTF();

            if (!answer.equals("cancel")) {

                // Creates the new thread that will receive all the data from the client and
                // create the respective file
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try (ServerSocket dataSocket = new ServerSocket(Server.primaryDataPort, 50,
                                Server.primaryAddress)) {
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

                            Server.sendBackup("Users/" + username + "/" + currentDir + "/"
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
