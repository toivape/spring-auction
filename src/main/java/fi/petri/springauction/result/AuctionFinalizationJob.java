package fi.petri.springauction.result;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives winner-selection finalization on a schedule. Deliberately plain (no ShedLock) for now, matching
 * the single-instance dev app and {@code AuctionUnsoldFinalizationJob}; distributed locking is a follow-up
 * before running multiple instances.
 */
@Component
public class AuctionFinalizationJob {

    private final AuctionFinalizationService finalizationService;

    public AuctionFinalizationJob(AuctionFinalizationService finalizationService) {
        this.finalizationService = finalizationService;
    }

    @Scheduled(fixedDelayString = "PT30S")
    public void finalizeEndedAuctions() {
        finalizationService.finalizeEndedAuctions();
    }

}
