import React, { useState, useEffect } from 'react';
import {
    Container,
    Row,
    Col,
    Card,
    Spinner,
    Alert,
    Form,
    Button,
    InputGroup,
    FormControl
} from 'react-bootstrap';
import { auth } from './Firebase'; // Pretpostavka da je Firebase auth dostupan
import { FaSearch, FaTimes, FaSpinner, FaInfoCircle, FaImage } from 'react-icons/fa'; // Dodane ikone
import './css/HomePage.css'; // Dodajemo novi CSS file za HomePage

function HomePage() {
    const [photos, setPhotos] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const [searchTerm, setSearchTerm] = useState('');
    const [authorUid, setAuthorUid] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [hasSearched, setHasSearched] = useState(false);
    const [searchLoading, setSearchLoading] = useState(false);
    const [searchError, setSearchError] = useState(null);

    const fetchWithAuth = async (url, options = {}) => {
        let headers = { ...options.headers };
        try {
            const currentUser = auth.currentUser;
            if (currentUser) {
                const idToken = await currentUser.getIdToken(true);
                headers['Authorization'] = `Bearer ${idToken}`;
            }
        } catch (tokenError) {
            console.warn("Nema ID tokena, nastavljam bez autentifikacije za javne rute:", tokenError);
        }

        const response = await fetch(url, { ...options, headers });
        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP error! status: ${response.status} - ${errorText}`);
        }
        return response.json();
    };

    const fetchPublicPhotos = async () => {
        try {
            setLoading(true);
            setError(null);
            const data = await fetchWithAuth('http://localhost:8080/api/photos/public');
            setPhotos(data);
        } catch (err) {
            console.error("Greška pri dohvatu fotografija za homepage:", err);
            setError(err);
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = async (e) => {
        e.preventDefault();
        setSearchLoading(true);
        setSearchError(null);
        setHasSearched(true);

        try {
            const queryParams = new URLSearchParams();
            if (searchTerm) {
                queryParams.append('searchTerm', searchTerm);
            }
            if (authorUid) {
                queryParams.append('uploadedBy', authorUid);
            }

            const data = await fetchWithAuth(`http://localhost:8080/api/photos/search?${queryParams.toString()}`);
            setSearchResults(data);
        } catch (err) {
            console.error('Greška pri pretrazi fotografija:', err);
            setSearchError(`Došlo je do greške prilikom pretrage: ${err.message}`);
            setSearchResults([]);
        } finally {
            setSearchLoading(false);
        }
    };

    const handleClearSearch = () => {
        setSearchTerm('');
        setAuthorUid('');
        setSearchResults([]);
        setHasSearched(false);
        setSearchError(null);
        fetchPublicPhotos();
    };

    useEffect(() => {
        fetchPublicPhotos();
    }, []);

    const photosToDisplay = hasSearched ? searchResults : photos;

    return (
        <Container fluid className="homepage-container"> {/* Promjena na fluid container */}
            <h1 className="section-heading text-center mb-5">
                <FaImage className="me-2 text-primary" /> Javne fotografije
            </h1>

            <Card className="search-card shadow-lg mb-5"> {/* Dodane klase za stiliziranje */}
                <Card.Body className="p-4 p-md-5">
                    <Card.Title className="text-center mb-4 search-card-title">
                        <FaSearch className="me-2 text-primary" /> Pretraži fotografije
                    </Card.Title>
                    <Form onSubmit={handleSearch}>
                        <Form.Group className="mb-3">
                            <InputGroup>
                                <FormControl
                                    type="text"
                                    placeholder="Pretraži po opisu ili hashtagovima..."
                                    value={searchTerm}
                                    onChange={(e) => setSearchTerm(e.target.value)}
                                    disabled={searchLoading}
                                    className="search-input"
                                />
                            </InputGroup>
                            <Form.Text className="text-muted">
                                Unesite ključne riječi za pretragu opisa ili hashtagova.
                            </Form.Text>
                        </Form.Group>

                        <Form.Group className="mb-4">
                            <InputGroup>
                                <FormControl
                                    type="text"
                                    placeholder="Pretraži po UID-u autora (opcionalno)..."
                                    value={authorUid}
                                    onChange={(e) => setAuthorUid(e.target.value)}
                                    disabled={searchLoading}
                                    className="search-input"
                                />
                            </InputGroup>
                            <Form.Text className="text-muted">
                                Opcionalno, filtrirajte fotografije specifičnog autora.
                            </Form.Text>
                        </Form.Group>

                        {searchError && (
                            <Alert variant="danger" className="mb-4 search-alert shake-animation">
                                {searchError}
                            </Alert>
                        )}

                        <div className="d-grid gap-3"> {/* Povećan gap za gumbe */}
                            <Button
                                variant="primary"
                                type="submit"
                                disabled={searchLoading || (!searchTerm && !authorUid)}
                                className="search-btn"
                            >
                                {searchLoading ? (
                                    <>
                                        <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2" />
                                        Pretražujem...
                                    </>
                                ) : (
                                    <>
                                        <FaSearch className="me-2" /> Pretraži
                                    </>
                                )}
                            </Button>
                            {hasSearched && (
                                <Button
                                    variant="outline-secondary"
                                    onClick={handleClearSearch}
                                    disabled={searchLoading}
                                    className="clear-search-btn"
                                >
                                    <FaTimes className="me-2" /> Poništi pretragu
                                </Button>
                            )}
                        </div>
                    </Form>
                </Card.Body>
            </Card>

            {(loading || searchLoading) && (
                <div className="d-flex flex-column justify-content-center align-items-center loading-spinner-section">
                    <Spinner animation="border" role="status" className="loading-spinner" />
                    <p className="loading-text mt-3">Učitavanje fotografija...</p>
                </div>
            )}

            {(error || searchError) && (
                <Alert variant="danger" className="text-center alert-message shake-animation">
                    <FaInfoCircle className="me-2" /> Došlo je do greške prilikom učitavanja/pretrage fotografija: {(error || searchError).message}
                </Alert>
            )}

            {!loading && !error && !searchLoading && !searchError && photosToDisplay.length === 0 && (
                <Alert variant="info" className="text-center alert-message">
                    <FaInfoCircle className="me-2" /> {hasSearched ? 'Nema pronađenih fotografija za ovu pretragu.' : 'Trenutno nema javnih fotografija za prikaz.'}
                </Alert>
            )}

            {!loading && !error && !searchLoading && !searchError && photosToDisplay.length > 0 && (
                <Row xs={1} md={2} lg={3} className="g-4 public-photo-grid"> {/* Dodana klasa za grid */}
                    {photosToDisplay.map(photo => (
                        <Col key={photo.id}>
                            <Card className="h-100 public-photo-card shadow-sm"> {/* Dodana klasa i shadow */}
                                <Card.Img
                                    variant="top"
                                    src={photo.fileUrl}
                                    alt={photo.description}
                                    className="public-photo-img"
                                />
                                <Card.Body>
                                    <Card.Title className="photo-card-title">{photo.description}</Card.Title>
                                    <Card.Text className="photo-card-hashtags">
                                        #{photo.hashtags}
                                    </Card.Text>
                                    <Card.Text className="photo-card-author">
                                        Postavio: <strong>{photo.uploadedBy}</strong>
                                    </Card.Text>
                                </Card.Body>
                                <Card.Footer className="text-muted photo-card-footer">
                                    Objavljeno: {new Date(photo.uploadDate._seconds * 1000).toLocaleDateString()}
                                </Card.Footer>
                            </Card>
                        </Col>
                    ))}
                </Row>
            )}
        </Container>
    );
}

export default HomePage;