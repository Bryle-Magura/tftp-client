package com.tftp;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Scanner;

public class Client {

    /** Scanner object to get user input */
    private Scanner Sc;

    /** String to store server IP address */
    private String Server_IPaddr;

    /** Port number of client */
    private final int Client_PortNum = 8000;

    /** Port number of server */
    private final int Server_PortNum = 69;

    /** Opcode values: */
    private final byte OPCODE_RRQ = 1;
    private final byte OPCODE_WRQ = 2;
    private final byte OPCODE_DATA = 3;
    private final byte OPCODE_ACK = 4;
    private final byte OPCODE_ERR = 5;
    private final byte OPCODE_OACK = 6;

    /** DatagramSocket of Client */
    private DatagramSocket clientSocket;

    /** InetAddress of Server */
    private InetAddress serverAddress;

    /** Packet to be sent to Server */
    private DatagramPacket outBoundPacket;

    /** Packet to be receive from Server */
    private DatagramPacket inBoundPacket;

    /**
     * Main function
     * 
     * @param args [NOT USED]
     */
    public static void main(String[] args) {
        Client client = new Client();
        client.Sc = new Scanner(System.in);

        System.out.println("+-----------------------------------------------------------------+" + "\n" +
                "|                  Welcome to TFTP client program                 |" + "\n" +
                "|          Made by: Kyle Loja and Bryle Magura from S13           |" + "\n" +
                "+-----------------------------------------------------------------+" + "\n");
        System.out.print("   Enter Server ip address: ");

        client.Server_IPaddr = client.Sc.nextLine();

        client.Menu();
    }

