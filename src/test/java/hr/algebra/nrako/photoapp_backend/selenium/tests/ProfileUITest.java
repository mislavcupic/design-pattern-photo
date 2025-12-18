package hr.algebra.nrako.photoapp_backend.selenium.tests;

import hr.algebra.nrako.photoapp_backend.selenium.BaseSeleniumTest;
import hr.algebra.nrako.photoapp_backend.selenium.pages.LoginPage;
import hr.algebra.nrako.photoapp_backend.selenium.pages.ProfilePage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UI Test - Profile Page")
class ProfileUITest extends BaseSeleniumTest {

    private LoginPage loginPage;
    private ProfilePage profilePage;

    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(driver);
        profilePage = new ProfilePage(driver);

        // Login prije svakog testa
        navigateToFrontend("/login");
        loginPage.login(TEST_USER_EMAIL, TEST_USER_PASSWORD);
        loginPage.waitForProfileRedirect();
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Profil stranica se učitava")
    void testProfilePageLoads() {
        assertTrue(profilePage.isOnProfilePage(), "Nije na profile stranici!");
        assertTrue(profilePage.isProfileInfoVisible(), "Profil info nije vidljiv!");

        System.out.println("✓ Profile stranica učitana");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Upload forma je prisutna")
    void testUploadFormPresent() {
        assertTrue(profilePage.isOnProfilePage());

        // Upload button bi trebao biti disabled bez filea
        assertFalse(profilePage.isUploadButtonEnabled(), "Upload button bi trebao biti disabled!");

        System.out.println("✓ Upload forma prisutna, button disabled bez filea");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Provjeri fotografije korisnika")
    void testUserPhotos() {
        // Možda korisnik nema fotografije - samo provjeri da nema errora
        int photoCount = profilePage.getPhotoCount();

        System.out.println("✓ Korisnik ima " + photoCount + " fotografija");
        assertTrue(photoCount >= 0, "Photo count ne može biti negativan!");
    }
}
