package com.archops.common.ssh;

import com.archops.common.exception.BusinessException;
import com.archops.curated.domain.HostSshSecretKind;
import com.archops.curated.service.HostSshCredentialService;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Production SSH adapter (Apache MINA SSHD) on the 执行引擎 only.
 * Not a control-plane {@code @Component}: {@link com.archops.executor.ExecutorApplication} imports it.
 */
@ConditionalOnProperty(name = "archops.ssh.mode", havingValue = "mina")
public class MinaSshPort implements ControlledSshPort {

    private final HostSshCredentialService credentialService;
    private final SshClient client;

    public MinaSshPort(HostSshCredentialService credentialService) {
        this.credentialService = credentialService;
        this.client = SshClient.setUpDefaultClient();
        this.client.start();
    }

    @Override
    public SshExecResult exec(SshExecRequest request) {
        HostSshCredentialService.DecryptedHostSshCredential cred =
                credentialService.requireDecrypted(request.hostId());
        try (ClientSession session = connect(cred)) {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            try (ClientChannel channel = session.createExecChannel(request.command())) {
                channel.setOut(stdout);
                channel.setErr(stderr);
                channel.open().verify(Duration.ofSeconds(15));
                Collection<ClientChannelEvent> events =
                        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TimeUnit.SECONDS.toMillis(60));
                if (!events.contains(ClientChannelEvent.CLOSED)) {
                    return SshExecResult.fail("SSH command timed out on host " + request.hostId());
                }
                Integer exit = channel.getExitStatus();
                int code = exit == null ? 1 : exit;
                if (code != 0) {
                    String err = stderr.toString(StandardCharsets.UTF_8);
                    return SshExecResult.fail(err.isBlank() ? "SSH exit code " + code : err);
                }
                return SshExecResult.ok(stdout.toString(StandardCharsets.UTF_8));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            return SshExecResult.fail("SSH failed: " + ex.getMessage());
        }
    }

    private ClientSession connect(HostSshCredentialService.DecryptedHostSshCredential cred) throws Exception {
        ClientSession session = client.connect(cred.username(), cred.connectHost(), cred.connectPort())
                .verify(Duration.ofSeconds(15))
                .getSession();
        if (cred.secretKind() == HostSshSecretKind.PASSWORD) {
            session.addPasswordIdentity(cred.secret());
        } else {
            Iterable<KeyPair> keys = SecurityUtils.getKeyPairResourceParser().loadKeyPairs(
                    null,
                    null,
                    FilePasswordProvider.EMPTY,
                    new ByteArrayInputStream(cred.secret().getBytes(StandardCharsets.UTF_8))
            );
            boolean added = false;
            for (KeyPair kp : keys) {
                session.addPublicKeyIdentity(kp);
                added = true;
            }
            if (!added) {
                throw new BusinessException("SSH_KEY_INVALID", "Private key material could not be parsed");
            }
        }
        session.auth().verify(Duration.ofSeconds(15));
        return session;
    }
}
