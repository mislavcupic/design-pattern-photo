import React, { useEffect, useState } from 'react';
import { auth } from './Firebase'; // Koristimo tvoju Firebase auth instancu
import {
    Button,
    Container,
    Row,
    Col,
    Form,
    Spinner,
    Image as BootstrapImage, // Alias za Image komponentu iz Bootstrapa
    Card,
    ListGroup,
    Badge,
    InputGroup,
    FormControl,
    Alert // Za prikaz grešaka i loadinga
} from 'react-bootstrap';
import { FaStar, FaRegStar } from 'react-icons/fa'; // Importiramo ikone zvjezdica (ako ih koristiš za privatnost)
import './css/ProfilePage.css'; // Dodatni CSS za specifične stilove

const BASE_URL = 'http://localhost:8080';

const ProfilePage = () => {
    // Stanja iz originalne komponente za korisnika i paket
    const [user, setUser] = useState(null);
    const [userPackage, setUserPackage] = useState(null);
    const [uploadsLeft, setUploadsLeft] = useState(0);
    const [nextEligibleChange, setNextEligibleChange] = useState(null);
    const [photos, setPhotos] = useState([]);
    const [isLoading, setIsLoading] = useState(true); // Glavni loading za cijelu stranicu

    // Nova stanja za upload fotografija (integrirana iz prethodnih verzija)
    const [file, setFile] = useState(null); // Ime je promijenjeno iz newPhoto u file radi jasnoće
    const [description, setDescription] = useState('');
    const [hashtags, setHashtags] = useState('');
    const [isPrivate, setIsPrivate] = useState(false); // Checkbox za privatnost

    // Stanja za opcije obrade slike
    const [isResizingEnabled, setIsResizingEnabled] = useState(false);
    const [maxWidth, setMaxWidth] = useState('');
    const [maxHeight, setMaxHeight] = useState('');
    const [outputFormat, setOutputFormat] = useState('');

    // Stanja za loading/error poruke UPLOADA (različito od glavnog isLoading)
    const [uploading, setUploading] = useState(false);
    const [uploadError, setUploadError] = useState(null);

    // Stanja za greške kod promjene paketa
    const [packageChangeError, setPackageChangeError] = useState(null);
    const [changingPackage, setChangingPackage] = useState(false);


    const [selectedPackage, setSelectedPackage] = useState(''); // Za drop-down izbor paketa


    // Tvoja fetchWithAuth funkcija, prilagođena za opće potrebe
    const fetchWithAuth = async (url, options = {}) => {
        try {
            let currentUser = auth.currentUser;

            if (!currentUser) {
                // Ako korisnik nije autentificiran, baci grešku ili preusmjeri
                // window.location.href = '/login'; // Opcionalno preusmjeravanje
                throw new Error("Korisnik nije autentificiran. Molimo prijavite se.");
            }

            const idToken = await currentUser.getIdToken(true); // Dohvati najnoviji ID token

            if (!idToken) {
                throw new Error("Neispravan ID token. Molimo pokušajte ponovo.");
            }

            const response = await fetch(url, {
                ...options,
                headers: {
                    ...options.headers,
                    Authorization: `Bearer ${idToken}`,
                },
            });

            // Handle non-OK responses (4xx, 5xx)
            if (!response.ok) {
                const errorText = await response.text();
                // Pokušaj parsirati JSON ako je odgovor JSON
                try {
                    const errorJson = JSON.parse(errorText);
                    throw new Error(errorJson.message || `Greška: ${response.status} - ${errorText}`);
                } catch (e) {
                    // Ako nije JSON, vrati običan tekst
                    throw new Error(`Greška: ${response.status} - ${errorText}`);
                }
            }

            return response;
        } catch (error) {
            console.error("fetchWithAuth error:", error);
            throw error; // Ponovno baci grešku da je gornji sloj uhvati
        }
    };

    // Glavna funkcija za dohvaćanje svih podataka
    const fetchDataAndUserStatus = async () => {
        try {
            setIsLoading(true);
            const currentUser = auth.currentUser;
            setUser(currentUser); // Postavi Firebase User objekt

            if (!currentUser) {
                // Ako nema currentUsera, zaustavi učitavanje i prikaži poruku
                setIsLoading(false);
                return;
            }

            // Dohvati podatke o paketu
            const userPackageRes = await fetchWithAuth(`${BASE_URL}/user-package/user-package`);
            const userPackageData = await userPackageRes.json();
            setUserPackage(userPackageData);

            // Dohvati preostale uploadove
            const remainingUploadsRes = await fetchWithAuth(`${BASE_URL}/user-package/remaining-uploads`);
            const remainingUploadsData = await remainingUploadsRes.json();
            setUploadsLeft(remainingUploadsData);

            // Dohvati fotografije trenutnog korisnika (sada koristi currentUser.uid)
            const photosRes = await fetchWithAuth(`${BASE_URL}/api/photos/user/${currentUser.uid}`);
            const photosData = await photosRes.json();
            setPhotos(photosData);

            // Dohvati vrijeme sljedeće promjene paketa
            const nextChangeRes = await fetchWithAuth(`${BASE_URL}/user-package/next-eligible-change`);
            const nextChangeData = await nextChangeRes.json();
            setNextEligibleChange(nextChangeData ? new Date(nextChangeData) : null);

        } catch (error) {
            console.error('Greška pri dohvaćanju podataka profila:', error);
            // Postavi grešku za cijelu stranicu ako je problem pri inicijalnom dohvatu
            setUploadError(`Greška pri učitavanju profila: ${error.message}`);
        } finally {
            setIsLoading(false);
        }
    };

    // useEffect za inicijalno dohvaćanje podataka i postavljanje listenera za autentikaciju
    useEffect(() => {
        // Firebase Auth Listener
        const unsubscribe = auth.onAuthStateChanged(user => {
            if (user) {
                setUser(user);
                fetchDataAndUserStatus(); // Dohvati podatke kada se korisnik prijavi
            } else {
                setUser(null);
                setIsLoading(false); // Ako nema korisnika, završi loading
                //setUploadError("Niste prijavljeni. Molimo prijavite se za pregled profila.");
            }
        });

        // Cleanup function za listener
        return () => unsubscribe();
    }, []); // Prazan array znači da se pokreće samo jednom pri montiranju komponente


    // Funkcija za promjenu paketa (iz originala)
    const handleChangePackage = async () => {
        if (!selectedPackage) {
            setPackageChangeError("Molimo odaberite paket.");
            return;
        }
        setPackageChangeError(null);
        setChangingPackage(true); // Aktiviraj loading za promjenu paketa

        try {
            console.log("Mijenjam paket u:", selectedPackage);
            await fetchWithAuth(`${BASE_URL}/user-package/change-package`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(selectedPackage), // Šalji samo odabrani string paketa
            });

            alert(`Paket uspješno promijenjen u ${selectedPackage}!`);
            // Ponovno dohvati sve podatke nakon uspješne promjene
            await fetchDataAndUserStatus();
            setSelectedPackage(''); // Resetiraj odabrani paket u drop-downu

        } catch (error) {
            console.error("Greška prilikom promjene paketa:", error);
            setPackageChangeError(`Greška prilikom promjene paketa: ${error.message}`);
        } finally {
            setChangingPackage(false); // Deaktiviraj loading
        }
    };

    // Funkcija za upload fotografije (integrirana nova logika)
    const handleUploadPhoto = async (e) => { // Proslijedi event
        e.preventDefault(); // Spriječi defaultno ponašanje forme
        setUploadError(null); // Resetiraj error za upload
        setUploading(true);

        if (!file) {
            setUploadError('Molimo odaberite datoteku.');
            setUploading(false);
            return;
        }

        if (isResizingEnabled) {
            const parsedMaxWidth = parseInt(maxWidth);
            const parsedMaxHeight = parseInt(maxHeight);

            if (
                (maxWidth && (isNaN(parsedMaxWidth) || parsedMaxWidth <= 0)) ||
                (maxHeight && (isNaN(parsedMaxHeight) || parsedMaxHeight <= 0))
            ) {
                setUploadError('Maksimalna širina i visina moraju biti pozitivni brojevi.');
                setUploading(false);
                return;
            }
        }

        const formData = new FormData();
        formData.append('file', file);
        formData.append('description', description);
        formData.append('hashtags', hashtags);
        formData.append('uid', user.uid); // Firebase UID je ključan
        formData.append('isPrivate', isPrivate);

        if (isResizingEnabled) {
            if (maxWidth) formData.append('maxWidth', maxWidth);
            if (maxHeight) formData.append('maxHeight', maxHeight);
        }
        if (outputFormat) {
            formData.append('outputFormat', outputFormat);
        }

        try {
            await fetchWithAuth(`${BASE_URL}/api/photos/upload`, {
                method: 'POST',
                body: formData, // fetch automatski postavlja Content-Type za FormData
            });

            alert('Fotografija uspješno učitana!');
            // Osvježi sve podatke nakon uploada
            await fetchDataAndUserStatus();

            // Resetiraj formu za upload
            setFile(null);
            setDescription('');
            setHashtags('');
            setIsPrivate(false);
            setIsResizingEnabled(false);
            setMaxWidth('');
            setMaxHeight('');
            setOutputFormat('');

            // Resetiraj input type="file" element, ako je potrebno
            // const fileInput = document.getElementById('formFile'); // Koristi ID iz forme
            // if (fileInput) fileInput.value = '';

        } catch (error) {
            console.error('Upload error:', error);
            setUploadError(`Greška kod uploada: ${error.message}`);
        } finally {
            setUploading(false);
        }
    };

    // Funkcija za brisanje fotografije (iz originala)
    const handleDeletePhoto = async (photoId) => {
        if (!window.confirm("Jeste li sigurni da želite obrisati ovu fotografiju?")) {
            return;
        }
        try {
            await fetchWithAuth(`${BASE_URL}/api/photos/${photoId}`, {
                method: 'DELETE',
            });
            alert('Fotografija uspješno obrisana!');
            // Osvježi sve podatke nakon brisanja
            await fetchDataAndUserStatus();
        } catch (error) {
            console.error('Greška prilikom brisanja fotografije:', error);
            alert(`Greška prilikom brisanja fotografije: ${error.message}`);
        }
    };

    // Funkcija za prebacivanje statusa privatnosti fotografije (iz originala)
    const handleTogglePrivacy = async (photoId, currentIsPrivate) => {
        try {
            // PUT zahtjev na backend endpoint za promjenu privatnosti
            await fetchWithAuth(`${BASE_URL}/api/photos/${photoId}/toggle-privacy`, {
                method: 'PUT',
            });
            alert(`Fotografija je sada ${currentIsPrivate ? 'javna' : 'privatna'}!`);
            // Ako je uspješno, osvježi listu fotografija
            await fetchDataAndUserStatus(); // Ponovno dohvati fotografije
        } catch (error) {
            console.error('Greška prilikom promjene privatnosti fotografije:', error);
            alert(`Greška: ${error.message}`);
        }
    };

    const getUserName = () => {
        return user?.displayName || "Anonimni Korisnik";
    };

    // Glavni loading spinner za cijelu stranicu
    if (isLoading) {
        return (
            <Container className="my-5 text-center">
                <Spinner animation="border" role="status">
                    <span className="visually-hidden">Učitavanje...</span>
                </Spinner>
                <p className="mt-3">Učitavanje korisničkih podataka...</p>
            </Container>
        );
    }

    // Poruka ako korisnik nije prijavljen (nakon što je isLoading završio)
    if (!user) {
        return (
            <Container className="my-5">
                <Alert variant="info" className="text-center">
                    <Alert.Heading>Niste prijavljeni!</Alert.Heading>
                    <p className="mb-0">Molimo prijavite se za pristup svom profilu i funkcionalnostima.</p>
                </Alert>
            </Container>
        );
    }


    return (
        <Container className="profile-container my-5">
            <Row className="justify-content-md-center">
                <Col md={8}>
                    {/* Sekcija korisničkog profila i paketa */}
                    <Card className="user-card shadow-sm mb-4">
                        <Card.Body className="p-4">
                            <div className="d-flex align-items-center mb-3">
                                <div className="profile-icon rounded-circle bg-primary text-white d-flex align-items-center justify-content-center me-3">
                                    {getUserName().charAt(0).toUpperCase()}
                                </div>
                                <div>
                                    <Card.Title className="mb-1 profile-name-text">{getUserName()}</Card.Title>
                                    <Card.Subtitle className="text-muted profile-subtitle-text">{user?.email || 'Nema emaila'}</Card.Subtitle>
                                </div>
                            </div>
                            <ListGroup variant="flush">
                                <ListGroup.Item className="package-info-item">
                                    Paket: <Badge pill bg={userPackage === 'FREE' ? 'secondary' : (userPackage === 'PRO' ? 'success' : 'info')} className="package-badge">{userPackage}</Badge>
                                </ListGroup.Item>
                                <ListGroup.Item className="upload-info-item">
                                    Preostali uploadovi: <Badge pill bg="info" className="uploads-badge">{uploadsLeft}</Badge>
                                </ListGroup.Item>
                                {nextEligibleChange && (
                                    <ListGroup.Item className="change-date-item">
                                        Možete ponovno promijeniti paket: <Badge pill bg="warning" className="change-date-badge">{nextEligibleChange.toLocaleString()}</Badge>
                                    </ListGroup.Item>
                                )}
                            </ListGroup>
                            <Form.Group className="mt-3">
                                <Form.Label className="form-label-custom">Odaberite novi paket</Form.Label>
                                <Form.Select
                                    value={selectedPackage}
                                    onChange={(e) => setSelectedPackage(e.target.value)}
                                    className="form-select-custom"
                                    disabled={changingPackage}
                                >
                                    <option value="">-- Odaberite --</option>
                                    {['FREE', 'PRO', 'GOLD'].filter(pkg => pkg !== userPackage).map(pkg => (
                                        <option key={pkg} value={pkg}>{pkg}</option>
                                    ))}
                                </Form.Select>
                                {packageChangeError && <Alert variant="danger" className="mt-2">{packageChangeError}</Alert>}
                                <Button
                                    variant="outline-secondary"
                                    className="mt-2 w-100 package-change-btn"
                                    disabled={!selectedPackage || changingPackage || (nextEligibleChange && new Date() < new Date(nextEligibleChange))}
                                    onClick={handleChangePackage} // Poziva handleChangePackage bez argumenta
                                >
                                    {changingPackage ? (
                                        <>
                                            <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2" />
                                            Mijenjam paket...
                                        </>
                                    ) : 'Promijeni paket'}
                                </Button>
                            </Form.Group>
                        </Card.Body>
                    </Card>

                    {/* Sekcija za upload nove fotografije */}
                    <Card className="upload-card shadow-sm mb-4">
                        <Card.Body>
                            <Card.Title className="mb-3 upload-card-title text-center">Učitaj Novu Fotografiju</Card.Title>
                            <Form onSubmit={handleUploadPhoto}> {/* Sada je onSubmit na Form elementu */}
                                {uploadError && <Alert variant="danger">{uploadError}</Alert>}
                                {uploading && (
                                    <Alert variant="info" className="d-flex align-items-center">
                                        <Spinner animation="border" size="sm" className="me-2" />
                                        Učitavam fotografiju...
                                    </Alert>
                                )}

                                <Form.Group controlId="formFile" className="mb-3">
                                    <Form.Label className="form-label-custom">Odaberite fotografiju</Form.Label>
                                    <Form.Control
                                        type="file"
                                        onChange={(e) => setFile(e.target.files[0])}
                                        required // Obavezno polje
                                        disabled={uploading}
                                    />
                                </Form.Group>

                                <Form.Group controlId="formDescription" className="mb-3">
                                    <Form.Label className="form-label-custom">Opis</Form.Label>
                                    <Form.Control
                                        type="text"
                                        value={description}
                                        onChange={(e) => setDescription(e.target.value)}
                                        placeholder="Dodajte opis fotografije"
                                        disabled={uploading}
                                    />
                                </Form.Group>

                                <Form.Group controlId="formHashtags" className="mb-3">
                                    <Form.Label className="form-label-custom">Hashtags</Form.Label>
                                    <Form.Control
                                        type="text"
                                        value={hashtags}
                                        onChange={(e) => setHashtags(e.target.value)}
                                        placeholder="Unesite hashtagove (odvojene zarezom)"
                                        disabled={uploading}
                                    />
                                </Form.Group>

                                {/* Dodana kontrola za privatnost */}
                                <Form.Group className="mb-3">
                                    <Form.Check
                                        type="checkbox"
                                        label="Privatna fotografija (vidljiva samo vama)"
                                        checked={isPrivate}
                                        onChange={(e) => setIsPrivate(e.target.checked)}
                                        className="private-checkbox"
                                        disabled={uploading}
                                    />
                                </Form.Group>

                                {/* OPCIJE OBRADE SLIKE (iz PhotoUploadForm) */}
                                <h4 className="mt-4 mb-3 text-center">Opcije obrade slike (prije uploada):</h4>

                                <Form.Group controlId="formResize" className="mb-3">
                                    <Form.Check
                                        type="checkbox"
                                        label="Promijeni veličinu (Resize)"
                                        checked={isResizingEnabled}
                                        onChange={(e) => setIsResizingEnabled(e.target.checked)}
                                        disabled={uploading}
                                    />
                                </Form.Group>

                                {isResizingEnabled && (
                                    <Row className="mb-3">
                                        <Col md={6}>
                                            <InputGroup className="mb-2">
                                                <InputGroup.Text>Maks. Širina (px)</InputGroup.Text>
                                                <FormControl
                                                    type="number"
                                                    value={maxWidth}
                                                    onChange={(e) => setMaxWidth(e.target.value)}
                                                    min="1"
                                                    placeholder="npr. 800"
                                                    disabled={uploading}
                                                />
                                            </InputGroup>
                                        </Col>
                                        <Col md={6}>
                                            <InputGroup className="mb-2">
                                                <InputGroup.Text>Maks. Visina (px)</InputGroup.Text>
                                                <FormControl
                                                    type="number"
                                                    value={maxHeight}
                                                    onChange={(e) => setMaxHeight(e.target.value)}
                                                    min="1"
                                                    placeholder="npr. 600"
                                                    disabled={uploading}
                                                />
                                            </InputGroup>
                                        </Col>
                                    </Row>
                                )}

                                <Form.Group controlId="formOutputFormat" className="mb-4">
                                    <Form.Label>Izlazni format:</Form.Label>
                                    <Form.Select
                                        value={outputFormat}
                                        onChange={(e) => setOutputFormat(e.target.value)}
                                        disabled={uploading}
                                    >
                                        <option value="">Original</option>
                                        <option value="png">PNG</option>
                                        <option value="jpeg">JPG</option>
                                        <option value="bmp">BMP</option>
                                    </Form.Select>
                                </Form.Group>

                                <Button variant="primary" type="submit" disabled={!file || uploading || uploadsLeft <= 0} className="w-100 upload-btn">
                                    {uploading ? (
                                        <>
                                            <Spinner as="span" animation="grow" size="sm" role="status" aria-hidden="true" />
                                            Učitavam...
                                        </>
                                    ) : (uploadsLeft <= 0 ? 'Nema preostalih uploadova' : 'Upload')}
                                </Button>
                                {uploadsLeft <= 0 && <Alert variant="warning" className="mt-2 text-center">Nadogradite paket za više uploadova!</Alert>}

                            </Form>
                        </Card.Body>
                    </Card>

                    {/* Sekcija za prikaz korisničkih fotografija */}
                    <Card className="gallery-card shadow-sm mb-4">
                        <Card.Body>
                            <Card.Title className="mb-3 gallery-card-title text-center">Vaše fotografije</Card.Title>
                            <Row xs={1} md={2} lg={3} className="g-3">
                                {photos.length === 0 ? (
                                    <Col xs={12}><p className="text-muted text-center">Još nema uploadanih fotografija. Budite prvi!</p></Col>
                                ) : (
                                    photos.map((photoData) => (
                                        <Col key={photoData.id}>
                                            <Card className="photo-item h-100">
                                                {photoData.fileUrl ? (
                                                    <BootstrapImage
                                                        src={photoData.fileUrl}
                                                        alt={photoData.description}
                                                        className="card-img-top"
                                                        style={{ objectFit: 'cover', height: '150px' }}
                                                    />
                                                ) : (
                                                    <div className="d-flex align-items-center justify-content-center bg-light" style={{ height: '150px' }}>
                                                        <p className="text-muted m-0">URL nedostupan</p>
                                                    </div>
                                                )}
                                                <Card.Body className="d-flex flex-column justify-content-between">
                                                    <div>
                                                        <Card.Text className="small text-muted mb-1">{photoData.description}</Card.Text>
                                                        {photoData.hashtags && (
                                                            <div className="mb-2">
                                                                {photoData.hashtags.split(',').map((tag, index) => (
                                                                    <Badge key={index} pill bg="light" text="dark" className="me-1 mb-1">#{tag.trim()}</Badge>
                                                                ))}
                                                            </div>
                                                        )}
                                                    </div>
                                                    <div className="d-flex justify-content-between align-items-center mt-auto">
                                                        <Button
                                                            variant="link"
                                                            className="p-0"
                                                            onClick={() => handleTogglePrivacy(photoData.id, photoData.isPrivate)}
                                                            title={photoData.isPrivate ? 'Privatna (klikni za javno)' : 'Javna (klikni za privatno)'}
                                                        >
                                                            {photoData.isPrivate ? (
                                                                <FaRegStar className="text-warning" size={20} /> // Prazna zvjezdica za privatno
                                                            ) : (
                                                                <FaStar className="text-warning" size={20} /> // Puna zvjezdica za javno
                                                            )}
                                                        </Button>
                                                        <Button variant="outline-danger" size="sm" onClick={() => handleDeletePhoto(photoData.id)}>
                                                            Obriši
                                                        </Button>
                                                    </div>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    ))
                                )}
                            </Row>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default ProfilePage;