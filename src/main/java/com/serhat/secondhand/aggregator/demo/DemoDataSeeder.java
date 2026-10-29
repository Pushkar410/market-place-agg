package com.serhat.secondhand.aggregator.demo;

import com.serhat.secondhand.aggregator.community.CommunityListing;
import com.serhat.secondhand.aggregator.community.CommunityListingRepository;
import com.serhat.secondhand.listing.domain.entity.Listing;
import com.serhat.secondhand.listing.domain.entity.enums.base.Currency;
import com.serhat.secondhand.listing.domain.entity.enums.base.ListingStatus;
import com.serhat.secondhand.listing.domain.entity.enums.base.ListingType;
import com.serhat.secondhand.listing.domain.repository.listing.ListingRepository;
import com.serhat.secondhand.user.domain.entity.User;
import com.serhat.secondhand.user.domain.entity.enums.AccountStatus;
import com.serhat.secondhand.user.domain.entity.enums.Gender;
import com.serhat.secondhand.user.domain.entity.enums.Provider;
import com.serhat.secondhand.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


/**
 * Local-demo convenience: inserts a few demo sellers and ACTIVE listings into PostgreSQL so the
 * "SecondHand (Internal DB)" source returns real rows. Idempotent; disabled by default in
 * production (aggregator.demo-seed.enabled=false).
 *
 * Demo login for every seeded user: password "Demo@1234".
 */
