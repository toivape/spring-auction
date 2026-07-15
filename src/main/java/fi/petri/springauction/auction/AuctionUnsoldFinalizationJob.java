package fi.petri.springauction.auction;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuctionUnsoldFinalizationJob {

    private final AuctionService auctionService;

    public AuctionUnsoldFinalizationJob(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @Scheduled(fixedDelayString = "PT30S")
    public void finalizeUnsoldAuctions() {
        auctionService.finalizeUnsold();
    }

}
