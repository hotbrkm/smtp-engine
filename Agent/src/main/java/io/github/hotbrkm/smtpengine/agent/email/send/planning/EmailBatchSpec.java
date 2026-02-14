package io.github.hotbrkm.smtpengine.agent.email.send.planning;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendContext;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResultWriter;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;

@Getter
@RequiredArgsConstructor
public final class EmailBatchSpec {

    private final String batchId;
    private final String domain;
    private final List<EmailSendTarget> emailSendTargetList;
    private final String runnerId;
    private final EmailBatchResultWriter resultWriter;
    private final EmailSendContext emailSendContext;

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (EmailBatchSpec) obj;
        return Objects.equals(this.batchId, that.batchId) &&
               Objects.equals(this.domain, that.domain) &&
               Objects.equals(this.emailSendTargetList, that.emailSendTargetList) &&
               Objects.equals(this.runnerId, that.runnerId) &&
               Objects.equals(this.resultWriter, that.resultWriter) &&
               Objects.equals(this.emailSendContext, that.emailSendContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, domain, emailSendTargetList, runnerId, resultWriter, emailSendContext);
    }

    @Override
    public String toString() {
        return "EmailBatchSubmitRequest[" +
               "batchId=" + batchId + ", " +
               "domain=" + domain + ", " +
               "requests=" + emailSendTargetList + ", " +
               "runnerId=" + runnerId + ", " +
               "resultWriter=" + resultWriter + ", " +
               "emailSendContext=" + emailSendContext + ']';
    }

}

