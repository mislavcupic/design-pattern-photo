package hr.algebra.nrako.photoapp_backend.selenium.tests;

import hr.algebra.nrako.photoapp_backend.selenium.BaseSeleniumTest;
import hr.algebra.nrako.photoapp_backend.selenium.pages.HomePage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UI Test - Navigacija")
class NavigationUITest extends BaseSeleniumTest {

    private HomePage homePage;

    @BeforeEach
    void setUpPages() {
        homePage = new HomePage(driver);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Učitaj homepage i provjeri navbar")
    void testHomePage() {
        navigateToFrontend("/");

        assertTrue(homePage.isNavbarVisible(), "Navbar nije vidljiv!");
        assertTrue(homePage.isOnHomePage(), "Nije na homepage-u!");

        System.out.println("✓ Homepage učitan, navbar prisutan");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Navigacija na login stranicu")
    void testNavigateToLogin() {
        navigateToFrontend("/");

        homePage.clickPrijavaLink();

        assertTrue(driver.getCurrentUrl().contains("/login"), "Nije redirectano na /login!");
        System.out.println("✓ Uspješan redirect na login stranicu");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Navigacija na sve stranice")
    void testAllNavigationLinks() {
        navigateToFrontend("/");

        // Test Profil link
        homePage.clickProfilLink();
        assertTrue(driver.getCurrentUrl().contains("/profile"));
        System.out.println("✓ Profil link radi");

        // Povratak na home
        navigateToFrontend("/");

        // Test Registracija link
        homePage.clickRegistracijaLink();
        assertTrue(driver.getCurrentUrl().contains("/register"));
        System.out.println("✓ Registracija link radi");

        // Povratak na home
        navigateToFrontend("/");

        // Test Odjava link
        homePage.clickOdjavaLink();
        assertTrue(driver.getCurrentUrl().contains("/logout"));
        System.out.println("✓ Odjava link radi");
    }
}