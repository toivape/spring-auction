package fi.petri.springauction.ingest;

import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionRepository;
import fi.petri.springauction.auction.AuctionType;
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

    public void ingest(AuctionRequest item) {
        // Idempotent by source item_id: a re-ingest of an already-seen item is a no-op, so admin edits
        // (title, activation, ...) captured in later versions are never reset to a fresh DRAFT.
        if (auctionRepository.findRefByItemId(item.id()).isPresent()) {
            return;
        }
        Auction auction = new Auction(
                null,
                auctionRepository.nextAuctionRef(),
                item.id(),
                null,
                item.description(),
                item.category(),
                AuctionType.FIRST_PRICE,
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
