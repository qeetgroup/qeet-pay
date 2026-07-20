package com.qeetgroup.qeetpay.cards;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sandbox card-issuing rail — the default internal simulation (no real card is minted). Preserves the
 * pre-existing in-process behaviour: a random four-digit {@code last4} (so the masked PAN keeps its
 * {@code "XXXX XXXX XXXX ####"} shape) plus a deterministic sandbox reference derived from the card id.
 * Freeze / unfreeze / close and load / spend authorization are local no-ops — lifecycle and balances
 * are tracked entirely in the ledger and the {@link VirtualCard} entity.
 *
 * <p>Active whenever no {@code liveCardIssuingProvider} bean is present (i.e. {@code
 * qeetpay.cards.enabled} is unset/false), mirroring {@code SandboxKybAdapter} / {@code
 * SandboxFxRateAdapter}.
 */
@Component
@ConditionalOnMissingBean(name = "liveCardIssuingProvider")
public class SandboxCardIssuingProvider implements CardIssuingProvider {

    private static final DateTimeFormatter MM_YY = DateTimeFormatter.ofPattern("MM/yy", Locale.ROOT);

    @Override
    public IssuedCard issue(CardIssueRequest request) {
        String last4 = String.format(Locale.ROOT, "%04d", ThreadLocalRandom.current().nextInt(10_000));
        String expiry = YearMonth.now().plusYears(3).format(MM_YY);
        return new IssuedCard("sbx_" + request.cardId(), last4, expiry);
    }

    @Override
    public void freeze(String networkCardRef) {
        // no-op — sandbox tracks lifecycle state locally on the VirtualCard.
    }

    @Override
    public void unfreeze(String networkCardRef) {
        // no-op.
    }

    @Override
    public void close(String networkCardRef) {
        // no-op.
    }
}
