// RtmpHandler.java - Final, Complete, and Corrected Version
package com.example.rtmpserver;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class RtmpHandler implements Runnable {

    private final Socket socket;
    private final ApiClient apiClient;

    // RTMP Message Type IDs
    // RTMP 协议消息类型常量
    private static final int MSG_TYPE_SET_CHUNK_SIZE = 1;
    private static final int MSG_TYPE_WINDOW_ACK_SIZE = 5;
    private static final int MSG_TYPE_SET_PEER_BANDWIDTH = 6;
    @SuppressWarnings("unused") // 保留用于将来的音频处理
    private static final int MSG_TYPE_AUDIO = 8;
    @SuppressWarnings("unused") // 保留用于将来的视频处理
    private static final int MSG_TYPE_VIDEO = 9;
    private static final int MSG_TYPE_COMMAND_AMF0 = 20;

    private final Map<Integer, RtmpHeader> lastHeaders = new HashMap<>();

    // --- THIS WAS THE MISSING VARIABLE ---
    private int clientChunkSize = 128; // RTMP default chunk size

    // 流相关信息
    private String currentStreamKey = null;
    private String clientIp = null;

    public RtmpHandler(Socket socket, ApiClient apiClient) {
        this.socket = socket;
        this.apiClient = apiClient;
        this.clientIp = socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            System.out.println("Handler started for " + socket.getRemoteSocketAddress());
            doHandshake(in, out);
            messageLoop(in, out);
        } catch (IOException e) {
            // This is expected when the client disconnects.
        } finally {
            System.out.println("Client disconnected: " + socket.getRemoteSocketAddress());

            // 如果有活跃的流，通知API服务器流结束
            if (currentStreamKey != null && apiClient != null) {
                System.out.println("🛑 Notifying stream stop for: " + currentStreamKey);
                boolean success = apiClient.notifyStreamStop(currentStreamKey);
                if (success) {
                    System.out.println("✅ Successfully notified API server of stream stop");
                } else {
                    System.out.println("⚠️ Failed to notify API server of stream stop");
                }
            }

            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // Ignore closing errors
            }
        }
    }

    private void doHandshake(DataInputStream in, DataOutputStream out) throws IOException {
        System.out.println("--> [HANDSHAKE] Performing for " + socket.getRemoteSocketAddress());
        byte c0 = in.readByte();
        if (c0 != 0x03) throw new IOException("Unsupported RTMP version: " + c0);
        System.out.println("--> [HANDSHAKE] Received C0. Version: " + c0);
        byte[] c1 = new byte[1536];
        in.readFully(c1);
        System.out.println("--> [HANDSHAKE] Received C1 (1536 bytes).");
        
        System.out.println("<-- [HANDSHAKE] Sending S0+S1+S2...");
        out.writeByte(0x03);
        out.write(c1);
        out.write(c1);
        out.flush();
        System.out.println("<-- [HANDSHAKE] S0+S1+S2 flushed to client.");

        byte[] c2 = new byte[1536];
        in.readFully(c2);
        System.out.println("--> [HANDSHAKE] Received C2 (1536 bytes).");
        
        System.out.println("✅ [HANDSHAKE] Handshake successful for " + socket.getRemoteSocketAddress());
    }
    
    // RtmpHandler.java - The Final, Working Version
    private void messageLoop(DataInputStream in, DataOutputStream out) throws IOException {
        System.out.println("Entering message loop for " + socket.getRemoteSocketAddress());

        Map<Integer, byte[]> incompletePayloads = new HashMap<>();
        Map<Integer, Integer> bytesReadForPayload = new HashMap<>();

        while (!Thread.interrupted() && !socket.isClosed()) {
            try {
                byte basicHeaderByte = in.readByte();
                int fmt = (basicHeaderByte & 0xFF) >> 6;
                int csid = basicHeaderByte & 0x3F;

                RtmpHeader lastHeader = lastHeaders.get(csid);
                RtmpHeader currentHeader = new RtmpHeader();

                switch (fmt) {
                    case 0:
                        currentHeader.timestamp = read3Bytes(in);
                        currentHeader.messageLength = read3Bytes(in);
                        currentHeader.messageTypeId = in.readByte() & 0xFF;
                        currentHeader.messageStreamId = Integer.reverseBytes(in.readInt());
                        break;
                    case 1:
                        if (lastHeader == null) throw new IOException("Chunk fmt=1 received without previous header.");
                        currentHeader.timestamp = read3Bytes(in);
                        currentHeader.messageLength = read3Bytes(in);
                        currentHeader.messageTypeId = in.readByte() & 0xFF;
                        currentHeader.messageStreamId = lastHeader.messageStreamId;
                        break;
                    case 2:
                        if (lastHeader == null) throw new IOException("Chunk fmt=2 received without previous header.");
                        currentHeader.timestamp = read3Bytes(in);
                        currentHeader.messageLength = lastHeader.messageLength;
                        currentHeader.messageTypeId = lastHeader.messageTypeId;
                        currentHeader.messageStreamId = lastHeader.messageStreamId;
                        break;
                    case 3:
                        if (lastHeader == null) throw new IOException("Chunk fmt=3 received without previous header.");
                        currentHeader = lastHeader;
                        break;
                    default:
                        throw new IOException("Invalid chunk format: " + fmt);
                }
                
                // --- START OF THE FIX ---
                // Create an effectively final variable for the lambda expression.
                final int finalMessageLength = currentHeader.messageLength;
                byte[] payload = incompletePayloads.computeIfAbsent(csid, k -> new byte[finalMessageLength]);
                // --- END OF THE FIX ---

                int bytesAlreadyRead = bytesReadForPayload.getOrDefault(csid, 0);
                int bytesToRead = Math.min(clientChunkSize, finalMessageLength - bytesAlreadyRead);
                in.readFully(payload, bytesAlreadyRead, bytesToRead);
                bytesAlreadyRead += bytesToRead;
                bytesReadForPayload.put(csid, bytesAlreadyRead);
                
                if (bytesAlreadyRead >= finalMessageLength) {
                    System.out.printf("Received full message: TypeId=%d, Length=%d, CSID=%d\n",
                            currentHeader.messageTypeId, currentHeader.messageLength, csid);
                    
                    switch (currentHeader.messageTypeId) {
                        case MSG_TYPE_SET_CHUNK_SIZE:
                            handleSetChunkSize(payload);
                            break;
                        case MSG_TYPE_COMMAND_AMF0:
                            handleCommand(payload, out);
                            break;
                        default:
                            break;
                    }
                    
                    incompletePayloads.remove(csid);
                    bytesReadForPayload.remove(csid);
                }
                
                lastHeaders.put(csid, currentHeader);

            } catch (IOException e) {
                System.err.println("Error in message loop, connection will be closed. Message: " + e.getMessage());
                break;
            }
        }
    }
    
    // --- THIS WAS THE MISSING METHOD ---
    private void handleSetChunkSize(byte[] payload) {
        DataInputStream payloadStream = new DataInputStream(new ByteArrayInputStream(payload));
        try {
            this.clientChunkSize = payloadStream.readInt();
            System.out.println("✅ Client chunk size updated to: " + this.clientChunkSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int read3Bytes(DataInputStream in) throws IOException {
        return (in.readByte() & 0xFF) << 16 | (in.readByte() & 0xFF) << 8 | (in.readByte() & 0xFF);
    }
    
    private static class RtmpHeader {
        @SuppressWarnings("unused") // 保留用于将来的时间戳处理
        int timestamp;
        int messageLength;
        int messageTypeId;
        int messageStreamId;
    }
    
    private void handleCommand(byte[] payload, DataOutputStream out) throws IOException {
        DataInputStream payloadStream = new DataInputStream(new ByteArrayInputStream(payload));
        String commandName = readAmfString(payloadStream);
        double transactionId = readAmfNumber(payloadStream);
        System.out.printf("Received command: %s, transactionId: %.1f\n", commandName, transactionId);

        switch (commandName) {
            case "connect":
                handleConnect(out, transactionId);
                break;
            case "createStream":
                handleCreateStream(out, transactionId);
                break;
            case "publish":
                handlePublish(payload, out, transactionId);
                break;
        }
    }
    
    private void handleConnect(DataOutputStream out, double transactionId) throws IOException {
        System.out.println("Handling 'connect' command.");
        System.out.println("<-- Sending Window Acknowledgement Size...");
        sendWindowAckSize(out, 5000000);
        System.out.println("<-- Sending Set Peer Bandwidth...");
        sendSetPeerBandwidth(out, 5000000, 2);
        System.out.println("<-- Sending Set Chunk Size (Server)...");
        sendSetChunkSize(out, 4096);
        System.out.println("<-- Sending '_result' for 'connect'...");
        sendConnectResult(out, transactionId);
        System.out.println("✅ 'connect' sequence fully sent.");
    }
    
    private void handleCreateStream(DataOutputStream out, double transactionId) throws IOException {
        System.out.println("Handling 'createStream' command.");
        sendCreateStreamResult(out, transactionId);
        System.out.println("✅ 'createStream' sequence fully sent.");
    }

    private void handlePublish(byte[] payload, DataOutputStream out, double transactionId) throws IOException {
        System.out.println("Handling 'publish' command.");

        try {
            // 解析publish命令以提取流密钥
            DataInputStream payloadStream = new DataInputStream(new ByteArrayInputStream(payload));

            // 跳过命令名称和事务ID（已经读取过）
            readAmfString(payloadStream); // command name
            readAmfNumber(payloadStream); // transaction id

            // 读取null参数
            int nullType = payloadStream.readByte();
            if (nullType != 0x05) {
                System.out.println("⚠️ Expected null parameter, got type: " + nullType);
            }

            // 读取流名称（流密钥）
            String streamName = readAmfString(payloadStream);
            this.currentStreamKey = streamName;

            System.out.println("🎬 Stream publish started:");
            System.out.println("   Stream Key: " + streamName);
            System.out.println("   Client IP: " + clientIp);

            // 通知Web API服务器流开始
            if (apiClient != null) {
                boolean success = apiClient.notifyStreamStart(streamName, clientIp);
                if (success) {
                    System.out.println("✅ Successfully notified API server of stream start");
                } else {
                    System.out.println("⚠️ Failed to notify API server of stream start");
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error parsing publish command: " + e.getMessage());
            e.printStackTrace();
        }

        sendPublishStatus(out, transactionId);
        System.out.println("✅ 'publish' sequence fully sent.");
    }
    
    private void sendWindowAckSize(DataOutputStream out, int size) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        DataOutputStream bodyOut = new DataOutputStream(baos);
        bodyOut.writeInt(size);
        sendRtmpMessage(out, 2, MSG_TYPE_WINDOW_ACK_SIZE, baos.toByteArray());
    }

    private void sendSetPeerBandwidth(DataOutputStream out, int size, int limitType) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        DataOutputStream bodyOut = new DataOutputStream(baos);
        bodyOut.writeInt(size);
        bodyOut.writeByte(limitType);
        sendRtmpMessage(out, 2, MSG_TYPE_SET_PEER_BANDWIDTH, baos.toByteArray());
    }
    
    private void sendSetChunkSize(DataOutputStream out, int size) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        DataOutputStream bodyOut = new DataOutputStream(baos);
        bodyOut.writeInt(size);
        sendRtmpMessage(out, 2, MSG_TYPE_SET_CHUNK_SIZE, baos.toByteArray());
    }
    
    private void sendConnectResult(DataOutputStream out, double transactionId) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        DataOutputStream bodyOut = new DataOutputStream(baos);
        Amf0Utils.writeString(bodyOut, "_result");
        Amf0Utils.writeNumber(bodyOut, transactionId);
        Amf0Utils.writeObjectBegin(bodyOut);
        Amf0Utils.writeObjectProperty(bodyOut, "fmsVer", "FMS/3,0,1,123");
        Amf0Utils.writeObjectProperty(bodyOut, "capabilities", 31.0);
        Amf0Utils.writeObjectEnd(bodyOut);
        Amf0Utils.writeObjectBegin(bodyOut);
        Amf0Utils.writeObjectProperty(bodyOut, "level", "status");
        Amf0Utils.writeObjectProperty(bodyOut, "code", "NetConnection.Connect.Success");
        Amf0Utils.writeObjectProperty(bodyOut, "description", "Connection succeeded.");
        Amf0Utils.writeObjectEnd(bodyOut);
        byte[] body = baos.toByteArray();
        sendRtmpMessage(out, 3, MSG_TYPE_COMMAND_AMF0, body);
    }
    
    private void sendCreateStreamResult(DataOutputStream out, double transactionId) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        DataOutputStream bodyOut = new DataOutputStream(baos);
        Amf0Utils.writeString(bodyOut, "_result");
        Amf0Utils.writeNumber(bodyOut, transactionId);
        Amf0Utils.writeNull(bodyOut);
        Amf0Utils.writeNumber(bodyOut, 1.0);
        byte[] body = baos.toByteArray();
        sendRtmpMessage(out, 3, MSG_TYPE_COMMAND_AMF0, body);
    }

    private void sendPublishStatus(DataOutputStream out, double transactionId) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        DataOutputStream bodyOut = new DataOutputStream(baos);
        Amf0Utils.writeString(bodyOut, "onStatus");
        Amf0Utils.writeNumber(bodyOut, 0.0);
        Amf0Utils.writeNull(bodyOut);
        Amf0Utils.writeObjectBegin(bodyOut);
        Amf0Utils.writeObjectProperty(bodyOut, "level", "status");
        Amf0Utils.writeObjectProperty(bodyOut, "code", "NetStream.Publish.Start");
        Amf0Utils.writeObjectProperty(bodyOut, "description", "Start publishing.");
        Amf0Utils.writeObjectEnd(bodyOut);
        byte[] body = baos.toByteArray();
        sendRtmpMessage(out, 5, MSG_TYPE_COMMAND_AMF0, body);
    }

    private void sendRtmpMessage(DataOutputStream out, int chunkStreamId, int messageTypeId, byte[] payload) throws IOException {
        out.writeByte(0x00 | (chunkStreamId & 0x3F));
        out.writeByte(0); out.writeByte(0); out.writeByte(0); // Timestamp
        out.writeByte((payload.length >> 16) & 0xFF);
        out.writeByte((payload.length >> 8) & 0xFF);
        out.writeByte(payload.length & 0xFF);
        out.writeByte(messageTypeId);
        out.writeInt(0);
        out.write(payload);
        out.flush();
        System.out.printf("Sent RTMP Message: TypeId=%d, Length=%d, CSID=%d\n", messageTypeId, payload.length, chunkStreamId);
    }

    private String readAmfString(DataInputStream in) throws IOException {
        int type = in.readByte();
        if (type != 0x02) throw new IOException("Not an AMF0 String: " + type);
        short len = in.readShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes);
    }
    
    private double readAmfNumber(DataInputStream in) throws IOException {
        int type = in.readByte();
        if (type != 0x00) throw new IOException("Not an AMF0 Number: " + type);
        return in.readDouble();
    }
}