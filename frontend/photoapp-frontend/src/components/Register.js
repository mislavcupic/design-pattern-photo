import React, { useState } from "react";
import { useAuth } from "../context/AuthContext"; // Importaj AuthContext da bi koristio prijavu
import { Form, Button, Container, Row, Col, Alert, Card, Spinner } from "react-bootstrap"; // Dodan Card i Spinner
import { getAuth, fetchSignInMethodsForEmail } from "firebase/auth"; // Firebase auth za provjeru korisnika
import { FaUserPlus, FaGoogle, FaGithub, FaEnvelope, FaExclamationCircle } from "react-icons/fa"; // Dodane ikone
import './css/Register.css'; // Importiramo novi CSS

function Register() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [userPackage, setUserPackage] = useState("FREE");
    const [errorMessage, setErrorMessage] = useState(""); // Za pohranu poruka o grešci
    const [isLoading, setIsLoading] = useState(false); // Za kontrolu učitavanja

    const { registerWithEmail, loginWithGoogle, loginWithGitHub } = useAuth(); // Importaj funkcije iz AuthContext-a

    const handleRegister = async () => {
        setErrorMessage("");
        setIsLoading(true);

        try {
            const auth = getAuth();
            const signInMethods = await fetchSignInMethodsForEmail(auth, email);

            if (signInMethods.length > 0) {
                setErrorMessage("Korisnik s tim emailom već postoji. Molimo prijavite se ili povežite račun.");
                setIsLoading(false);
                return;
            }

            await registerWithEmail(email, password, displayName);
            console.log("✅ Registracija uspješna");

            // Ovdje bi obično preusmjerio korisnika na neku drugu stranicu, npr. profil
            // navigate("/profile"); // Primjer, ovisno o tvojoj ruti
            alert("Registracija uspješna!"); // Privremena poruka
        } catch (error) {
            console.error("❌ Greška pri registraciji:", error);
            // Bolje greške za korisnika
            if (error.code === 'auth/weak-password') {
                setErrorMessage("Lozinka mora biti barem 6 znakova duga.");
            } else if (error.code === 'auth/invalid-email') {
                setErrorMessage("Neispravan format emaila.");
            } else {
                setErrorMessage("Došlo je do greške pri registraciji. Molimo pokušajte ponovno.");
            }
        } finally {
            setIsLoading(false);
        }
    };

    const handleGoogleLogin = async () => {
        setErrorMessage("");
        setIsLoading(true);
        try {
            await loginWithGoogle();
            console.log("✅ Prijava putem Googlea uspješna");
            // navigate("/profile"); // Primjer
        } catch (error) {
            console.error("❌ Greška pri Google prijavi:", error);
            setErrorMessage("Greška pri prijavi putem Googlea. Molimo pokušajte ponovno.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleGitHubLogin = async () => {
        setErrorMessage("");
        setIsLoading(true);
        try {
            await loginWithGitHub();
            console.log("✅ Prijava putem GitHub-a uspješna");
            // navigate("/profile"); // Primjer
        } catch (error) {
            console.error("❌ Greška pri GitHub prijavi:", error);
            setErrorMessage("Greška pri prijavi putem GitHub-a. Molimo pokušajte ponovno.");
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Container fluid className="register-container">
            <Row className="justify-content-center align-items-center h-100 flex-grow-1">
                <Col xs={10} sm={8} md={6} lg={4}>
                    <Card className="register-card shadow-lg border-0">
                        <Card.Body className="p-4 p-md-5">
                            <Card.Title className="text-center mb-4 register-title">
                                <FaUserPlus className="me-2 text-primary" /> Registracija
                            </Card.Title>

                            {errorMessage && (
                                <Alert variant="danger" className="shake-animation register-alert">
                                    <FaExclamationCircle className="me-2" />{errorMessage}
                                </Alert>
                            )}

                            <Form>
                                {/* Display Name */}
                                <Form.Group controlId="formDisplayName" className="mb-3">
                                    <Form.Label className="form-label-custom">Ime i prezime</Form.Label>
                                    <Form.Control
                                        type="text"
                                        placeholder="Unesite vaše ime i prezime"
                                        value={displayName}
                                        onChange={(e) => setDisplayName(e.target.value)}
                                        className="form-control-custom"
                                        disabled={isLoading}
                                    />
                                </Form.Group>

                                {/* Email */}
                                <Form.Group controlId="formEmail" className="mb-3">
                                    <Form.Label className="form-label-custom">Email</Form.Label>
                                    <Form.Control
                                        type="email"
                                        placeholder="vas.email@primjer.com"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                        className="form-control-custom"
                                        disabled={isLoading}
                                    />
                                </Form.Group>

                                {/* Password */}
                                <Form.Group controlId="formPassword" className="mb-4">
                                    <Form.Label className="form-label-custom">Lozinka</Form.Label>
                                    <Form.Control
                                        type="password"
                                        placeholder="••••••••"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        className="form-control-custom"
                                        disabled={isLoading}
                                    />
                                </Form.Group>

                                {/* User Package */}
                                <Form.Group controlId="formUserPackage" className="mb-4">
                                    <Form.Label className="form-label-custom">Odaberi paket</Form.Label>
                                    <Form.Select
                                        value={userPackage}
                                        onChange={(e) => setUserPackage(e.target.value)}
                                        className="form-control-custom" // Koristi isti stil kao inputi
                                        disabled={isLoading}
                                    >
                                        <option value="FREE">FREE</option>
                                        <option value="PRO">PRO</option>
                                        <option value="GOLD">GOLD</option>
                                    </Form.Select>
                                </Form.Group>

                                {/* Register Button */}
                                <Button
                                    variant="outline-primary" // Promijenjeno na outline
                                    onClick={handleRegister}
                                    className="w-100 register-btn mb-3"
                                    disabled={isLoading || !email || !password || !displayName}
                                >
                                    {isLoading ? (
                                        <>
                                            <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2" />
                                            Registriram se...
                                        </>
                                    ) : (
                                        <>
                                            <FaEnvelope className="me-2" /> Registracija putem emaila
                                        </>
                                    )}
                                </Button>
                            </Form>

                            {/* Social Login Buttons */}
                            <div className="mt-4 text-center social-login-buttons">
                                <p className="social-login-text">Ili se registrirajte putem:</p>
                                <Button
                                    variant="outline-danger" // Promijenjeno na outline
                                    onClick={handleGoogleLogin}
                                    className="w-100 social-btn google-btn mb-3"
                                    disabled={isLoading}
                                >
                                    {isLoading ? (
                                        <Spinner as="span" animation="border" size="sm" />
                                    ) : (
                                        <>
                                            <FaGoogle className="me-2" /> Google
                                        </>
                                    )}
                                </Button>
                                <Button
                                    variant="outline-dark" // Promijenjeno na outline
                                    onClick={handleGitHubLogin}
                                    className="w-100 social-btn github-btn"
                                    disabled={isLoading}
                                >
                                    {isLoading ? (
                                        <Spinner as="span" animation="border" size="sm" />
                                    ) : (
                                        <>
                                            <FaGithub className="me-2" /> GitHub
                                        </>
                                    )}
                                </Button>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
}

export default Register;