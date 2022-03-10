package driveServer;

// TCPServer2.java: Multithreaded server
import java.net.*;
import java.util.LinkedList;
import java.io.*;

public class Server {
    private static int serverPort = 6000;

    public static void main(String args[]) {
        int numero = 0;
        Socket[] socketList = new Socket[2];

        try (ServerSocket listenSocket = new ServerSocket(serverPort)) {
            System.out.println("A escuta no porto 6000");
            System.out.println("LISTEN SOCKET=" + listenSocket);
            while (true) {
                Socket clientSocket = listenSocket.accept(); // BLOQUEANTE
                socketList[numero] = clientSocket;
                System.out.println("CLIENT_SOCKET (created at accept())=" + clientSocket);
                numero++;
                new Connection(clientSocket, numero, socketList);
            }
        } catch (IOException e) {
            System.out.println("Listen:" + e.getMessage());
        }
    }
}

// = Thread para tratar de cada canal de comunicação com um cliente
class Connection extends Thread {
    DataInputStream in;
    DataOutputStream out;
    Socket clientSocket;
    int thread_number;
    Socket[] socketL;

    public Connection(Socket aClientSocket, int numero, Socket[] socketList) {
        thread_number = numero;
        try {
            clientSocket = aClientSocket;
            socketL = socketList;
            in = new DataInputStream(clientSocket.getInputStream());
            this.start();
        } catch (IOException e) {
            System.out.println("Connection:" + e.getMessage());
        }
    }

    // =============================
    public void run() {
        String resposta;
        try {
            while (true) {
                // an echo server
                String data = in.readUTF();
                resposta = "T[" + thread_number + "]: " + data;
                System.out.println("T[" + thread_number + "] Recebeu: " + data);
                for (int i = 0; i < socketL.length; i++) {
                    if (i != thread_number - 1) {
                        out = new DataOutputStream(socketL[i].getOutputStream());
                        out.writeUTF(resposta);
                    }
                }
            }
        } catch (EOFException e) {
            System.out.println("EOF:" + e);
        } catch (IOException e) {
            System.out.println("IO:" + e);
        }
    }
}