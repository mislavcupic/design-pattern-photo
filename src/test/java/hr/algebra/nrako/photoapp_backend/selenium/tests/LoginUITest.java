package hr.algebra.nrako.photoapp_backend.selenium.tests;

import hr.algebra.nrako.photoapp_backend.selenium.BaseSeleniumTest;
import hr.algebra.nrako.photoapp_backend.selenium.pages.LoginPage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UI Test - Login Flow")
class LoginUITest extends BaseSeleniumTest {

    private LoginPage loginPage;

    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(driver);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Login button disabled bez inputa")
    void testLoginButtonDisabledWithoutInput() {
        navigateToFrontend("/login");

        assertFalse(loginPage.isLoginButtonEnabled(), "Login button bi trebao biti disabled!");
        System.out.println("✓ Login button je disabled bez inputa");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Neuspješan login sa lošim kredencijalima")
    void testInvalidLogin() {
        navigateToFrontend("/login");

        loginPage.login("nepostojeci@email.com", "krivaLozinka123");

        sleep(2000); // Čekaj Firebase odgovor

        assertTrue(loginPage.isErrorDisplayed(), "Error poruka nije prikazana!");
        System.out.println("✓ Error poruka prikazana za loše kredencijale");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Uspješan login sa validnim kredencijalima")
    void testSuccessfulLogin() {
        navigateToFrontend("/login");

        loginPage.login(TEST_USER_EMAIL, TEST_USER_PASSWORD);

        // Čekaj redirect na profile
        loginPage.waitForProfileRedirect();

        assertTrue(driver.getCurrentUrl().contains("/profile"), "Nije redirectano na /profile!");
        System.out.println("✓ Uspješan login, redirect na /profile");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Već prijavljen korisnik vidi welcome poruku")
    void testAlreadyLoggedIn() {
        // Prvo se prijavi
        navigateToFrontend("/login");
        loginPage.login(TEST_USER_EMAIL, TEST_USER_PASSWORD);
        loginPage.waitForProfileRedirect();

        // Vrati se na login
        navigateToFrontend("/login");

        assertTrue(loginPage.isLoggedIn(), "Welcome poruka nije prikazana!");
        System.out.println("✓ Prijavljen korisnik vidi welcome poruku");
    }
}