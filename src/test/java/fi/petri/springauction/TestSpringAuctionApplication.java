package fi.petri.springauction;

import org.springframework.boot.SpringApplication;

public class TestSpringAuctionApplication {

    public static void main(String[] args) {
        SpringApplication.from(SpringAuctionApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
