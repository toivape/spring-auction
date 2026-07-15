package fi.petri.springauction.admin;

import fi.petri.springauction.auction.AuctionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Controller
public class AdminAuctionController {

    private final AuctionService auctionService;

    public AdminAuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @GetMapping("/admin/auctions")
    public String list(Model model) {
        model.addAttribute("auctions", auctionService.findAll());
        return "admin/auctions";
    }

    @GetMapping("/admin/auctions/{id}/activate")
    public String activateForm(@PathVariable Long id, Model model) {
        model.addAttribute("auction", auctionService.findById(id));
        return "admin/activate-auction";
    }

    @PostMapping("/admin/auctions/{id}/activate")
    public String activate(@PathVariable Long id,
                            @RequestParam(required = false) String startsAt,
                            @RequestParam(required = false) String endsAt) {
        auctionService.activate(id, parseDateTime(startsAt), parseDateTime(endsAt));
        return "redirect:/admin/auctions";
    }

    @PostMapping("/admin/auctions/{id}/archive")
    public String archive(@PathVariable Long id) {
        auctionService.archive(id);
        return "redirect:/admin/auctions";
    }

    @PostMapping("/admin/auctions/{id}/cancel")
    public String cancel(@PathVariable Long id) {
        auctionService.cancel(id);
        return "redirect:/admin/auctions";
    }

    @GetMapping("/admin/auctions/{id}/extend")
    public String extendForm(@PathVariable Long id, Model model) {
        model.addAttribute("auction", auctionService.findById(id));
        return "admin/extend-auction";
    }

    @PostMapping("/admin/auctions/{id}/extend")
    public String extend(@PathVariable Long id, @RequestParam(required = false) String endsAt) {
        auctionService.extend(id, parseDateTime(endsAt));
        return "redirect:/admin/auctions";
    }

    private Instant parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    }

}
