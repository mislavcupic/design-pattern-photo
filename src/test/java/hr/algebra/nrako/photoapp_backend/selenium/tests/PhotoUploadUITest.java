package hr.algebra.nrako.photoapp_backend.selenium.tests;

import hr.algebra.nrako.photoapp_backend.selenium.BaseSeleniumTest;
import hr.algebra.nrako.photoapp_backend.selenium.pages.LoginPage;
import hr.algebra.nrako.photoapp_backend.selenium.pages.ProfilePage;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UI Test - Photo Upload")
class PhotoUploadUITest extends BaseSeleniumTest {

    private LoginPage loginPage;
    private ProfilePage profilePage;
    private String testImagePath;

    @BeforeEach
    void setUpPages() throws IOException {
        loginPage = new LoginPage(driver);
        profilePage = new ProfilePage(driver);

        // Kreiraj test image file
        testImagePath = createTestImage();

        // Login
        navigateToFrontend("/login");
        loginPage.login(TEST_USER_EMAIL, TEST_USER_PASSWORD);
        loginPage.waitForProfileRedirect();
    }

    @AfterEach
    void cleanUp() {
        // Obriši test image
        if (testImagePath != null) {
            new File(testImagePath).delete();
        }
    }

    private String createTestImage() throws IOException {
        Path tempFile = Files.createTempFile("test-image-", ".jpg");
        Files.write(tempFile, "fake image content".getBytes());
        return tempFile.toAbsolutePath().toString();
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Upload fotografije sa opisom i hashtagovima")
    @Disabled("Requires actual file - enable when needed")
    void testPhotoUpload() {
        int initialPhotoCount = profilePage.getPhotoCount();

        // Upload fotografiju
        profilePage.uploadPhoto(
                testImagePath,
                "Test fotografija iz Selenium testa",
                "#test #selenium #automation"
        );

        // Čekaj da se upload završi
        sleep(5000);

        // Refresh stranicu
        driver.navigate().refresh();
        sleep(2000);

        int newPhotoCount = profilePage.getPhotoCount();
        assertTrue(newPhotoCount > initialPhotoCount, "Fotografija nije uploadana!");

        System.out.println("✓ Fotografija uspješno uploadana");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Upload button se disablea za vrijeme uploada")
    @Disabled("Requires actual file - enable when needed")
    void testUploadButtonDisablesDuringUpload() {
        profilePage.selectFile(testImagePath);
        profilePage.enterDescription("Test");
        profilePage.enterHashtags("#test");
        profilePage.clickUploadButton();

        // Odmah nakon klika, button bi trebao biti disabled ili prikazivati progress
        sleep(500);

        // Provjeri da je upload u tijeku ili završen
        boolean uploadInProgress = profilePage.isUploadInProgress();
        boolean buttonDisabled = !profilePage.isUploadButtonEnabled();

        assertTrue(uploadInProgress || buttonDisabled, "Upload state nije ispravan!");
        System.out.println("✓ Upload state je ispravan");
    }
}