import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

public class Main {
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");

    // Uncomment this block to pass the first stage
    //
    try(DatagramSocket serverSocket = new DatagramSocket(2053)) {
      while(true) {
        final byte[] buf = new byte[512];
        final DatagramPacket packet = new DatagramPacket(buf, buf.length);
        serverSocket.receive(packet);
        System.out.println("Received data");

        ByteBuffer requestBuffer = ByteBuffer.wrap(packet.getData());

        // Parse the header from the request
        short id = requestBuffer.getShort(); // ID
        short flags = requestBuffer.getShort(); // Flags
        short qdcount = requestBuffer.getShort(); // Number of questions
        short ancount = requestBuffer.getShort(); // Number of answers
        short nscount = requestBuffer.getShort(); // Number of authority records
        short arcount = requestBuffer.getShort(); // Number of additional records

        // Extract QR, OPCODE, and RD from flags
        int qr = 1; // Response
        int opcode = (flags >> 11) & 0xF; // OPCODE
        int aa = 0; // Not authoritative
        int tc = 0; // Not truncated
        int rd = (flags >> 8) & 0x1; // Recursion Desired
        int ra = 0; // Recursion not available
        int z = 0; // Reserved
        int rcode = (opcode == 0) ? 0 : 4; // No error if standard query, else not implemented

        short responseFlags = (short) ((qr << 15) | (opcode << 11) | (aa << 10) | (tc << 9) | (rd << 8) | (ra << 7) | (z << 4) | rcode);

        ByteBuffer byteBuffer = ByteBuffer.allocate(512);

        // DNS Header
        byteBuffer.putShort(id); // ID from request
        byteBuffer.putShort(responseFlags); // Flags
        byteBuffer.putShort(qdcount); // QDCOUNT 
        byteBuffer.putShort((short) 1); // ANCOUNT (1 answer)
        byteBuffer.putShort(nscount); // NSCOUNT 
        byteBuffer.putShort(arcount); // ARCOUNT 


        // Question Section (copy from request)
        requestBuffer.position(12);
        for (int i = 0; i < qdcount; i++) {
            while (requestBuffer.get() != 0) {
                requestBuffer.position(requestBuffer.position() - 1);
                int len = requestBuffer.get() & 0xFF;
                byteBuffer.put((byte) len);
                byte[] label = new byte[len];
                requestBuffer.get(label);
                byteBuffer.put(label);
            }
            byteBuffer.put((byte) 0); // Null byte to terminate the name

            // Type and Class
            byteBuffer.putShort(requestBuffer.getShort()); // Type
            byteBuffer.putShort(requestBuffer.getShort()); // Class
        }

        // Answer Section
        // Name: codecrafters.io
        byteBuffer.put((byte) 0x0c); // Length of "codecrafters"
        byteBuffer.put("codecrafters".getBytes());
        byteBuffer.put((byte) 0x02); // Length of "io"
        byteBuffer.put("io".getBytes());
        byteBuffer.put((byte) 0x00); // Null byte to terminate the name

        // Type, Class, TTL, Length, and Data for Answer
        byteBuffer.putShort((short) 1); // Type: A (Host Address)
        byteBuffer.putShort((short) 1); // Class: IN (Internet)
        byteBuffer.putInt(60); // TTL: 60 seconds
        byteBuffer.putShort((short) 4); // RDLENGTH: 4 bytes
        byteBuffer.put(new byte[] {(byte) 8, (byte) 8, (byte) 8, (byte) 8}); // RDATA: IP address 8.8.8.8

        
        final byte[] bufResponse = byteBuffer.array();
        final DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
        serverSocket.send(packetResponse);
      }
    } catch (IOException e) {
        System.out.println("IOException: " + e.getMessage());
    }
  }
}
