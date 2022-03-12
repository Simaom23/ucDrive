package driveServer;

import java.net.*;
import java.io.*;

// Primary server
public class Server {
    private static int serverPort = 6000;

    public static void main(String args[]) {
        int num = 0;
        try (ServerSocket listenSocket = new ServerSocket(serverPort)) {
            System.out.println("Listening On -> " + listenSocket);
            System.out.println("### - Server Info - ###");
            while (true) {
                Socket clientSocket = listenSocket.accept(); // BLOQUEANTE
                System.out.println("New Connection -> " + clientSocket);
                num++;
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
                String username = in.readUTF();
                String password = in.readUTF();
            }
        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }
}