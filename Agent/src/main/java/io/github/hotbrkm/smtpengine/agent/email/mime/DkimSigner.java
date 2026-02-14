package io.github.hotbrkm.smtpengine.agent.email.mime;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.jdkim.DKIMSigner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;

@Slf4j
class DkimSigner {

    private static final String signatureTemplate = "v=1; a=rsa-sha256; q=dns/txt; c=simple/simple; d=%s; s=%s; "
                                                    + "h=Content-Type:MIME-Version:Subject:Message-ID:To:Sender:From:Date; bh=; b=";

    private final EmailConfig.Dkim dkim;
    private final DKIMSigner signer;

    public DkimSigner(EmailConfig.Dkim dkim) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        this.dkim = dkim;
        this.signer = initializeDkimSigner();
    }

    private DKIMSigner initializeDkimSigner() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        String signatureTemplate = getSignatureTemplate();
        PrivateKey privateKey = loadPrivateKey(dkim.getKeyPath());
        return new DKIMSigner(signatureTemplate, privateKey);
    }

    private String getSignatureTemplate() {
        return String.format(signatureTemplate, dkim.getDomain(), dkim.getSelector());
    }

    private PrivateKey loadPrivateKey(String privateKeyPath) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        Path keyPath = Paths.get(privateKeyPath);
        String privateKeyData = Files.readString(keyPath, Charset.defaultCharset());
        privateKeyData = privateKeyData.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");
        return DKIMSigner.getPrivateKey(privateKeyData);
    }

    public Optional<String> sign(String content, String domain) {
        if (!dkim.isTargetDomain(domain)) {
            return Optional.empty();
        }

        try {
            String signature = signer.sign(new ByteArrayInputStream(content.getBytes()));
            return Optional.of(signature);
        } catch (Exception e) {
            log.error("DKIM signing failed", e);
            return Optional.empty();
        }
    }

}