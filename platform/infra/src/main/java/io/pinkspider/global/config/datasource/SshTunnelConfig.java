package io.pinkspider.global.config.datasource;

import io.pinkspider.global.component.SshTunnel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Slf4j
@Profile("!test & !push-test")
public class SshTunnelConfig {

    @Bean(name = "sshTunnel", initMethod = "start", destroyMethod = "stop")
    public SshTunnel sshTunnel(
        @Value("${app.ssh.tunnel.host}") String sshHost,
        @Value("${app.ssh.tunnel.port}") int sshPort,
        @Value("${app.ssh.tunnel.username}") String sshUsername,
        @Value("${app.ssh.tunnel.private-key-path}") String privateKeyPath,
        @Value("${app.ssh.tunnel.local-port}") int localPort,
        @Value("${app.ssh.tunnel.remote-host}") String remoteHost,
        @Value("${app.ssh.tunnel.remote-port}") int remotePort,
        @Value("${app.ssh.tunnel.passphrase:}") String passphrase
    ) {
        return new SshTunnel(sshHost, sshPort, sshUsername, privateKeyPath,
            localPort, remoteHost, remotePort, passphrase);
    }
}