    /**
     * Function in handling the Menu
     */
    private void Menu() {
        int nOpt;
        do {
            System.out.println("\n\n");
            nOpt = Menu_Options();
            try {
                switch (nOpt) {
                    case 1:
                        FileUpload();
                        break;
                    case 2:
                        FileDownload();
                        break;
                    case 3:
                        System.out.println("   Exiting. . . .");
                        break;
                    default:
                        System.out.println("   !!! Invalid Option Number !!!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } while (nOpt != 3);
    }

    /**
     * Function to display Menu Options
     * 
     * @return int corresponding to the option selected by the User.
     */
    private int Menu_Options() {
        System.out.print("=====================================================================" + "\n" +
                "                            Main Menu" + "\n" +
                "=====================================================================" + "\n" +
                "   1) Upload file to server" + "\n" +
                "   2) Download file from server" + "\n" +
                "   3) Exit" + "\n" +
                "=====================================================================" + "\n" +
                "   Select Option number: ");
        int nOpt = Sc.nextInt();

        return nOpt;
    }

    /**
     * Function to oversee the whole file uploading process (WRQ)
     * 
     * @throws IOException Handles IOExceptions
     */
    private void FileUpload() throws IOException {
        System.out.print("   Enter file location: ");
        String fileloc = Sc.next();
        String mode = "octet";
        System.out.print("   Enter new filename: ");
        String newfilename = Sc.next();

        File file = new File(fileloc);

        // Check if file exist and is accessible
        if (file.exists() && file.canRead()) {
            boolean isValidAnswer, useOptFields = false;
            int timeout, Blksize;
            long tsize = file.length();

            do {
                System.out.print("\n\n   Do you wish to modify blksize and timeout?" +
                        " (Y or N): ");
                String answer = Sc.next();
                switch (answer) {
                    case "Y":
                        isValidAnswer = true;
                        useOptFields = true;
                        break;
                    case "N":
                        isValidAnswer = true;
                        useOptFields = false;
                        break;
                    default:
                        isValidAnswer = false;
                }
            } while (!isValidAnswer);

            if (useOptFields == true) {
                System.out.print("\n   Enter timeout in seconds: ");
                timeout = Sc.nextInt();

                do {
                    System.out.print("   Enter blksize in bytes (8 to 65434): ");
                    Blksize = Sc.nextInt();
                    if (!(Blksize >= 8 && Blksize <= 65434))
                        System.out.println("   !!! Invalid Blocksize !!!\n");
                } while (Blksize < 8 || Blksize > 65434);
            } else {
                timeout = 20;
                Blksize = 512;
            }

            // Create DatagramSocket and get InetAddress of Server
            clientSocket = new DatagramSocket(null);
            clientSocket.bind(new InetSocketAddress(Client_PortNum));
            serverAddress = InetAddress.getByName(Server_IPaddr);

            byte[] requestPacketArr = null;
            // Generate WRQ Packet
            // byte[] requestPacketArr = CreateWRQPacket(newfilename, mode, Blksize, tsize);
            if (!useOptFields) {
                requestPacketArr = CreateWRQPacket(newfilename, mode);
            } else {
                requestPacketArr = CreateWRQPacket(newfilename, mode, Blksize, tsize);
            }

            // Create DatagramPacket
            outBoundPacket = new DatagramPacket(requestPacketArr, requestPacketArr.length,
                    serverAddress, Server_PortNum);

            // Send WRQ Packet
            clientSocket.send(outBoundPacket);

            boolean success;
            // Send file in the form of Data Packets to server
            success = SendFile(file, Blksize, timeout, tsize);

            if (success == true)
                System.out.println("\n\n   " + file.getName() + " is successfully uploaded to the server");

        } else {
            System.out.println("\n\n   !!! File not found or File is inaccessible !!!");
        }

        // Close client socket
        clientSocket.close();
    }

    private byte[] CreateWRQPacket(String filename, String mode) {
        /*
         * WRQ Packet Format:
         * +--------+----------+---+---------+---+-----------+---+---------------+---+
         * | OPCODE | Filename | 0 | "Octet" | 0 | "Blksize" | 0 | Blksize Value | 0 |
         * +--------+----------+---+---------+---+-----------+---+---------------+---+
         */

        // Caluculate WRQ packet length
        int bytearrlength = 2 + filename.length() + 1 + mode.length() + 1;
        // Byte array to store the wrq packet
        byte[] bytearr = new byte[bytearrlength];

        int index = 0;

        // 0
        bytearr[index] = 0;
        index++;

        // Opcode
        bytearr[index] = OPCODE_WRQ;
        index++;

        // FILENAME
        for (int i = 0; i < filename.length(); i++) {
            bytearr[index] = (byte) filename.charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // Mode: "Octet"
        for (int i = 0; i < mode.length(); i++) {
            bytearr[index] = (byte) mode.charAt(i);
            index++;
        }

        bytearr[index] = 0;

        return bytearr;
    }

    /**
     * Function in charge to generate WRQ Packet stored in a byte array
     * 
     * @param filename Filename of the file to be uploaded
     * @param mode     Mode of transmitting data [Octet]
     * @param blksize  Amount of data to be transffered per packet in bytes
     * @param tsize    Size of the file in bytes
     * @return Byte array corresponding to the WRQ Packet.
     */
    private byte[] CreateWRQPacket(String filename, String mode, int blksize, long tsize) {
        /*
         * WRQ Packet Format:
         * +--------+----------+---+---------+---+-----------+---+---------------+---+--
         * ------+---+-------------+---+
         * | OPCODE | Filename | 0 | Mode | 0 | Blksize | 0 | Blksize Value | 0 |"Tsize"
         * | 0 | Tsize Value | 0 |
         * +--------+----------+---+---------+---+-----------+---+---------------+---+--
         * ------+---+-------------+---+
         * 
         * 
         * 
         */

        // Caluculate WRQ packet length
        int bytearrlength = 2 + filename.length() + 1 + mode.length() + 1 +
                "blksize".length() + 1 + String.valueOf(blksize).length() + 1 +
                "tsize".length() + 1 + String.valueOf(tsize).length() + 1;

        // Byte array to store the wrq packet
        byte[] bytearr = new byte[bytearrlength];

        int index = 0;

        // 0
        bytearr[index] = 0;
        index++;

        // Opcode
        bytearr[index] = OPCODE_WRQ;
        index++;

        // FILENAME
        for (int i = 0; i < filename.length(); i++) {
            bytearr[index] = (byte) filename.charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // Mode: "Octet"
        for (int i = 0; i < mode.length(); i++) {
            bytearr[index] = (byte) mode.charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // Blksize
        for (int i = 0; i < "blksize".length(); i++) {
            bytearr[index] = (byte) "blksize".charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // Blksize value
        for (int i = 0; i < String.valueOf(blksize).length(); i++) {
            bytearr[index] = (byte) String.valueOf(blksize).charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // tsize
        for (int i = 0; i < String.valueOf("tsize").length(); i++) {
            bytearr[index] = (byte) String.valueOf("tsize").charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // tsize value
        for (int i = 0; i < String.valueOf(tsize).length(); i++) {
            bytearr[index] = (byte) String.valueOf(tsize).charAt(i);
            index++;
        }

        bytearr[index] = 0;

        return bytearr;
    }

    /**
     * Function in charge of sending data packets and receiving ACK or Error packets
     * from the server
     * 
     * @param file    File to be uploaded
     * @param tsize   Size of the file in bytes
     * @param blksize Size of the data to be transferred per data packet
     * @param timeout Time in seconds before the DatagramSocket expires
     * @throws IOException Handles IOException
     * 
     * @return True if file is sent successfully, otherwise false
     */
    private boolean SendFile(File file, int blksize, int timeout, long tsize) throws IOException {
        long bytesSent = 0; // Bytes sent in the server
        int blknumber = 1; // Block number of data packet
        int tfptCount = 0; // Packets sent to the server
        boolean success;

        // Set socket timeout
        clientSocket.setSoTimeout(timeout * 1000);

        try {
            // Stores the data receive from the server in Byte Array
            byte[] DataReceived = new byte[blksize + 100];
            inBoundPacket = new DatagramPacket(DataReceived, DataReceived.length,
                    serverAddress, Server_PortNum);

            // Receive OACK / ACK packet from server
            clientSocket.receive(inBoundPacket);

            // Stores op code in a byte array
            byte[] opCode = { DataReceived[0], DataReceived[1] };

            // Cancel the whole process and exit the function if the packet received is an
            // Error packet
            if (opCode[1] == OPCODE_ACK || opCode[1] == OPCODE_OACK) {
                System.out.println("\n\n   ACK for WRQ request has been received. Sending File");
            } else {
                reportError(DataReceived);
                success = false;
                return false;
            }

            // Create BufferedInputStream to read the file
            BufferedInputStream fileInputStream = new BufferedInputStream(new FileInputStream(file));

            // Store the data read from the file in a byte array
            byte[] fileBuffer = new byte[blksize];

            // Number of bytes read
            int bytesRead = fileInputStream.read(fileBuffer);

            // Loops as long as the end of the file hasn't been reached yet
            while (bytesRead != -1) {

                System.out.print("\n   TFTP Packet Count " + tfptCount);

                /*
                 * Data Packet Format:
                 * +--------+-----------+------+
                 * | OPCODE | Blknumber | DATA |
                 * +--------+-----------+------+
                 * 
                 * Default bytes of the Data field is 512
                 */

                // Generate Data Packet:
                byte[] dataPacket = new byte[bytesRead + 4];
                // Opcode
                dataPacket[0] = 0;
                dataPacket[1] = OPCODE_DATA;
                // Block number
                dataPacket[2] = 0;
                dataPacket[3] = (byte) blknumber;
                // Copy data read from the file to the byte array
                System.arraycopy(fileBuffer, 0, dataPacket, 4, bytesRead);
                outBoundPacket = new DatagramPacket(dataPacket, dataPacket.length, serverAddress,
                        inBoundPacket.getPort());

                // Record ServerPort as it changes from a different port when sending tft
                // packets
                int ServerPort = inBoundPacket.getPort();

                // Send data pocket
                clientSocket.send(outBoundPacket);

                // Check the contents of the ACK packet replied by the server.
                byte[] replyPacket = new byte[blksize + 100];
                DatagramPacket PacketReceived = new DatagramPacket(replyPacket, replyPacket.length,
                        serverAddress, ServerPort);
                clientSocket.receive(PacketReceived);

                // If there are errors, cancel the whole process
                if (replyPacket[1] == OPCODE_ERR) {
                    reportError(replyPacket);
                    success = false;
                    break;
                }

                // Update Values:
                bytesSent += Long.valueOf(bytesRead);
                System.out.println("   File Data Sent: " + bytesSent + " / " + tsize);
                bytesRead = fileInputStream.read(fileBuffer);
                tfptCount++;
                blknumber++;
            }

            // Close FileInputStream
            fileInputStream.close();
            success = true;
        } catch (SocketTimeoutException se) {
            System.out.println("\n\n   Error: Unable to contact server ");
            success = false;
        }

        return success;
    }

    /**
     * Function in overseeing the whole file download process (RRQ)
     * 
     * @throws IOException Handles IOException
     */
    private void FileDownload() throws IOException {
        System.out.print("   Enter filename to download: ");
        String filename = Sc.next();
        String mode = "octet";

        boolean isValidAnswer, useOptFields = false;
        int timeout, Blksize;

        do {
            System.out.print("\n\n   Do you wish to modify blksize and timeout?" +
                    " (Y or N): ");
            String answer = Sc.next();
            switch (answer) {
                case "Y":
                    isValidAnswer = true;
                    useOptFields = true;
                    break;
                case "N":
                    isValidAnswer = true;
                    useOptFields = false;
                    break;
                default:
                    isValidAnswer = false;
            }
        } while (!isValidAnswer);

        if (useOptFields == true) {
            System.out.print("\n   Enter timeout in seconds: ");
            timeout = Sc.nextInt();

            do {
                System.out.print("   Enter blksize in bytes (8 to 65434): ");
                Blksize = Sc.nextInt();
                if (!(Blksize >= 8 && Blksize <= 65434))
                    System.out.println("   !!! Invalid Blocksize !!!\n");
            } while (Blksize < 8 || Blksize > 65434);
        } else {
            timeout = 20;
            Blksize = 512;
        }

        // Create DatagramSocket and get InetAddress of Server
        clientSocket = new DatagramSocket(null);
        clientSocket.bind(new InetSocketAddress(Client_PortNum));
        serverAddress = InetAddress.getByName(Server_IPaddr);

        byte[] requestPacketArr = null;

        // Generate RRQ Packet
        if (!useOptFields) {
            requestPacketArr = CreateRRQPacket(filename, mode);
        } else {
            requestPacketArr = CreateRRQPacket(filename, mode, Blksize);
        }

        // Create DatagramPacket
        outBoundPacket = new DatagramPacket(requestPacketArr, requestPacketArr.length,
                serverAddress, Server_PortNum);

        // Send the RRQ packet
        clientSocket.send(outBoundPacket);

        ByteArrayOutputStream byteOutStream = null;
        // Receive the file from the server
        if (!useOptFields) {
            byteOutStream = ReceiveFile(timeout);
        } else {
            byteOutStream = ReceiveFile(Blksize, timeout);
        }

        // Create and write file in the client side if the client successfully receives
        // all Data packets from the server.
        if (byteOutStream != null) {
            writeFile(byteOutStream, filename);
            System.out.println("\n\n   " + filename + " is successfully downloaded");
        }

        // Close client socket
        clientSocket.close();
    }

    private byte[] CreateRRQPacket(String filename, String mode) {
        /*
         * WRQ Packet Format:
         * +--------+----------+---+---------+---+-----------+---+---------------+---+
         * | OPCODE | Filename | 0 | "Octet" | 0 | "Blksize" | 0 | Blksize Value | 0 |
         * +--------+----------+---+---------+---+-----------+---+---------------+---+
         */

        // Caluculate WRQ packet length
        int bytearrlength = 2 + filename.length() + 1 + mode.length() + 1;
        // Byte array to store the wrq packet
        byte[] bytearr = new byte[bytearrlength];

        int index = 0;

        // 0
        bytearr[index] = 0;
        index++;

        // Opcode
        bytearr[index] = OPCODE_RRQ;
        index++;

        // FILENAME
        for (int i = 0; i < filename.length(); i++) {
            bytearr[index] = (byte) filename.charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // Mode: "Octet"
        for (int i = 0; i < mode.length(); i++) {
            bytearr[index] = (byte) mode.charAt(i);
            index++;
        }

        bytearr[index] = 0;

        return bytearr;
    }

    /**
     * Function in charge to generate RRQ packet
     * 
     * @param filename Filename of the file to be downloaded
     * @param mode     Mode of transmitting data [Octet]
     * @param blksize  Amount of data to be transffered per packet in bytes
     * 
     * @return Byte array corresponding to the RRQ Packet.
     */
    private byte[] CreateRRQPacket(String filename, String mode, int blksize) {
        /*
         * RRQ Packet Format: (Same as WRQ Packet)
         * +--------+----------+---+---------+---+-----------+---+---------------+---+
         * | OPCODE | Filename | 0 | "Octet" | 0 | "Blksize" | 0 | Blksize Value | 0 |
         * +--------+----------+---+---------+---+-----------+---+---------------+---+
         * --------+---+-------------+---+
         * "Tsize" | 0 | Tsize Value | 0 |
         * --------+---+-------------+---+
         */

        // Calculate RRQ packet length
        int bytearrlength = 2 + filename.length() + 1 + mode.length() + 1 +
                "blksize".length() + 1 + String.valueOf(blksize).length() + 1 +
                "tsize".length() + 3;

        // Byte array to store RRQ packet
        byte[] bytearr = new byte[bytearrlength];

        int index = 0;

        // OPCODE
        bytearr[index] = 0;
        index++;
        bytearr[index] = OPCODE_RRQ;
        index++;

        // FILENAME
        for (int i = 0; i < filename.length(); i++) {
            bytearr[index] = (byte) filename.charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // mode: "Octet"
        for (int i = 0; i < mode.length(); i++) {
            bytearr[index] = (byte) mode.charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // Blksize
        for (int i = 0; i < "blksize".length(); i++) {
            bytearr[index] = (byte) "blksize".charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // Blksize val
        for (int i = 0; i < String.valueOf(blksize).length(); i++) {
            bytearr[index] = (byte) String.valueOf(blksize).charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        // tsize
        for (int i = 0; i < String.valueOf("tsize").length(); i++) {
            bytearr[index] = (byte) String.valueOf("tsize").charAt(i);
            index++;
        }

        bytearr[index] = 0;
        index++;

        String zero = "0";

        // tsize value (0)
        bytearr[index] = (byte) zero.charAt(0);
        index++;

        bytearr[index] = 0;

        return bytearr;
    }

    /**
     * Function in charge of receiving the file from the server.
     * 
     * @param blksize Size of the data to be received per data packet.
     * @param timeout Time in seconds before the Socket expires
     * @return ByteArrayOutputStream where the data of the received file is stored
     * @throws IOException Handles IOException
     */
    private ByteArrayOutputStream ReceiveFile(int timeout) throws IOException {

        // Stores data of the received file in ByteArrayOutputStream
        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();

        int tftpCount = 0; // Number of tftp packets sent.
        int fileDataReceived = 0; // Size of the file data received in bytes

        // Set socket timeout
        clientSocket.setSoTimeout(timeout * 1000);

        try {
            do {
                System.out.print("\n\n   TFTP Packet count: " + tftpCount);
                tftpCount++;

                // Byte array to store the content of a reply from the server
                byte[] bufferByteArr = new byte[512 + 100];

                // Initialize inBoundPacket to receive reply from the server
                inBoundPacket = new DatagramPacket(bufferByteArr, bufferByteArr.length,
                        serverAddress, Server_PortNum);

                // Receive the reply from the server
                clientSocket.receive(inBoundPacket);

                // Extract opcode from the tftp reply
                byte[] opCode = { bufferByteArr[0], bufferByteArr[1] };

                // If the packet is an Error packet, cancel the whole process
                if (opCode[1] == OPCODE_ERR) {
                    reportError(bufferByteArr);
                    byteOS = null;
                    break;

                }

                // If the packet is a Data packet, extract blocknumber and get the data to store
                // it in ByteArrayOutputStream. Once finished send ACK message to the server
                if (opCode[1] == OPCODE_DATA) {
                    byte[] blkNumber = { bufferByteArr[2], bufferByteArr[3] };
                    DataOutputStream dos = new DataOutputStream(byteOS);
                    dos.write(inBoundPacket.getData(), 4,
                            inBoundPacket.getLength() - 4);

                    fileDataReceived += inBoundPacket.getLength() - 4;
                    System.out.println("   File Data Received: " + fileDataReceived);
                    sendAcknowledgment(blkNumber);
                }

                // If the packet is a OACK packet or ACK packet, extract the tsize value from
                // the packet and send ACK reply to the server
                if (opCode[1] == OPCODE_OACK || opCode[1] == OPCODE_ACK) {
                    /*
                     * ACK Packet:
                     * +--------+-----------+
                     * | Opcode | Blknumber |
                     * +--------+-----------+
                     */
                    byte[] ACK = { 0, OPCODE_ACK, 0, 0 };
                    DatagramPacket ACK_Packet = new DatagramPacket(ACK, ACK.length,
                            serverAddress, inBoundPacket.getPort());

                    clientSocket.send(ACK_Packet);
                }

                // Check if it is last packet
            } while (!isLastPacket(inBoundPacket) || tftpCount == 1);
        } catch (SocketTimeoutException se) {
            System.err.println("\n\n   Error: Unable to connect to server");
            byteOS = null;
        }

        return byteOS;
    }

    private ByteArrayOutputStream ReceiveFile(int blksize, int timeout) throws IOException {

        // Stores data of the received file in ByteArrayOutputStream
        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();

        int tftpCount = 0; // Number of tftp packets sent.
        int fileDataReceived = 0; // Size of the file data received in bytes
        int tsizeValue = 0; // Size of the whole file.

        // Set socket timeout
        clientSocket.setSoTimeout(timeout * 1000);

        try {
            do {
                System.out.print("\n\n   TFTP Packet count: " + tftpCount);
                tftpCount++;

                // Byte array to store the content of a reply from the server
                byte[] bufferByteArr = new byte[blksize + 100];

                // Initialize inBoundPacket to receive reply from the server
                inBoundPacket = new DatagramPacket(bufferByteArr, bufferByteArr.length,
                        serverAddress, Server_PortNum);

                // Receive the reply from the server
                clientSocket.receive(inBoundPacket);

                // Extract opcode from the tftp reply
                byte[] opCode = { bufferByteArr[0], bufferByteArr[1] };

                // If the packet is an Error packet, cancel the whole process
                if (opCode[1] == OPCODE_ERR) {
                    reportError(bufferByteArr);
                    byteOS = null;
                    break;

                }

                // If the packet is a Data packet, extract blocknumber and get the data to store
                // it in ByteArrayOutputStream. Once finished send ACK message to the server
                if (opCode[1] == OPCODE_DATA) {
                    byte[] blkNumber = { bufferByteArr[2], bufferByteArr[3] };
                    DataOutputStream dos = new DataOutputStream(byteOS);
                    dos.write(inBoundPacket.getData(), 4,
                            inBoundPacket.getLength() - 4);

                    fileDataReceived += inBoundPacket.getLength() - 4;
                    System.out.println("   File Data Received: " + fileDataReceived + " / " + tsizeValue + " bytes");
                    sendAcknowledgment(blkNumber);
                }

                // If the packet is a OACK packet or ACK packet, extract the tsize value from
                // the packet and send ACK reply to the server
                if (opCode[1] == OPCODE_OACK || opCode[1] == OPCODE_ACK) {
                    /*
                     * ACK Packet:
                     * +--------+-----------+
                     * | Opcode | Blknumber |
                     * +--------+-----------+
                     */
                    byte[] ACK = { 0, OPCODE_ACK, 0, 0 };
                    DatagramPacket ACK_Packet = new DatagramPacket(ACK, ACK.length,
                            serverAddress, inBoundPacket.getPort());

                    String oackOptions = new String(bufferByteArr, 2, inBoundPacket.getLength() - 2);
                    String[] options = oackOptions.split("\0");

                    for (int i = 0; i < options.length; i += 2) {
                        String option = options[i];
                        if (option.equals("tsize")) {
                            tsizeValue = Integer.parseInt(options[i + 1]);
                            break;
                        }
                    }
                    clientSocket.send(ACK_Packet);
                }

                // Check if it is last packet
            } while (!isLastPacket(tsizeValue, fileDataReceived) || tftpCount == 1);
        } catch (SocketTimeoutException se) {
            System.err.println("\n\n   Error: Unable to connect to server");
            byteOS = null;
        }

        return byteOS;
    }

    /**
     * Function in charge of displaying error packets received from server
     * 
     * @param bufferByteArr Byte array containing the error packet
     */
    private void reportError(byte[] bufferByteArr) {
        String errorText = new String(bufferByteArr, 4,
                inBoundPacket.getLength() - 4);
        System.err.println("\n   Error: " + errorText +
                " [" + bufferByteArr[2] + "x0" + bufferByteArr[3] + "]");
    }

    /**
     * Function to send ACK packets to the server used by the ReceiveFile()
     * 
     * @param blkNumber Byte array containing the block number
     */
    private void sendAcknowledgment(byte[] blkNumber) {
        /*
         * ACK Packet:
         * +--------+-----------+
         * | Opcode | Blknumber |
         * +--------+-----------+
         */
        byte[] ACK = { 0, OPCODE_ACK, blkNumber[0], blkNumber[1] };

        DatagramPacket ackPacket = new DatagramPacket(ACK, ACK.length, serverAddress,
                inBoundPacket.getPort());

        try {
            clientSocket.send(ackPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isLastPacket(DatagramPacket datagramPacket) {
        if (datagramPacket.getLength() < 512)
            return true;
        else
            return false;
    }

    /**
     * Function to check if the packet is the last to be received by the
     * ReceiveFile()
     * 
     * @param tsize         Size of the file to be received
     * @param bytesReceived Bytes received by the client
     * @return True if bytesReceived >= tsize, else returns false
     */
    private boolean isLastPacket(int tsize, int bytesReceived) {
        if (bytesReceived >= tsize)
            return true;
        else
            return false;
    }

    /**
     * Function write the file in the client's side.
     * 
     * @param baoStream ByteArrayOutputStream containing the data of the file
     * @param fileName  Filename of the file
     */
    private void writeFile(ByteArrayOutputStream baoStream, String fileName) {
        try {
            OutputStream outputStream = new FileOutputStream("com/folder/" + fileName);
            baoStream.writeTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
