package fi.petri.springauction.admin;

import fi.petri.springauction.auction.Auction;
import fi.petri.springauction.auction.AuctionLifecycleStatus;
import fi.petri.springauction.auction.AuctionService;
import fi.petri.springauction.auction.AuctionType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Controller
public class AdminAuctionController {

    private static final List<Integer> PAGE_SIZES = List.of(15, 30, 60);

    private final AuctionService auctionService;

    public AdminAuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @GetMapping("/admin/auctions")
    public String list(@RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "15") int size,
                        Model model) {
        AuctionLifecycleStatus statusFilter = parseStatus(status);
        int resolvedSize = PAGE_SIZES.contains(size) ? size : PAGE_SIZES.get(0);
        Pageable pageable = PageRequest.of(Math.max(page, 0), resolvedSize, Sort.by("id").ascending());
        Page<Auction> auctionPage = auctionService.findPage(statusFilter, pageable);

        model.addAttribute("auctionPage", auctionPage);
        model.addAttribute("auctions", auctionPage.getContent());
        model.addAttribute("statuses", AuctionLifecycleStatus.values());
        model.addAttribute("selectedStatus", statusFilter);
        model.addAttribute("pageSizes", PAGE_SIZES);
        model.addAttribute("selectedSize", resolvedSize);
        return "admin/auctions";
    }

    private AuctionLifecycleStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AuctionLifecycleStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @InitBinder("form")
    void initFormBinder(WebDataBinder binder) {
        // HTML <input type="number"> always submits a '.' decimal separator, so parse locale-invariantly
        // via new BigDecimal(...) rather than a locale-sensitive NumberFormat (which would mis-read "10.50"
        // in e.g. fi_FI). Blank fields bind to null so optional fields stay null and required numeric fields
        // fail as @NotNull rather than a typeMismatch.
        binder.registerCustomEditor(BigDecimal.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null || text.isBlank() ? null : new BigDecimal(text.trim()));
            }
        });
    }

    @GetMapping("/admin/auctions/new")
    public String createForm(Model model) {
        model.addAttribute("form", NewAuctionForm.empty());
        model.addAttribute("auctionTypes", AuctionType.values());
        return "admin/new-auction";
    }

    @PostMapping("/admin/auctions")
    public String create(@Valid @ModelAttribute("form") NewAuctionForm form, BindingResult binding, Model model) {
        if (!binding.hasFieldErrors("itemId") && auctionService.itemIdExists(form.itemId())) {
            binding.rejectValue("itemId", "duplicate", "An auction with this item ID already exists");
        }
        if (binding.hasErrors()) {
            model.addAttribute("auctionTypes", AuctionType.values());
            return "admin/new-auction";
        }
        auctionService.create(form.toCommand());
        return "redirect:/admin/auctions";
    }

    @GetMapping("/admin/auctions/{id}/activate")
    public String activateForm(@PathVariable Long id, Model model) {
        model.addAttribute("auction", auctionService.findById(id));
        model.addAttribute("auctionTypes", AuctionType.values());
        return "admin/activate-auction";
    }

    @PostMapping("/admin/auctions/{id}/activate")
    public String activate(@PathVariable Long id,
                            @RequestParam(required = false) String auctionType,
                            @RequestParam(required = false) String startsAt,
                            @RequestParam(required = false) String endsAt) {
        auctionService.activate(id, parseAuctionType(auctionType), parseDateTime(startsAt), parseDateTime(endsAt));
        return "redirect:/admin/auctions";
    }

    private AuctionType parseAuctionType(String auctionType) {
        if (auctionType == null || auctionType.isBlank()) {
            return AuctionType.FIRST_PRICE;
        }
        try {
            return AuctionType.valueOf(auctionType);
        } catch (IllegalArgumentException e) {
            return AuctionType.FIRST_PRICE;
        }
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
    public String extend(@PathVariable Long id,
                         @RequestParam(required = false) String endsAt,
                         @RequestParam(required = false) String startPrice) {
        auctionService.extend(id, parseDateTime(endsAt), parseDecimal(startPrice));
        return "redirect:/admin/auctions";
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private Instant parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // The datetime-local input carries the admin's local wall-clock time with no zone; interpret it
        // in the server's zone (same machine in local dev), not UTC — otherwise it shifts by the offset.
        return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
    }

}
