package dev.mobicode.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.os.Build;
import android.util.Base64;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class LinuxWorkspace {
    static final String HOME = "/root";
    static final String PROJECT = HOME + "/Projects/mobiCode-starter";
    private static final String SERVER_VERSION = "4.145.2";
    private static final String SERVER = "/opt/code-server/bin/code-server";
    private static final String DATA = HOME + "/.local/share/mobicode";
    private static final String CONFIG = HOME + "/.config/mobicode/config.yaml";
    private static final String ROOTFS_NAME = "ubuntu-noble";
    private static final String READY_FILE = ".mobicode-workspace-v1";
    private static final String PACKAGES = "ca-certificates curl git g++ make python3 python-is-python3 openjdk-21-jdk-headless openssh-client";
    private final Context context;
    private final WorkspaceService service;
    private final Path root;
    private final Path staging;
    private final Path downloads;
    private final Path nativeDir;
    private final String arch;
    private final WorkspaceFiles.Download base;
    private final WorkspaceFiles.Download server;

    LinuxWorkspace(Context context, WorkspaceService service) throws IOException {
        this.context = context;
        this.service = service;
        root = rootPath(context);
        staging = root.resolveSibling(ROOTFS_NAME + ".partial");
        downloads = context.getFilesDir().toPath().resolve("workspace-downloads");
        nativeDir = Paths.get(context.getApplicationInfo().nativeLibraryDir);
        if (!Files.isExecutable(nativeDir.resolve("libproot.so"))) {
            throw new IOException("Workspace tools are unavailable for this device. Install a packaged mobiCode APK.");
        }
        boolean arm = Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a") && !Build.SUPPORTED_ABIS[0].equals("x86_64");
        arch = arm ? "arm64" : "amd64";
        base = new WorkspaceFiles.Download("https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-" + arch + ".tar.gz",
                arm ? "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2" : "e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86",
                arm ? 29936675L : 30028293L);
        server = new WorkspaceFiles.Download("https://github.com/coder/code-server/releases/download/v" + SERVER_VERSION + "/" + archivePrefix() + ".tar.gz",
                arm ? "e867a34416e2da4a83496b09580001893e7a4e5bb58a0624c4fcd22819e7d53d" : "2f6db6c3e8b733dd6b3aeee83532e148b2bae2e392353ba85452d03572348962",
                arm ? 233639045L : 236495502L);
    }

    private static Path rootPath(Context context) {
        return context.getFilesDir().toPath().resolve(ROOTFS_NAME);
    }

    static boolean isInstalled(Context context) {
        return Files.isRegularFile(rootPath(context).resolve(READY_FILE));
    }

    private String archivePrefix() { return "code-server-" + SERVER_VERSION + "-linux-" + arch; }

    void install() throws Exception {
        if (isInstalled(context)) {
            refreshDns();
            return;
        }
        if (!Files.isDirectory(root) && context.getFilesDir().getUsableSpace() < 1900 * WorkspaceFiles.MIB) {
            throw new IOException("Free at least 2 GB of storage, then tap Try again.");
        }
        Files.createDirectories(downloads);
        if (!Files.isDirectory(root)) {
            Path archive = downloads.resolve("ubuntu-base.tar.gz");
            service.download("Downloading Linux workspace", archive, base);
            service.step("Preparing Linux workspace", "");
            WorkspaceFiles.removeTree(staging);
            try (InputStream input = Files.newInputStream(archive)) {
                WorkspaceFiles.unpack(input, staging, "", false);
            }
            Files.move(staging, root, StandardCopyOption.ATOMIC_MOVE);
            Files.deleteIfExists(archive);
        }
        prepareGuest();

        Path editor = root.resolve("opt/code-server");
        if (!Files.isDirectory(editor)) {
            Path archive = downloads.resolve("code-server-" + SERVER_VERSION + ".tar.gz");
            service.download("Downloading code editor", archive, server);
            service.step("Installing code editor", "");
            Path editorStage = root.resolve("opt/.code-server-partial");
            WorkspaceFiles.removeTree(editorStage);
            try (InputStream input = Files.newInputStream(archive)) {
                WorkspaceFiles.unpack(input, editorStage, archivePrefix(), false);
            }
            Files.move(editorStage, editor, StandardCopyOption.ATOMIC_MOVE);
            Files.deleteIfExists(archive);
        }

        if (!Files.isRegularFile(root.resolve(".mobicode-toolchain"))) {
            service.step("Installing C++, Java, Python and Git", "This can take a few minutes.");
            runSetupCommand("export DEBIAN_FRONTEND=noninteractive; "
                    + "dpkg --configure -a || true; "
                    + "apt-get -o Acquire::Retries=2 update && "
                    + "apt-get -y --no-install-recommends --fix-broken install " + PACKAGES + " && apt-get clean && "
                    + "g++ --version && python --version && git --version && javac -version", 25 * 60);
            Files.createFile(root.resolve(".mobicode-toolchain"));
        }
        service.step("Preparing your first project", "");
        try (InputStream input = context.getResources().openRawResource(R.raw.workspace_starter)) {
            WorkspaceFiles.unpack(input, root.resolve("root"), "", true);
        }
        runSetupCommand(SERVER + " --config " + CONFIG + " --user-data-dir " + DATA + "/editor "
                + "--extensions-dir " + DATA + "/extensions --install-extension " + DATA + "/mobiCode-agent.vsix --force", 180);
        Files.createFile(root.resolve(READY_FILE));
    }

    private void prepareGuest() throws Exception {
        refreshDns();
        configurePackageDownloads();
        write(root.resolve("etc/apt/apt.conf.d/99mobicode"), "APT::Sandbox::User \"root\";\n"
                + "Acquire::http::Pipeline-Depth \"0\";\nAcquire::PDiffs \"false\";\n"
                + "Acquire::https::CaInfo \"/etc/mobicode/package-roots.pem\";\n"
                + "Acquire::http::Timeout \"30\";\nAcquire::https::Timeout \"30\";\n"
                + "DPkg::Options { \"--force-confold\"; };\n");
        write(root.resolve("usr/sbin/policy-rc.d"), "#!/bin/sh\nexit 101\n");
        android.system.Os.chmod(root.resolve("usr/sbin/policy-rc.d").toString(), 0755);
        write(root.resolve("root/.config/mobicode/config.yaml"), "bind-addr: 127.0.0.1:8765\nauth: password\ncert: false\n");
        Files.createDirectories(root.resolve("usr/local/bin"));
        Path node = root.resolve("usr/local/bin/node");
        if (!Files.exists(node, LinkOption.NOFOLLOW_LINKS)) android.system.Os.symlink("/opt/code-server/lib/node", node.toString());
        Files.createDirectories(root.resolve("tmp/mc"));
        Files.createDirectories(root.resolve("run"));
        Files.createDirectories(root.resolve("dev/pts"));
        Files.createDirectories(root.resolve("dev/shm"));
        Files.createDirectories(root.resolve("dev/shm/.mobicode"));
        Files.createDirectories(root.resolve("sys"));
        Files.createDirectories(root.resolve("proc"));
        Files.createDirectories(root.resolve(".links"));
        if (!Files.exists(root.resolve("dev/ptmx"), LinkOption.NOFOLLOW_LINKS)) {
            android.system.Os.symlink("pts/ptmx", root.resolve("dev/ptmx").toString());
        }
    }

    private void configurePackageDownloads() throws Exception {
        // Ubuntu Base may not have its certificate package installed yet.
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        StringBuilder certificates = new StringBuilder();
        for (TrustManager manager : factory.getTrustManagers()) {
            if (!(manager instanceof X509TrustManager)) continue;
            for (X509Certificate certificate : ((X509TrustManager) manager).getAcceptedIssuers()) {
                certificates.append("-----BEGIN CERTIFICATE-----\n")
                        .append(Base64.encodeToString(certificate.getEncoded(), Base64.DEFAULT))
                        .append("-----END CERTIFICATE-----\n");
            }
        }
        if (certificates.length() == 0) throw new IOException("Android has no trust roots for the tool downloads.");
        write(root.resolve("etc/mobicode/package-roots.pem"), certificates.toString());
        Path sources = root.resolve("etc/apt/sources.list.d/ubuntu.sources");
        String entries = new String(Files.readAllBytes(sources), StandardCharsets.UTF_8);
        write(sources, entries.replace("URIs: http://", "URIs: https://"));
    }

    private void refreshDns() throws Exception {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        LinkProperties network = manager == null ? null : manager.getLinkProperties(manager.getActiveNetwork());
        if (network == null && isInstalled(context)) return;
        StringBuilder servers = new StringBuilder();
        if (network != null) {
            for (InetAddress address : network.getDnsServers()) {
                String host = address.getHostAddress();
                if (host != null && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) servers.append("nameserver ").append(host).append('\n');
            }
        }
        if (servers.length() == 0) servers.append("nameserver 1.1.1.1\nnameserver 8.8.8.8\n");
        Path resolv = root.resolve("etc/resolv.conf");
        Files.deleteIfExists(resolv);
        write(resolv, servers.toString());
    }

    private static void write(Path path, String value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    Process startEditor(int port, String password) throws IOException {
        List<String> args = Arrays.asList(SERVER, "--config", CONFIG, "--bind-addr", "127.0.0.1:" + port,
                "--user-data-dir", DATA + "/editor", "--extensions-dir", DATA + "/extensions",
                "--disable-telemetry", "--disable-update-check", PROJECT);
        return guest(args, password).start();
    }

    private ProcessBuilder guest(List<String> arguments, String password) {
        Path links = root.resolve(".links");
        List<String> command = new ArrayList<>(Arrays.asList(nativeDir.resolve("libproot.so").toString(), "--kill-on-exit",
                "-0", "-l", "-r", root.toString(), "-w", HOME,
                "-b", links + ":" + links,
                "-b", "/dev/null", "-b", "/dev/zero", "-b", "/dev/random", "-b", "/dev/urandom",
                "-b", "/dev/pts", "-b", "/dev/ptmx", "-b", "/dev/tty", "-b", "/proc", "-b", "/sys",
                "/usr/bin/env", "-i", "HOME=" + HOME, "USER=root", "LOGNAME=root",
                "PATH=/usr/local/bin:/usr/local/sbin:/usr/bin:/usr/sbin:/bin:/sbin",
                "SHELL=/bin/bash", "LANG=C.UTF-8", "TERM=xterm-256color", "TMPDIR=/tmp/mc", "XDG_RUNTIME_DIR=/tmp/mc"));
        if (password != null) command.add("PASSWORD=" + password);
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.put("LD_LIBRARY_PATH", nativeDir.toString());
        environment.put("PROOT_TMP_DIR", context.getCacheDir().getAbsolutePath());
        environment.put("PROOT_L2S_DIR", links.toString());
        environment.put("PROOT_LOADER", nativeDir.resolve("libproot_loader.so").toString());
        environment.put("PROOT_LOADER_32", nativeDir.resolve("libproot_loader32.so").toString());
        return builder;
    }

    private void runSetupCommand(String command, long maxSeconds) throws Exception {
        Process process = guest(Arrays.asList("/bin/bash", "-c", command), null).start();
        String[] last = {""};
        Thread output = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (line.trim().isEmpty()) continue;
                    last[0] = line.substring(0, Math.min(line.length(), 240));
                    service.detail(last[0]);
                }
            } catch (IOException ignored) { }
        }, "mobiCode setup output");
        output.setDaemon(true);
        output.start();
        try {
            if (!process.waitFor(maxSeconds, TimeUnit.SECONDS)) throw new IOException("Installing tools took too long. Tap Try again to continue.");
            output.join(1000);
            if (process.exitValue() != 0) throw new IOException("A workspace tool could not install. " + last[0]);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
