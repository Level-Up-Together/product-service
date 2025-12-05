package io.pinkspider.global.component;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import java.io.File;
import java.net.ServerSocket;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SshTunnel {

    private final String sshHost;
    private final int sshPort;
    private final String sshUsername;
    private final String privateKeyPath;
    private final int configuredLocalPort;
    private final String remoteHost;
    private final int remotePort;
    private final String passphrase;
    private com.jcraft.jsch.Session session;

    @Getter
    private int actualLocalPort;

    public SshTunnel(String sshHost, int sshPort, String sshUsername,
                     String privateKeyPath, int localPort, String remoteHost,
                     int remotePort, String passphrase) {
        this.sshHost = sshHost;
        this.sshPort = sshPort;
        this.sshUsername = sshUsername;
        this.privateKeyPath = privateKeyPath;
        this.configuredLocalPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.passphrase = passphrase;
    }

    public void start() {
        try {
            log.info("JSch SSH 터널 연결 시작...");

            JSch jsch = new JSch();

            // 개인키 파일 경로 처리
            String keyPath = privateKeyPath;
            if (keyPath.startsWith("~/")) {
                keyPath = System.getProperty("user.home") + keyPath.substring(1);
            }

            // 파일 존재 및 읽기 권한 확인
            File keyFile = new File(keyPath);
            if (!keyFile.exists()) {
                throw new RuntimeException("SSH 개인키 파일을 찾을 수 없습니다: " + keyPath);
            }

            if (!keyFile.canRead()) {
                throw new RuntimeException("SSH 개인키 파일을 읽을 수 없습니다. 파일 권한을 확인하세요: " + keyPath);
            }

            log.info("SSH 개인키 파일: {}", keyPath);

            // 개인키 추가
            if (passphrase != null && !passphrase.trim().isEmpty()) {
                jsch.addIdentity(keyPath, passphrase);
                log.info("패스프레이즈와 함께 개인키 로드 완료");
            } else {
                jsch.addIdentity(keyPath);
                log.info("개인키 로드 완료");
            }

            // SSH 세션 생성
            session = jsch.getSession(sshUsername, sshHost, sshPort);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "publickey");
            session.setTimeout(30000);

            log.info("SSH 서버 연결 시도: {}@{}:{}", sshUsername, sshHost, sshPort);
            session.connect();
            log.info("SSH 연결 성공");

            // 동적 포트 할당으로 포트 포워딩 설정
            this.actualLocalPort = setupPortForwardingWithDynamicPort();

            // 포트가 열릴 때까지 대기
            waitForPortToOpen(actualLocalPort);

            log.info("SSH 터널이 성공적으로 생성되었습니다: localhost:{} -> {}:{}:{}",
                actualLocalPort, sshHost, remoteHost, remotePort);

        } catch (JSchException e) {
            log.error("SSH 터널 생성 실패: {}", e.getMessage(), e);

            if (e.getMessage().contains("invalid privatekey")) {
                log.error("개인키 형식이 잘못되었습니다. RSA 형식 키를 사용하세요.");
            } else if (e.getMessage().contains("Auth fail")) {
                log.error("인증에 실패했습니다. 공개키가 서버에 등록되어 있는지 확인하세요.");
            } else if (e.getMessage().contains("Connection refused")) {
                log.error("서버 연결이 거부되었습니다. 호스트와 포트를 확인하세요.");
            }

            cleanupResources();
            throw new RuntimeException("SSH 터널 연결에 실패했습니다: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("예상치 못한 오류: {}", e.getMessage(), e);
            cleanupResources();
            throw new RuntimeException("SSH 터널 설정 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 동적 포트 할당으로 포트 포워딩을 설정합니다.
     * 설정된 포트부터 시도하고, 실패하면 다른 포트를 시도합니다.
     */
    private int setupPortForwardingWithDynamicPort() throws JSchException {
        // 먼저 설정된 포트로 시도
        int[] portsToTry = generatePortsToTry(configuredLocalPort);

        for (int port : portsToTry) {
            try {
                session.setPortForwardingL(port, remoteHost, remotePort);
                log.info("SSH 터널 로컬 포트: {} (설정값: {})", port, configuredLocalPort);
                return port;
            } catch (JSchException e) {
                if (e.getMessage().contains("cannot be bound") || e.getMessage().contains("Address already in use")) {
                    log.warn("포트 {}가 사용 중입니다. 다른 포트 시도...", port);
                } else {
                    throw e;
                }
            }
        }

        throw new JSchException("사용 가능한 포트를 찾을 수 없습니다. 시도한 포트: " + configuredLocalPort + " ~ " + (configuredLocalPort + 100));
    }

    /**
     * 시도할 포트 목록을 생성합니다.
     * 설정된 포트부터 시작해서 100개의 포트를 시도합니다.
     */
    private int[] generatePortsToTry(int startPort) {
        int[] ports = new int[100];
        for (int i = 0; i < 100; i++) {
            ports[i] = startPort + i;
        }
        return ports;
    }

    public void stop() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.info("SSH 터널이 종료되었습니다.");
        }
    }

    private void cleanupResources() {
        if (session != null && session.isConnected()) {
            try {
                session.disconnect();
                log.info("SSH 세션이 종료되었습니다.");
            } catch (Exception e) {
                log.warn("SSH 세션 종료 중 오류: {}", e.getMessage());
            }
        }
    }

    private void waitForPortToOpen(int port) throws InterruptedException {
        long timeoutMillis = 10000;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("localhost", port), 1000);
                log.info("포트 {}가 성공적으로 열렸습니다.", port);
                return;
            } catch (Exception e) {
                Thread.sleep(500);
            }
        }
        log.warn("포트 {}가 {}ms 내에 열리지 않았습니다.", port, timeoutMillis);
    }
}