@Component
@ConditionalOnProperty(name = "aggregator.demo-seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final PasswordEncoder passwordEncoder;
    private final CommunityListingRepository communityRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedInternalMarketplace();
        seedCommunityListings();
    }

    private void seedInternalMarketplace() {
        if (userRepository.existsByEmail("demo.seller1@secondhand.local")) {
            log.info("Aggregator demo data already present - skipping internal seed");
            return;
        }
        User priya = user("Priya", "Nair", "demo.seller1@secondhand.local", "9000000001", true, 700);
        User arjun = user("Arjun", "Rao", "demo.seller2@secondhand.local", "9000000002", true, 320);
        User newbie = user("Sam", "Newbie", "demo.seller3@secondhand.local", "9000000003", false, 4);

        // Prices are in USD on purpose: the normalizer converts every currency to INR.
        // ListingType.OTHER on purpose: the marketplace detail pages expect category sub-entities for typed listings;
        // the aggregator infers the category (electronics, books, ...) from the title text instead.
        // Image URLs are mapped by imageSeed index - see listing() method below
        List<Listing> listings = List.of(
                listing(priya, ListingType.OTHER, "Dell XPS 13 laptop i7 16GB", "Dell XPS 13, Core i7, 16GB RAM, 512GB SSD, FHD display. Excellent condition, original charger and sleeve included. Battery health good.", 360, "Bengaluru", 1),
                listing(priya, ListingType.OTHER, "HP Pavilion 15 laptop i5 8GB 512GB SSD", "HP Pavilion 15, 10th gen i5, 8GB RAM, 512GB SSD. Lightly used, with bill and charger. Ideal for students.", 385, "Mysuru", 2),
                listing(arjun, ListingType.OTHER, "Lenovo ThinkPad T480 laptop i5 8GB", "ThinkPad T480, 8th gen i5, 8GB RAM, 256GB SSD, backlit keyboard, battery about 4 hours. Good working condition with charger.", 280, "Chennai", 3),
                listing(arjun, ListingType.OTHER, "Mountain bicycle 21 gear Hero Sprint", "Hero Sprint 21 gear mountain bicycle, serviced, new tyres and brakes. Great for commuting around campus.", 52, "Tumakuru", 4),
                listing(priya, ListingType.OTHER, "Hercules Roadeo cycle with carrier", "Hercules Roadeo single speed cycle, light use, sturdy carrier and bell. Suitable for short college rides.", 36, "Tumakuru", 5),
                listing(arjun, ListingType.OTHER, "Engineering Mathematics textbook set", "Higher Engineering Mathematics and module notes for 4 semesters, clean copies.", 11, "Manipal", 6),
                listing(priya, ListingType.OTHER, "Data Structures and Algorithms textbook", "Data Structures using C, 2nd edition, minimal highlighting, plus handwritten notes for the whole semester.", 8, "Hyderabad", 7),
                listing(priya, ListingType.OTHER, "Noise cancelling bluetooth headphones", "Wireless over-ear headphones with active noise cancellation, 30 hours battery, case included.", 38, "Bengaluru", 8),
                listing(arjun, ListingType.OTHER, "Samsung Galaxy M31 smartphone 6GB 128GB", "Samsung Galaxy M31, 6GB RAM, 128GB, 6000 mAh battery, screen guard applied since day one, box and bill available.", 105, "Pune", 9),
                listing(arjun, ListingType.OTHER, "Study table and ergonomic chair", "Sturdy study desk 4x2 ft with ergonomic chair, perfect for hostel or PG rooms.", 34, "Delhi", 10),
                listing(priya, ListingType.OTHER, "Single room for rent near SIT Tumakuru", "Furnished single room 10 minutes walk from SIT, attached bathroom, wifi, 24 hour water. Rent 5500 per month, one month deposit.", 66, "Tumakuru", 11),
                listing(newbie, ListingType.OTHER, "Brand new iPhone 14 half price", "Sealed iPhone 14, selling at half price because I need money urgently. Pay advance token and I will send by courier, no meetup.", 300, "Mumbai", 12),
                listing(priya, ListingType.OTHER, "Casio fx-991EX ClassWiz scientific calculator", "Original Casio fx-991EX scientific calculator for VTU engineering exams. Used 2 semesters, solar cell working, with hard slip-on case.", 14, "Tumakuru", 13),
                listing(arjun, ListingType.OTHER, "MacBook Air M1 8GB 256GB Space Grey", "Apple MacBook Air M1, 8GB RAM, 256GB SSD. Battery health 93%, with original charger and box. Flawless condition for programming.", 580, "Bengaluru", 14),
                listing(priya, ListingType.OTHER, "Double room PG for boys near SIT main gate", "2 sharing room with attached bathroom, 3 times North/South Indian food, high speed wifi, power backup. Walkable distance to campus.", 72, "Tumakuru", 15),
                listing(arjun, ListingType.OTHER, "Btwin MyBike single speed commuter cycle", "Decathlon Btwin MyBike, single speed, lightweight frame, side stand and mudguards included. Great for college commute in Tumkur.", 42, "Mangaluru", 16),
                listing(priya, ListingType.OTHER, "Operating System Concepts 10th Edition Silberschatz", "Silberschatz Dinosaur book for VTU CSE 4th sem. Clean copy, no torn pages, with semester solved questions.", 7, "Vellore", 17)
        );
        listingRepository.saveAll(listings);
        log.info("Aggregator demo seed: created 3 users and {} listings", listings.size());
    }

    /** Real-world style listings shared by people who found them elsewhere; also demonstrates the "share a listing" flow. */
    private void seedCommunityListings() {
        if (communityRepository.count() > 0) {
            return;
        }
        communityRepository.saveAll(List.of(
                shared("Samsung 24 inch monitor, 1 year old", "Full HD monitor with HDMI cable, no dead pixels. Selling because I moved to a laptop.", 5500.0, "Tumakuru", "ELECTRONICS", "College WhatsApp group", "https://chat.whatsapp.com/example-college-group", "Karthik", 2),
                shared("Boys PG vacancy near SIT, 2 sharing", "PG with food, wifi and hot water, 2 sharing. Rent 6500 per month, deposit 6500.", 6500.0, "Tumakuru", "HOUSING", "Notice board outside SIT gate", "https://www.google.com/maps/search/?api=1&query=Siddaganga+Institute+of+Technology+Main+Gate+Tumakuru", "Mr. Gowda", 3),
                shared("Hero Sprint 21 gear cycle", "Hero Sprint 21 gear cycle, serviced last month, good for campus commute.", 3500.0, "Tumakuru", "VEHICLES", "OLX", "https://www.herocycles.com/reaction-27.5t-ISRC7275BKGN01.html", "Rakesh", 6),
                shared("iPhone 13 128GB blue, battery health 89%", "Original charger and box available, no scratches. Can meet at the bus stand for inspection.", 38000.0, "Bengaluru", "ELECTRONICS", "Facebook Marketplace", "https://www.cashify.in/buy-refurbished-mobile-phones/apple-iphone-13", "Divya", 1),
                shared("SIT Campus Boys Hostel Room Vacancy", "Single room vacancy in SIT Boys Hostel block near sports ground. 3 times South Indian mess food included, wifi and 24x7 study hall.", 5500.0, "Tumakuru", "HOUSING", "SIT Student Notice Board", "https://www.google.com/maps/search/?api=1&query=Siddaganga+Institute+of+Technology+Boys+Hostel+Tumakuru", "Hostel Warden", 1),
                shared("Hero Sprint 21 gear cycle near SIT Main Gate", "Serviced Hero Sprint mountain bike, new tube and brake pads. Can test ride near SIT gate.", 3200.0, "Tumakuru", "VEHICLES", "College WhatsApp Group", "https://www.google.com/maps/search/?api=1&query=Siddaganga+Institute+of+Technology+Main+Gate+Tumakuru", "Sagar", 2),
                shared("Casio fx-991EX Scientific Calculator VTU", "Hardly used ClassWiz calculator, perfect for VTU 1st to 8th sem exams. Clean condition with cover.", 950.0, "Tumakuru", "ELECTRONICS", "Student Notice Board", "https://www.amazon.in/Casio-FX-991EX-Classwiz-Scientific-Calculator/dp/B0727VS486", "Anand", 3)
        ));
        log.info("Aggregator demo seed: created 4 community-shared listings");
    }

    private CommunityListing shared(String title, String description, Double price, String city, String category,
                                    String originName, String originUrl, String contactName, int daysAgo) {
        CommunityListing c = new CommunityListing();
        c.setTitle(title);
        c.setDescription(description);
        c.setPrice(price);
        c.setCity(city);
        c.setCategory(category);
        c.setOriginName(originName);
        c.setOriginUrl(originUrl);
        c.setContactName(contactName);
        c.setContactProvided(true);
        c.setActive(true);
        c.setCreatedAt(java.time.LocalDateTime.now().minusDays(daysAgo));
        return c;
    }

    private User user(String name, String surname, String email, String phone, boolean verified, int ageDays) {
        return userRepository.save(User.builder()
                .name(name)
                .surname(surname)
                .email(email)
                .phoneNumber(phone)
                .password(passwordEncoder.encode("Demo@1234"))
                .gender(Gender.PREFER_NOT_TO_SAY)
                .provider(Provider.LOCAL)
                .accountVerified(verified)
                .accountStatus(AccountStatus.ACTIVE)
                .accountCreationDate(LocalDate.now().minusDays(ageDays))
                .build());
    }

    private static final Map<Integer, String> IMAGE_MAP = Map.ofEntries(
            Map.entry(1,  "https://images.unsplash.com/photo-1593642632559-0c6d3fc62b89?w=500&auto=format&fit=crop"),  // laptop general
            Map.entry(2,  "https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2?w=500&auto=format&fit=crop"),  // HP laptop silver
            Map.entry(3,  "https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=500&auto=format&fit=crop"),  // ThinkPad
            Map.entry(4,  "https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=500&auto=format&fit=crop"),  // bicycle
            Map.entry(5,  "https://images.unsplash.com/photo-1532298229144-0ec0c57515c7?w=500&auto=format&fit=crop"),  // bicycle road
            Map.entry(6,  "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=500&auto=format&fit=crop"),  // textbooks
            Map.entry(7,  "https://images.unsplash.com/photo-1507842229450-490b07b19a9a?w=500&auto=format&fit=crop"),  // books stack
            Map.entry(8,  "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500&auto=format&fit=crop"),  // headphones
            Map.entry(9,  "https://images.unsplash.com/photo-1610945264803-c22b62d2a7b3?w=500&auto=format&fit=crop"),  // android phone
            Map.entry(10, "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=500&auto=format&fit=crop"),  // desk chair
            Map.entry(11, "https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=500&auto=format&fit=crop"),  // room interior
            Map.entry(12, "https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=500&auto=format&fit=crop"),  // iphone
            Map.entry(13, "https://images.unsplash.com/photo-1594980596870-8aa52a78d8cd?w=500&auto=format&fit=crop"),  // calculator
            Map.entry(14, "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?w=500&auto=format&fit=crop"),  // macbook
            Map.entry(15, "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?w=500&auto=format&fit=crop"),  // PG room
            Map.entry(16, "https://images.unsplash.com/photo-1532298229144-0ec0c57515c7?w=500&auto=format&fit=crop"),  // city bike
            Map.entry(17, "https://images.unsplash.com/photo-1507842229450-490b07b19a9a?w=500&auto=format&fit=crop")   // OS book
    );

    private Listing listing(User seller, ListingType type, String title, String description, int priceUsd, String city, int imageSeed) {
        String imageUrl = IMAGE_MAP.getOrDefault(imageSeed,
                "https://images.unsplash.com/photo-1593642632559-0c6d3fc62b89?w=500&auto=format&fit=crop");
        Listing l = Listing.builder()
                .title(title)
                .description(description)
                .price(BigDecimal.valueOf(priceUsd))
                .currency(Currency.USD)
                .status(ListingStatus.ACTIVE)
                .listingType(type)
                .isListingFeePaid(true)
                .city(city)
                .cityKey(city.toLowerCase())
                .imageUrl(imageUrl)
                .seller(seller)
                .build();
        return l;
    }
}

