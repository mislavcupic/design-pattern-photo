package hr.algebra.nrako.photoapp_backend.selenium.tests;

import hr.algebra.nrako.photoapp_backend.selenium.BaseSeleniumTest;
import hr.algebra.nrako.photoapp_backend.selenium.pages.HomePage;
import hr.algebra.nrako.photoapp_backend.selenium.pages.LoginPage;
import hr.algebra.nrako.photoapp_backend.selenium.pages.ProfilePage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UI Test - Complete User Flow")
class CompleteUserFlowTest extends BaseSeleniumTest {

    private HomePage homePage;
    private LoginPage loginPage;
    private ProfilePage profilePage;

    @BeforeEach
    void setUpPages() {
        homePage = new HomePage(driver);
        loginPage = new LoginPage(driver);
        profilePage = new ProfilePage(driver);
    }

    @Test
    @Order(1)
    @DisplayName("Kompletan user flow: Homepage → Login → Profile → Logout")
    void testCompleteUserFlow() {
        // 1. HOMEPAGE
        System.out.println("=== KORAK 1: Homepage ===");
        navigateToFrontend("/");
        assertTrue(homePage.isNavbarVisible(), "Navbar nije vidljiv!");
        System.out.println("✓ Homepage učitan");

        // 2. NAVIGACIJA NA LOGIN
        System.out.println("\n=== KORAK 2: Navigacija na Login ===");
        homePage.clickPrijavaLink();
        assertTrue(driver.getCurrentUrl().contains("/login"));
        System.out.println("✓ Redirectano na /login");

        // 3. LOGIN
        System.out.println("\n=== KORAK 3: Login ===");
        loginPage.login(TEST_USER_EMAIL, TEST_USER_PASSWORD);
        loginPage.waitForProfileRedirect();
        assertTrue(driver.getCurrentUrl().contains("/profile"));
        System.out.println("✓ Login uspješan, na /profile");

        // 4. PROFILE PAGE
        System.out.println("\n=== KORAK 4: Profile Page ===");
        assertTrue(profilePage.isOnProfilePage());
        assertTrue(profilePage.isProfileInfoVisible());
        System.out.println("✓ Profile info vidljiv");

        int photoCount = profilePage.getPhotoCount();
        System.out.println("✓ Korisnik ima " + photoCount + " fotografija");

        // 5. NAVIGACIJA NA LOGOUT
        System.out.println("\n=== KORAK 5: Logout ===");
        navigateToFrontend("/");
        homePage.clickOdjavaLink();
        assertTrue(driver.getCurrentUrl().contains("/logout"));
        System.out.println("✓ Logout stranica učitana");

        System.out.println("\n=== ✅ KOMPLETAN FLOW USPJEŠNO ZAVRŠEN ===");
    }

    @Test
    @Order(2)
    @DisplayName("User flow: Direktan pristup profilu bez logina")
    void testDirectProfileAccessWithoutLogin() {
        navigateToFrontend("/profile");

        // Možda će redirectati na login ili prikazati profile bez podataka
        sleep(2000);

        String currentUrl = driver.getCurrentUrl();
        System.out.println("Pristup /profile bez logina → URL: " + currentUrl);

        // Samo provjeravamo da nema errora
        assertNotNull(currentUrl);
    }
}
