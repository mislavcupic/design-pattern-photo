package hr.algebra.nrako.photoapp_backend.selenium.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class HomePage extends BasePage {

    @FindBy(css = "nav.navbar")
    private WebElement navbar;

    @FindBy(linkText = "Naslovna")
    private WebElement naslovnaLink;

    @FindBy(linkText = "Profil")
    private WebElement profilLink;

    @FindBy(linkText = "Prijava")
    private WebElement prijavaLink;

    @FindBy(linkText = "Registracija")
    private WebElement registracijaLink;

    @FindBy(linkText = "Obriši račun")
    private WebElement deleteLink;

    @FindBy(linkText = "Odjava")
    private WebElement odjavaLink;

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public boolean isNavbarVisible() {
        return isElementDisplayed(navbar);
    }

    public void clickPrijavaLink() {
        clickElement(prijavaLink);
    }

    public void clickProfilLink() {
        clickElement(profilLink);
    }

    public void clickRegistracijaLink() {
        clickElement(registracijaLink);
    }

    public void clickDeleteLink() {clickElement(deleteLink);}

    public void clickOdjavaLink() {
        clickElement(odjavaLink);
    }

    public boolean isOnHomePage() {
        return getCurrentUrl().endsWith("/") || getCurrentUrl().contains("localhost:3000");
    }
}