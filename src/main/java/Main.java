import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

public class Main {
    public static void main(String[] args) {
        // Print initial log message
        System.out.println("Logs from your program will appear here!");

        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
            // Loop to continuously receive and process DNS packets
            while (true) {
                final byte[] buf = new byte[512];
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                
                // Receive the packet from the client
                serverSocket.receive(packet);
                System.out.println("Received data");

                // Wrap the received packet data in a ByteBuffer for easier manipulation
                ByteBuffer requestBuffer = ByteBuffer.wrap(packet.getData());

                // Parse the DNS header from the request
                short id = requestBuffer.getShort(); // Transaction ID
                short flags = requestBuffer.getShort(); // Flags
                short qdcount = requestBuffer.getShort(); // Number of questions
                short ancount = requestBuffer.getShort(); // Number of answers
                short nscount = requestBuffer.getShort(); // Number of authority records
                short arcount = requestBuffer.getShort(); // Number of additional records

                // Extract QR, OPCODE, and RD from flags and set response parameters
                int qr = 1; // This is a response
                int opcode = (flags >> 11) & 0xF; // Extract OPCODE
                int aa = 0; // Not authoritative
                int tc = 0; // Not truncated
                int rd = (flags >> 8) & 0x1; // Extract RD (Recursion Desired)
                int ra = 0; // Recursion not available
                int z = 0; // Reserved
                int rcode = (opcode == 0) ? 0 : 4; // No error for standard query, else not implemented

                // Construct the flags for the response
                short responseFlags = (short) ((qr << 15) | (opcode << 11) | (aa << 10) | (tc << 9) | (rd << 8) | (ra << 7) | (z << 4) | rcode);

                // Allocate a ByteBuffer for the response
                ByteBuffer byteBuffer = ByteBuffer.allocate(512);

                // DNS Header for the response
                byteBuffer.putShort(id); // ID from the request
                byteBuffer.putShort(responseFlags); // Response flags
                byteBuffer.putShort(qdcount); // Number of questions (same as in request)
                byteBuffer.putShort(qdcount); // Number of answers (same as the number of questions)
                byteBuffer.putShort(nscount); // Number of authority records (same as in request)
                byteBuffer.putShort(arcount); // Number of additional records (same as in request)

                // Question Section (copy from request, but uncompress labels)
                requestBuffer.position(12); // Position buffer to the start of the question section
                for (int i = 0; i < qdcount; i++) {
                    // Parse the domain name from the request buffer
                    String domainName = parseDomainName(requestBuffer, requestBuffer.position());
                    // Add the parsed domain name to the response buffer
                    addDomainNameToBuffer(byteBuffer, domainName);
                    // Copy question type and class from the request to the response
                    short qType = requestBuffer.getShort();
                    short qClass = requestBuffer.getShort();
                    byteBuffer.putShort(qType);
                    byteBuffer.putShort(qClass);
                }

                // Answer Section (provide an answer for each question)
                requestBuffer.position(12); // Reset position to the start of the question section
                for (int i = 0; i < qdcount; i++) {
                    // Parse the domain name again for the answer section
                    String domainName = parseDomainName(requestBuffer, requestBuffer.position());
                    // Add the domain name to the response buffer
                    addDomainNameToBuffer(byteBuffer, domainName);
                    // Copy question type and class to the answer section
                    short qType = requestBuffer.getShort();
                    short qClass = requestBuffer.getShort();
                    byteBuffer.putShort(qType);
                    byteBuffer.putShort(qClass);
                    byteBuffer.putInt(60); // TTL: 60 seconds
                    byteBuffer.putShort((short) 4); // RDLENGTH: 4 bytes
                    byteBuffer.put(new byte[]{(byte) 8, (byte) 8, (byte) 8, (byte) 8}); // RDATA: IP address 8.8.8.8
                }

                // Create a response packet and send it back to the client
                final byte[] bufResponse = byteBuffer.array();
                final DatagramPacket packetResponse = new DatagramPacket(bufResponse, byteBuffer.position(), packet.getSocketAddress());
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            // Print any IO exceptions that occur
            System.out.println("IOException: " + e.getMessage());
        }
    }

    // Function to parse a domain name from a ByteBuffer, handling compression
    private static String parseDomainName(ByteBuffer buffer, int position) {
        StringBuilder domainName = new StringBuilder();
        boolean jumped = false; // Flag to check if we have followed a compression pointer
        int jumps = 0; // Counter to prevent infinite loops

        while (true) {
            int length = buffer.get(position) & 0xFF; // Read the length of the label
            if (length == 0) {
                break; // End of the domain name
            }
            if ((length & 0xC0) == 0xC0) { // Check if the length indicates a pointer
                if (!jumped) {
                    buffer.position(position + 2); // Move past the pointer
                }
                position = ((length & 0x3F) << 8) | (buffer.get(position + 1) & 0xFF); // Calculate the pointer position
                if (jumps++ > 5) { // Prevent infinite loops
                    throw new IllegalStateException("Too many jumps in the compressed label");
                }
                jumped = true;
            } else {
                position++;
                byte[] label = new byte[length];
                buffer.get(position, label); // Read the label
                domainName.append(new String(label)).append("."); // Append the label to the domain name
                position += length;
            }
        }
        if (!jumped) {
            buffer.position(position + 1); // Move past the null byte
        }
        return domainName.toString();
    }

    // Function to add a domain name to a ByteBuffer
    private static void addDomainNameToBuffer(ByteBuffer buffer, String domainName) {
        String[] labels = domainName.split("\\."); // Split the domain name into labels
        for (String label : labels) {
            buffer.put((byte) label.length()); // Write the length of the label
            buffer.put(label.getBytes()); // Write the label
        }
        buffer.put((byte) 0x00); // Null byte to terminate the name
    }
}
