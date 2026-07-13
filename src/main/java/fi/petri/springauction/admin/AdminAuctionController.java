package fi.petri.springauction.admin;

import fi.petri.springauction.auction.AuctionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminAuctionController {

    private final AuctionService auctionService;

    public AdminAuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping("/admin/auctions/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable Long id) {
        auctionService.activate(id);
    }

}
