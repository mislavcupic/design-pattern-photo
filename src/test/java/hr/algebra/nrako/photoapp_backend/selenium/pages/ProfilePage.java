package hr.algebra.nrako.photoapp_backend.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import java.util.List;

public class ProfilePage extends BasePage {

    // Profil info
    @FindBy(css = "h3, h4") // Ime korisnika
    private List<WebElement> profileHeaders;

    // Upload forma
    @FindBy(css = "input[type='file']")
    private WebElement fileInput;

    @FindBy(css = "input#formDescription")
    private WebElement descriptionInput;

    @FindBy(css = "input#formHashtags")
    private WebElement hashtagsInput;

    @FindBy(css = "input[type='checkbox']")
    private List<WebElement> checkboxes;

    @FindBy(css = "button.upload-btn")
    private WebElement uploadButton;

    @FindBy(css = ".upload-progress-alert")
    private WebElement uploadProgressAlert;

    // Fotografije
    @FindBy(css = ".photo-card, .card")
    private List<WebElement> photoCards;

    public ProfilePage(WebDriver driver) {
        super(driver);
    }

    public boolean isOnProfilePage() {
        return getCurrentUrl().contains("/profile");
    }

    public boolean isProfileInfoVisible() {
        return !profileHeaders.isEmpty() && profileHeaders.get(0).isDisplayed();
    }

    // Upload metode
    public void selectFile(String absoluteFilePath) {
        fileInput.sendKeys(absoluteFilePath);
    }

    public void enterDescription(String description) {
        enterText(descriptionInput, description);
    }

    public void enterHashtags(String hashtags) {
        enterText(hashtagsInput, hashtags);
    }

    public void setPrivateCheckbox(boolean isPrivate) {
        WebElement privateCheckbox = checkboxes.stream()
                .filter(cb -> cb.getAttribute("type").equals("checkbox"))
                .findFirst()
                .orElseThrow();

        if (privateCheckbox.isSelected() != isPrivate) {
            clickElement(privateCheckbox);
        }
    }

    public void clickUploadButton() {
        clickElement(uploadButton);
    }

    public void uploadPhoto(String filePath, String description, String hashtags) {
        selectFile(filePath);
        enterDescription(description);
        enterHashtags(hashtags);
        clickUploadButton();
    }

    public boolean isUploadButtonEnabled() {
        return uploadButton.isEnabled();
    }

    public boolean isUploadInProgress() {
        return isElementDisplayed(uploadProgressAlert);
    }

    public int getPhotoCount() {
        return photoCards.size();
    }

    public boolean hasPhotos() {
        return !photoCards.isEmpty();
    }
}