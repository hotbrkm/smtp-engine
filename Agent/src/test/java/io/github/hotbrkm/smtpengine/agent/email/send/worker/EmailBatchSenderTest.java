package io.github.hotbrkm.smtpengine.agent.email.send.worker;

import io.github.hotbrkm.smtpengine.agent.email.mime.EmailMimeComposer;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.result.ResultPersistenceException;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommand;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommandResponse;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionOpenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EmailBatchSender test")
class EmailBatchSenderTest {

    @DisplayName("On successful send, cleans up session with QUIT and closeSession")
    @Test
    void callShouldSendQuitAndCloseSessionOnSuccess() throws Exception {
        SmtpSessionManager smtpSessionManager = mock(SmtpSessionManager.class);
        EmailMimeComposer emailMimeComposer = mock(EmailMimeComposer.class);
        ResultApplier resultApplier = spy(new ResultApplier(null, null, new DomainSendMetrics(5, 60)));

        EmailSendTarget target = createTarget("user-success@example.com");
        EmailBatchSender sender = new EmailBatchSender(
                List.of(target),
                "example.com",
                "127.0.0.1",
                smtpSessionManager,
                emailMimeComposer,
                resultApplier,
                List.of(),
                new DomainSendMetrics(5, 60)
        );

        when(smtpSessionManager.isSessionValid()).thenReturn(true);
        when(smtpSessionManager.sendMailFrom(anyString())).thenReturn(response(SmtpCommand.MAIL_FROM, "250 OK"));
        when(smtpSessionManager.sendRcptTo(anyString())).thenReturn(response(SmtpCommand.RCPT_TO, "250 OK"));
        when(smtpSessionManager.sendData()).thenReturn(response(SmtpCommand.DATA, "354 Start mail input"));
        when(smtpSessionManager.sendMessage(anyString(), anyString())).thenReturn(response(SmtpCommand.DATA_END, "250 Accepted"));
        when(smtpSessionManager.sendRset()).thenReturn(response(SmtpCommand.RSET, "250 Reset"));
        when(smtpSessionManager.sendQuit()).thenReturn(response(SmtpCommand.QUIT, "221 Bye"));
        when(emailMimeComposer.makeMime(any(EmailSendTarget.class))).thenReturn("mime-content");

        int successCount = sender.call();

        assertThat(successCount).isEqualTo(1);
        assertThat(target.getSendCode()).isEqualTo("250");
        assertThat(target.getSendStatus()).isEqualTo("SUCCESS");
        assertThat(target.hasAttribute("message")).isFalse();
        assertThat(target.hasAttribute("mailBody")).isFalse();
        assertThat(target.hasAttribute("encodedBody")).isFalse();
        assertThat(target.hasAttribute("mime")).isFalse();

        verify(smtpSessionManager).sendQuit();
        verify(smtpSessionManager).closeSession();
        verify(resultApplier).cleanup(target);
    }

    @DisplayName("On result persistence exception, still performs QUIT/closeSession in finally block")
    @Test
    void callShouldFinalizeSessionWhenResultPersistenceExceptionOccurs() throws Exception {
        SmtpSessionManager smtpSessionManager = mock(SmtpSessionManager.class);
        EmailMimeComposer emailMimeComposer = mock(EmailMimeComposer.class);
        ResultApplier resultApplier = spy(new ResultApplier(null, null, new DomainSendMetrics(5, 60)));

        EmailSendTarget target = createTarget("user-persistence-error@example.com");
        EmailBatchSender sender = new EmailBatchSender(
                List.of(target),
                "example.com",
                "127.0.0.1",
                smtpSessionManager,
                emailMimeComposer,
                resultApplier,
                List.of(),
                new DomainSendMetrics(5, 60)
        );

        when(smtpSessionManager.isSessionValid()).thenReturn(true);
        when(smtpSessionManager.sendMailFrom(anyString())).thenReturn(response(SmtpCommand.MAIL_FROM, "250 OK"));
        when(smtpSessionManager.sendRcptTo(anyString())).thenReturn(response(SmtpCommand.RCPT_TO, "250 OK"));
        when(smtpSessionManager.sendData()).thenReturn(response(SmtpCommand.DATA, "354 Start mail input"));
        when(smtpSessionManager.sendMessage(anyString(), anyString())).thenReturn(response(SmtpCommand.DATA_END, "250 Accepted"));
        when(smtpSessionManager.sendQuit()).thenReturn(response(SmtpCommand.QUIT, "221 Bye"));
        when(emailMimeComposer.makeMime(any(EmailSendTarget.class))).thenReturn("mime-content");
        doThrow(new ResultPersistenceException("checkpoint failed"))
                .when(resultApplier)
                .apply(any(EmailSendTarget.class), any());

        assertThatThrownBy(sender::call)
                .isInstanceOf(ResultPersistenceException.class)
                .hasMessageContaining("checkpoint failed");

        verify(smtpSessionManager).sendQuit();
        verify(smtpSessionManager).closeSession();
        verify(resultApplier).cleanup(target);
    }

    @DisplayName("On session open failure, still calls closeSession for cleanup")
    @Test
    void callShouldCloseSessionWhenOpenSessionFails() {
        SmtpSessionManager smtpSessionManager = mock(SmtpSessionManager.class);
        EmailMimeComposer emailMimeComposer = mock(EmailMimeComposer.class);
        ResultApplier resultApplier = spy(new ResultApplier(null, null, new DomainSendMetrics(5, 60)));

        EmailSendTarget target = createTarget("user-open-fail@example.com");
        EmailBatchSender sender = new EmailBatchSender(
                List.of(target),
                "example.com",
                "127.0.0.1",
                smtpSessionManager,
                emailMimeComposer,
                resultApplier,
                List.of(),
                new DomainSendMetrics(5, 60)
        );

        doThrow(new SmtpSessionOpenException(602, "602 connect failed", "127.0.0.1", false))
                .when(smtpSessionManager)
                .openSession(anyString(), anyString());
        when(smtpSessionManager.isSessionValid()).thenReturn(false);

        int successCount = sender.call();

        assertThat(successCount).isZero();
        assertThat(target.getSendCode()).isEqualTo("602");
        assertThat(target.getSendStatus()).isEqualTo("FAILURE");

        verify(smtpSessionManager, never()).sendQuit();
        verify(smtpSessionManager).closeSession();
    }

    private EmailSendTarget createTarget(String targetEmail) {
        Map<String, Object> targetData = new HashMap<>();
        targetData.put("message", "legacy-message");
        targetData.put("mailBody", "legacy-mail-body");
        targetData.put("encodedBody", "legacy-encoded-body");
        targetData.put("mime", "legacy-mime");

        return EmailSendTarget.builder()
                .targetId("user-1")
                .targetName("User")
                .targetEmail(targetEmail)
                .senderName("Sender")
                .senderEmail("sender@example.com")
                .title("Test title")
                .body("<p>Test body</p>")
                .listSeq(1)
                .retryCount(0)
                .sendCode("701")
                .sendStatus("PENDING")
                .targetData(targetData)
                .build();
    }

    private SmtpCommandResponse response(SmtpCommand command, String line) {
        return new SmtpCommandResponse(command, List.of(line));
    }
}

