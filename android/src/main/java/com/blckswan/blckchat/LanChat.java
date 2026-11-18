package com.blckswan.blckchat;

import android.util.Base64;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Internal Wi-Fi messenger transport. This is NOT the production GitHub
 * transport and is NOT end-to-end encryption: the administrator's host
 * decrypts and forwards messages. Never advertise it as Signal-equivalent.
 *
 * Each invitation has an independently generated random 256-bit key and
 * expires after ten minutes. The one-time encrypted join grants a fresh
 * random transport session key. Android platform cryptography is used,
 * not a custom cipher. LAN clients do not receive admin GitHub credentials.
 */
public final class LanChat {
    public interface Events {
        void onStatus(String text);
        void onMessage(String name, String text);
    }

    public static final int PORT = 42424;
    private static final int MAX_LINE = 9000;
    private static final int MAX_MESSAGE_BYTES = 2048;
    private static final long INVITE_LIFETIME_MS = 10L * 60L * 1000L;
    private static final byte[] AAD = "BLCKCHAT42-LAN-V1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final class Invite {
        final String id;
        final byte[] key;
        final long expires;
        boolean used;
        Invite(String id, byte[] key) {
            this.id = id;
            this.key = key;
            this.expires = System.currentTimeMillis() + INVITE_LIFETIME_MS;
        }
    }

    public static final class Link {
        public final String host;
        public final String id;
        public final String key;
        Link(String host, Invite invite) {
            this.host=host;
            this.id=invite.id;
            this.key=encode(invite.key);
        }
        public String asUri() {
            return "blckchat://join?host=" + host + "&id=" + id + "&key=" + key;
        }
    }

    private static final class Wire {
        final BufferedReader input;
        final BufferedWriter output;
        Wire(Socket socket) throws IOException {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }
        synchronized void send(String line) throws IOException {
            output.write(line);
            output.write('\n');
            output.flush();
        }
        String receive() throws IOException {
            StringBuilder result = new StringBuilder(128);
            for (;;) {
                int c = input.read();
                if (c == -1) return null;
                if (c == '\n') return result.toString();
                if (c == '\r') continue;
                if (result.length() >= MAX_LINE) throw new IOException("Frame too long");
                if (c < 32) throw new IOException("Invalid frame");
                result.append((char)c);
            }
        }
    }

    private static final class Peer {
        final Socket socket;
        final Wire wire;
        final String name;
        final byte[] sessionKey;
        Peer(Socket socket, Wire wire, String name, byte[] sessionKey) {
            this.socket = socket;
            this.wire = wire;
            this.name = name;
            this.sessionKey = sessionKey;
        }
        void deliver(String name, String text) throws Exception {
            wire.send("E " + seal(sessionKey, "M|" + name + "|" + text));
        }
        void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private final Events events;
    private final Map<String, Invite> invitations = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Peer> peers = new CopyOnWriteArrayList<>();
    private final List<String[]> history = new ArrayList<>();
    private volatile ServerSocket server;
    private volatile Socket localSocket;
    private volatile Wire localWire;
    private volatile byte[] localKey;
    private volatile boolean running;
    private volatile String localName;

    public LanChat(Events events) { this.events = events; }

    public static boolean safeName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    private static String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
    private static byte[] decode(String text) {
        return Base64.decode(text, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
    private static String seal(byte[] key, String message) throws Exception {
        return sealBytes(key, message.getBytes(StandardCharsets.UTF_8));
    }
    private static String sealBytes(byte[] key, byte[] message) throws Exception {
        if (key.length != 32) throw new IOException("Invalid key length");
        byte[] nonce = randomBytes(12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        byte[] encrypted = cipher.doFinal(message);
        byte[] result = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, result, 0, nonce.length);
        System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
        return encode(result);
    }
    private static byte[] openBytes(byte[] key, String wire) throws Exception {
        if (key.length != 32 || wire.length() > MAX_LINE) throw new IOException("Invalid ciphertext");
        byte[] all = decode(wire);
        if (all.length < 12 + 16 || all.length > MAX_MESSAGE_BYTES + 256) {
            throw new IOException("Ciphertext size is invalid");
        }
        byte[] nonce = new byte[12];
        System.arraycopy(all,0,nonce,0,12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128,nonce));
        cipher.updateAAD(AAD);
        return cipher.doFinal(all,12,all.length-12);
    }
    private static String open(byte[] key, String wire) throws Exception {
        return new String(openBytes(key,wire), StandardCharsets.UTF_8);
    }

    public synchronized void host(String name) throws IOException {
        if (!safeName(name)) throw new IOException("Use letters, numbers, _ or - in your display name");
        if (running) throw new IOException("A room is already active");
        server = new ServerSocket(PORT,12);
        server.setReuseAddress(true);
        running = true;
        Thread acceptor = new Thread(() -> {
            while (running) {
                try {
                    Socket incoming = server.accept();
                    incoming.setSoTimeout(15_000);
                    Thread worker = new Thread(() -> serve(incoming),"blck-peer");
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException ex) {
                    if (running) events.onStatus("Server: " + ex.getMessage());
                }
            }
        },"blck-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        Link mine = newInvite("127.0.0.1");
        connect(mine,name);
    }

    public Link newInvite(String host) {
        if (!running || server == null) throw new IllegalStateException("Admin room not open");
        if (invitations.size() >= 32) throw new IllegalStateException("Too many pending invitations");
        Invite invite = new Invite(encode(randomBytes(16)), randomBytes(32));
        invitations.put(invite.id,invite);
        return new Link(host,invite);
    }

    private void serve(Socket socket) {
        Peer peer = null;
        try {
            Wire wire = new Wire(socket);
            String greeting = wire.receive();
            if (greeting == null || !greeting.startsWith("J ")) throw new IOException("Missing join");
            int split=greeting.indexOf(' ',2);
            if (split<3) throw new IOException("Invalid join");
            String id=greeting.substring(2,split);
            Invite invite=invitations.get(id);
            if (invite == null) throw new IOException("Unknown invitation");
            byte[] transportKey;
            String name;
            synchronized (invite) {
                if (invite.used || System.currentTimeMillis() >= invite.expires)
                    throw new IOException("Invitation expired or already used");
                String hello=open(invite.key,greeting.substring(split+1));
                if (!hello.startsWith("J|")) throw new IOException("Invalid join payload");
                name=hello.substring(2);
                if (!safeName(name)) throw new IOException("Invalid display name");
                transportKey=randomBytes(32);
                wire.send("W " + sealBytes(invite.key,transportKey));
                invite.used=true;
                invitations.remove(id);
            }
            peer = new Peer(socket,wire,name,transportKey);
            peers.add(peer);
            socket.setSoTimeout(0);
            synchronized (history) {
                for (String[] item:history) peer.deliver(item[0],item[1]);
            }
            broadcast("System", name + " joined the room");
            String frame;
            while ((frame=wire.receive())!=null) {
                if (!frame.startsWith("E ")) throw new IOException("Invalid message frame");
                String decrypted=open(transportKey,frame.substring(2));
                if (!decrypted.startsWith("M|")) throw new IOException("Invalid message type");
                String text=decrypted.substring(2);
                int length=text.getBytes(StandardCharsets.UTF_8).length;
                if (length==0 || length>MAX_MESSAGE_BYTES || text.indexOf('\u0000')>=0)
                    throw new IOException("Invalid message size");
                broadcast(name,text);
            }
        } catch (Exception ignored) {
            // Privacy: never log invitation keys, messages, or phone numbers.
        } finally {
            if (peer != null) {
                peers.remove(peer);
                peer.close();
            } else {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void broadcast(String author,String text) {
        synchronized (history) {
            history.add(new String[]{author,text});
            if (history.size()>120) history.remove(0);
        }
        for (Peer p:peers) {
            try {p.deliver(author,text);}
            catch (Exception e) {peers.remove(p);p.close();}
        }
    }

    public void connect(Link link,String name) {
        if (!safeName(name)) {
            events.onStatus("Invalid name");
            return;
        }
        new Thread(() -> {
            try {
                byte[] inviteKey=decode(link.key);
                if (inviteKey.length!=32 || link.id.length()>64) throw new IOException("Bad invite");
                Socket socket=new Socket();
                socket.connect(new InetSocketAddress(link.host,PORT),8000);
                socket.setSoTimeout(15000);
                Wire wire=new Wire(socket);
                wire.send("J " + link.id + " " + seal(inviteKey,"J|" + name));
                String welcome=wire.receive();
                if (welcome==null || !welcome.startsWith("W "))
                    throw new IOException("Join refused or invitation expired");
                byte[] sessionKey=openBytes(inviteKey,welcome.substring(2));
                if (sessionKey.length!=32) throw new IOException("Bad session");
                socket.setSoTimeout(0);
                localSocket=socket;
                localWire=wire;
                localKey=sessionKey;
                localName=name;
                events.onStatus("Connected to " + link.host + " · " + name);
                String frame;
                while ((frame=wire.receive())!=null) {
                    if (!frame.startsWith("E ")) throw new IOException("Bad frame");
                    String message=open(sessionKey,frame.substring(2));
                    if (!message.startsWith("M|")) throw new IOException("Unknown message");
                    int sep=message.indexOf('|',2);
                    if (sep<3) throw new IOException("Malformed message");
                    events.onMessage(message.substring(2,sep),message.substring(sep+1));
                }
                events.onStatus("Disconnected");
            } catch (Exception e) {
                events.onStatus("Connection failed: " + e.getMessage());
            } finally {
                localSocket=null;localWire=null;localKey=null;
            }
        },"blck-client").start();
    }

    public boolean send(String text) {
        Wire w=localWire;
        byte[] key=localKey;
        if (w==null || key==null || text==null || text.isEmpty() ||
            text.getBytes(StandardCharsets.UTF_8).length>MAX_MESSAGE_BYTES) return false;
        new Thread(() -> {
            try {w.send("E " + seal(key,"M|" + text));}
            catch (Exception e) {events.onStatus("Send error: "+e.getMessage());}
        },"blck-send").start();
        return true;
    }

    public boolean isHosting() { return running; }
    public static String localIp() {
        try {
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network=interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                Enumeration<InetAddress> addresses=network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address=addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress())
                        return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
    public synchronized void shutdown() {
        running=false;
        try {if (server!=null) server.close();} catch (IOException ignored) {}
        try {if (localSocket!=null) localSocket.close();} catch (IOException ignored) {}
        for(Peer p:peers) p.close();
        peers.clear();
        invitations.clear();
    }
}
