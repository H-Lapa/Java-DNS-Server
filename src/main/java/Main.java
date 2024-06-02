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

        ByteBuffer byteBuffer = ByteBuffer.allocate(512);
        
        // DNS Header
        byteBuffer.putShort((short)1234); // ID
        byteBuffer.putShort((short)0x8000); // Flags
        byteBuffer.putShort((short)1); // QDCount - 1 question
        byteBuffer.putShort((short)0); // answers
        byteBuffer.putShort((short)0); // authority records
        byteBuffer.putShort((short)0); // additional records


        // Question Section
        // Name: codecrafters.io
        byteBuffer.put((byte) 0x0c); // Length of "codecrafters"
        byteBuffer.put("codecrafters".getBytes());
        byteBuffer.put((byte) 0x02); // Length of "io"
        byteBuffer.put("io".getBytes());
        byteBuffer.put((byte) 0x00); // Null byte to terminate the name

        // Type and Class
        byteBuffer.putShort((short) 1); // Type: A (Host Address)
        byteBuffer.putShort((short) 1); // Class: IN (Internet)
        
        final byte[] bufResponse = byteBuffer.array();
        final DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
        serverSocket.send(packetResponse);
      }
    } catch (IOException e) {
        System.out.println("IOException: " + e.getMessage());
    }
  }
}
