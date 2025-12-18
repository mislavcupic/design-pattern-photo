package hr.algebra.nrako.photoapp_backend.selenium.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class LoginPage extends BasePage {

    @FindBy(css = "input[type='email']")
    private WebElement emailInput;

    @FindBy(css = "input[type='password']")
    private WebElement passwordInput;

    @FindBy(css = "button.login-btn")
    private WebElement loginButton;

    @FindBy(css = ".login-alert")
    private WebElement errorAlert;

    @FindBy(css = ".welcome-text")
    private WebElement welcomeMessage;

    @FindBy(css = "button.logout-btn")
    private WebElement logoutButton;

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public void enterEmail(String email) {
        enterText(emailInput, email);
    }

    public void enterPassword(String password) {
        enterText(passwordInput, password);
    }

    public void clickLoginButton() {
        clickElement(loginButton);
    }

    public void login(String email, String password) {
        enterEmail(email);
        enterPassword(password);
        clickLoginButton();
    }

    public boolean isErrorDisplayed() {
        return isElementDisplayed(errorAlert);
    }

    public String getErrorMessage() {
        waitForElementToBeVisible(errorAlert);
        return errorAlert.getText();
    }

    public boolean isLoggedIn() {
        return isElementDisplayed(welcomeMessage);
    }

    public boolean isLoginButtonEnabled() {
        return loginButton.isEnabled();
    }

    public void waitForProfileRedirect() {
        waitForUrl("/profile");
    }
}