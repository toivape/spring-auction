package fi.petri.springauction.ingest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/api/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public void ingest(@Valid @RequestBody NewAuctionItem body) {
        ingestService.ingest(body);
    }

}
