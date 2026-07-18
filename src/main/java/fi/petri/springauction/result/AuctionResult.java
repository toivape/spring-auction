package fi.petri.springauction.result;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The immutable outcome of a finalized auction. Core fields (result_status, winner, winning_price,
 * finalized_at) never change once written; only paid_at and the invalidation fields may be updated later.
 * The unique constraint on auction_id is the DB-level idempotency backstop for finalization.
 */
@Table("auction_result")
public record AuctionResult(
        @Id Long id,
        Long auctionId,
        ResultStatus resultStatus,
        Long winnerUserId,
        BigDecimal winningPrice,
        Instant finalizedAt,
        Instant paidAt,
        Instant invalidatedAt,
        Long invalidatedBy,
        String invalidationReason,
        @Version Long version
) {
}
