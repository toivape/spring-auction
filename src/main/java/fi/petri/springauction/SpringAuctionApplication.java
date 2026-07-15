package fi.petri.springauction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class SpringAuctionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAuctionApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

}
