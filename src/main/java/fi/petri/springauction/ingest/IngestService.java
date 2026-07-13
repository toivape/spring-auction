package fi.petri.springauction.ingest;

import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class IngestService {

    private final AuctionRepository auctionRepository;

    public IngestService(AuctionRepository auctionRepository) {
        this.auctionRepository = auctionRepository;
    }

    public void ingest(NewAuctionItem item) {
        Auction auction = new Auction(
                null,
                item.id(),
                null,
                item.description(),
                item.category(),
                null,
                AuctionLifecycleStatus.DRAFT,
                BigDecimal.valueOf(item.price()),
                BigDecimal.valueOf(item.currentValue()),
                item.currency(),
                LocalDate.parse(item.startDate()).atStartOfDay(ZoneOffset.UTC).toInstant(),
                null,
                item.comment(),
                item.serialNumber(),
                Instant.now()
        );
        auctionRepository.save(auction);
    }
}
