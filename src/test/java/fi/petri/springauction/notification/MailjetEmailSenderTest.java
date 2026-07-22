package fi.petri.springauction.notification;

import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.errors.MailjetException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test of the Mailjet transport — no Spring context, no network. The client is mocked; we assert
 * the request body is well-formed and that non-success responses / client errors surface as exceptions
 * (so {@link AuctionEmailNotifier}'s per-recipient catch logs and moves on).
 */
class MailjetEmailSenderTest {

    private static final NotificationProperties PROPS =
            new NotificationProperties("http://localhost:8080", "auctions@spring-auction.local");

    @Test
    void sendBuildsAWellFormedMailjetMessage() throws Exception {
        MailjetClient client = mock(MailjetClient.class);
        when(client.post(any(MailjetRequest.class)))
                .thenReturn(new MailjetResponse(200, "{\"Messages\":[{\"Status\":\"success\"}]}"));
        MailjetEmailSender sender = new MailjetEmailSender(client, PROPS);

        sender.send("bidder@example.com", "Auction won", "<p>You won</p>");

        ArgumentCaptor<MailjetRequest> captor = ArgumentCaptor.forClass(MailjetRequest.class);
        org.mockito.Mockito.verify(client).post(captor.capture());
        JSONObject message = captor.getValue().getBodyJSON().getJSONArray("Messages").getJSONObject(0);
        assertEquals("auctions@spring-auction.local", message.getJSONObject("From").getString("Email"));
        assertEquals("bidder@example.com", message.getJSONArray("To").getJSONObject(0).getString("Email"));
        assertEquals("Auction won", message.getString("Subject"));
        assertEquals("<p>You won</p>", message.getString("HTMLPart"));
    }

    @Test
    void nonSuccessStatusThrows() throws Exception {
        MailjetClient client = mock(MailjetClient.class);
        when(client.post(any(MailjetRequest.class))).thenReturn(new MailjetResponse(400, "{\"ErrorMessage\":\"bad\"}"));
        MailjetEmailSender sender = new MailjetEmailSender(client, PROPS);

        assertThrows(IllegalStateException.class,
                () -> sender.send("bidder@example.com", "Auction won", "<p>hi</p>"));
    }

    @Test
    void perMessageFailureInA200ResponseThrows() throws Exception {
        // Send API v3.1 reports rejected messages inside a 200 response via Messages[].Status.
        MailjetClient client = mock(MailjetClient.class);
        when(client.post(any(MailjetRequest.class))).thenReturn(new MailjetResponse(200,
                "{\"Messages\":[{\"Status\":\"error\",\"Errors\":[{\"ErrorMessage\":\"invalid recipient\"}]}]}"));
        MailjetEmailSender sender = new MailjetEmailSender(client, PROPS);

        assertThrows(IllegalStateException.class,
                () -> sender.send("bidder@example.com", "Auction won", "<p>hi</p>"));
    }

    @Test
    void clientExceptionIsWrapped() throws Exception {
        MailjetClient client = mock(MailjetClient.class);
        when(client.post(any(MailjetRequest.class))).thenThrow(new MailjetException("boom"));
        MailjetEmailSender sender = new MailjetEmailSender(client, PROPS);

        assertThrows(IllegalStateException.class,
                () -> sender.send("bidder@example.com", "Auction won", "<p>hi</p>"));
    }
}
