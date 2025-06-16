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
    FormControl,
    Modal
} from 'react-bootstrap';
import { auth } from './Firebase';
import { FaSearch, FaTimes, FaSpinner, FaInfoCircle, FaImage, FaTrash, FaDownload, FaEdit, FaEyeSlash, FaEye } from 'react-icons/fa';
import './css/HomePage.css';

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

    const [currentUserUid, setCurrentUserUid] = useState(null);

    const [showEditModal, setShowEditModal] = useState(false);
    const [currentPhotoToEdit, setCurrentPhotoToEdit] = useState(null);
    const [editDescription, setEditDescription] = useState('');
    const [editHashtags, setEditHashtags] = useState('');
    const [editIsPrivate, setEditIsPrivate] = useState(false);

    // --- NOVO STANJE ZA VELIKU FOTOGRAFIJU ---
    const [showPhotoModal, setShowPhotoModal] = useState(false);
    const [selectedPhotoUrl, setSelectedPhotoUrl] = useState('');
    const [selectedPhotoDescription, setSelectedPhotoDescription] = useState('');
    // ------------------------------------------

    useEffect(() => {
        const unsubscribe = auth.onAuthStateChanged(user => {
            if (user) {
                setCurrentUserUid(user.uid);
            } else {
                setCurrentUserUid(null);
            }
        });
        return () => unsubscribe();
    }, []);

    const fetchWithAuth = async (url, options = {}) => {
        let headers = { ...options.headers };
        try {
            const currentUser = auth.currentUser;
            if (currentUser) {
                const idToken = await currentUser.getIdToken(true);
                headers['Authorization'] = `Bearer ${idToken}`;
            }
        } catch (tokenError) {
            console.warn("Nema ID tokena ili greška pri dohvatu tokena:", tokenError);
        }

        const response = await fetch(url, { ...options, headers });
        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP error! status: ${response.status} - ${errorText}`);
        }

        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return response.json();
        }
        return response;
    };

    const fetchLast10PublicPhotos = async () => {
        try {
            setLoading(true);
            setError(null);
            const data = await fetchWithAuth('http://localhost:8080/api/photos/last10');
            setPhotos(data);
        } catch (err) {
            console.error("Greška pri dohvatu zadnjih 10 fotografija za homepage:", err);
            setError(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchLast10PublicPhotos();
    }, []);

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
        fetchLast10PublicPhotos();
    };

    const handleDeletePhoto = async (photoId) => {
        if (!window.confirm('Jeste li sigurni da želite obrisati ovu fotografiju?')) {
            return;
        }
        try {
            await fetchWithAuth(`http://localhost:8080/api/photos/${photoId}`, {
                method: 'DELETE',
            });
            alert('Fotografija uspješno obrisana!');
            if (hasSearched) {
                handleSearch({ preventDefault: () => {} });
            } else {
                fetchLast10PublicPhotos();
            }
        } catch (err) {
            console.error('Greška pri brisanju fotografije:', err);
            alert(`Greška prilikom brisanja fotografije: ${err.message}`);
        }
    };

    const handleDownloadPhoto = async (photoId, filename) => {
        try {
            const queryParams = new URLSearchParams({
                outputFormat: 'jpeg'
            }).toString();

            const response = await fetchWithAuth(`http://localhost:8080/api/photos/${photoId}/download?${queryParams}`, {
                method: 'GET',
            });

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;

            const contentDisposition = response.headers.get('Content-Disposition');
            let downloadFilename = `photo_${photoId}.jpeg`;
            if (contentDisposition && contentDisposition.includes('filename=')) {
                const filenameMatch = /filename\*?=['"]?(?:UTF-8'')?([^;"\n\r]+)['"]?/.exec(contentDisposition);
                if (filenameMatch && filenameMatch[1]) {
                    downloadFilename = decodeURIComponent(filenameMatch[1]);
                }
            } else if (filename) {
                downloadFilename = filename.substring(filename.lastIndexOf('/') + 1);
            }
            a.download = downloadFilename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
            alert('Fotografija uspješno preuzeta!');
        } catch (err) {
            console.error('Greška pri preuzimanju fotografije:', err);
            alert(`Greška prilikom preuzimanja fotografije: ${err.message}`);
        }
    };

    const handleEditClick = (photo) => {
        setCurrentPhotoToEdit(photo);
        setEditDescription(photo.description || '');
        setEditHashtags(formatHashtagsForEdit(photo.hashtags));
        setEditIsPrivate(photo.isPrivate !== undefined ? photo.isPrivate : false);
        setShowEditModal(true);
    };

    const handleUpdatePhoto = async (e) => {
        e.preventDefault();
        if (!currentPhotoToEdit) return;

        try {
            const queryParams = new URLSearchParams({
                description: editDescription,
                hashtags: editHashtags,
                isPrivate: editIsPrivate
            }).toString();

            await fetchWithAuth(`http://localhost:8080/api/photos/${currentPhotoToEdit.id}?${queryParams}`, {
                method: 'PUT',
            });
            alert('Metapodaci fotografije uspješno ažurirani!');
            setShowEditModal(false);
            if (hasSearched) {
                handleSearch({ preventDefault: () => {} });
            } else {
                fetchLast10PublicPhotos();
            }
        } catch (err) {
            console.error('Greška pri ažuriranju fotografije:', err);
            alert(`Greška prilikom ažuriranja fotografije: ${err.message}`);
        }
    };

    const handleTogglePrivacy = async (photoId, currentIsPrivate) => {
        try {
            await fetchWithAuth(`http://localhost:8080/api/photos/${photoId}/toggle-privacy`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
            });
            alert(`Fotografija je sada ${currentIsPrivate ? 'javna' : 'privatna'}!`);
            if (hasSearched) {
                handleSearch({ preventDefault: () => {} });
            } else {
                fetchLast10PublicPhotos();
            }
        } catch (err) {
            console.error('Greška pri promjeni privatnosti:', err);
            alert(`Greška prilikom promjene privatnosti: ${err.message}`);
        }
    };

    // --- NOVO: Funkcija za otvaranje modala s velikom fotkom ---
    const handlePhotoClick = (photoUrl, description) => {
        setSelectedPhotoUrl(photoUrl);
        setSelectedPhotoDescription(description);
        setShowPhotoModal(true);
    };
    // -----------------------------------------------------------

    const photosToDisplay = hasSearched ? searchResults : photos;

    const formatHashtagsForDisplay = (hashtags) => {
        if (!hashtags) {
            return '';
        }

        let tags = [];

        if (Array.isArray(hashtags)) {
            tags = hashtags;
        } else if (typeof hashtags === 'string') {
            let cleanedString = hashtags.replace(/^\[?#?|\]?$/g, '');
            tags = cleanedString.split(/,+/);
        } else {
            return '';
        }

        return tags
            .map(tag => tag.trim().replace(/^#/, ''))
            .filter(tag => tag !== '')
            .map(tag => `#${tag}`)
            .join(' ');
    };

    const formatHashtagsForEdit = (hashtags) => {
        if (!hashtags) {
            return '';
        }

        let tags = [];

        if (Array.isArray(hashtags)) {
            tags = hashtags;
        } else if (typeof hashtags === 'string') {
            let cleanedString = hashtags.replace(/^\[?#?|\]?$/g, '');
            tags = cleanedString.split(/,+/);
        } else {
            return '';
        }

        return tags
            .map(tag => tag.trim().replace(/^#/, ''))
            .filter(tag => tag !== '')
            .join(' ');
    };

    return (
        <Container fluid className="homepage-container">
            <h1 className="section-heading text-center mb-5">
                <FaImage className="me-2 text-primary" /> Javne fotografije
            </h1>

            <Card className="search-card shadow-lg mb-5">
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

                        <div className="d-grid gap-3">
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
                <Row xs={1} md={2} lg={3} className="g-4 public-photo-grid">
                    {photosToDisplay.map(photo => (
                        <Col key={photo.id}>
                            <Card className="h-100 public-photo-card shadow-sm">
                                <Card.Img
                                    variant="top"
                                    src={photo.fileUrl}
                                    alt={photo.description}
                                    className="public-photo-img"
                                    onClick={() => handlePhotoClick(photo.fileUrl, photo.description)} // --- DODANO OVDJE ---
                                    style={{ cursor: 'pointer' }} // Dodaje vizualni indikator da je klikabilno
                                />
                                <Card.Body>
                                    <Card.Title className="photo-card-title">{photo.description}</Card.Title>
                                    <Card.Text className="photo-card-hashtags">
                                        {formatHashtagsForDisplay(photo.hashtags)}
                                    </Card.Text>
                                    <Card.Text className="photo-card-author">
                                        Postavio: <strong>{photo.uploadedBy}</strong>
                                    </Card.Text>
                                    <Card.Text className="photo-card-upload-date">
                                        Objavljeno: {
                                        photo.uploadDate
                                            ? new Date(photo.uploadDate).toLocaleDateString('hr-HR', {
                                                year: 'numeric',
                                                month: 'long',
                                                day: 'numeric',
                                                hour: '2-digit',
                                                minute: '2-digit'
                                            })
                                            : 'Datum nije dostupan'
                                    }
                                    </Card.Text>
                                </Card.Body>
                                <Card.Footer className="text-muted photo-card-footer">
                                    <div className="photo-actions mt-2">
                                        {currentUserUid && (
                                            <Button
                                                variant="outline-primary"
                                                size="sm"
                                                className="me-2"
                                                onClick={() => handleDownloadPhoto(photo.id, photo.filename)}
                                                title="Preuzmi fotografiju"
                                            >
                                                <FaDownload />
                                            </Button>
                                        )}

                                        {currentUserUid === photo.uploadedBy && (
                                            <>
                                                <Button
                                                    variant="outline-danger"
                                                    size="sm"
                                                    className="me-2"
                                                    onClick={() => handleDeletePhoto(photo.id)}
                                                    title="Obriši fotografiju"
                                                >
                                                    <FaTrash />
                                                </Button>

                                                <Button
                                                    variant="outline-info"
                                                    size="sm"
                                                    className="me-2"
                                                    onClick={() => handleEditClick(photo)}
                                                    title="Uredi metapodatke"
                                                >
                                                    <FaEdit />
                                                </Button>

                                                <Button
                                                    variant={photo.isPrivate ? "outline-secondary" : "outline-success"}
                                                    size="sm"
                                                    onClick={() => handleTogglePrivacy(photo.id, photo.isPrivate)}
                                                    title={photo.isPrivate ? "Učini javnom" : "Učini privatnom"}
                                                >
                                                    {photo.isPrivate ? <FaEyeSlash /> : <FaEye />}
                                                </Button>
                                            </>
                                        )}
                                    </div>
                                </Card.Footer>
                            </Card>
                        </Col>
                    ))}
                </Row>
            )}

            <Modal show={showEditModal} onHide={() => setShowEditModal(false)} centered>
                <Modal.Header closeButton>
                    <Modal.Title>Uredi metapodatke fotografije</Modal.Title>
                </Modal.Header>
                <Form onSubmit={handleUpdatePhoto}>
                    <Modal.Body>
                        {currentPhotoToEdit && (
                            <>
                                <Form.Group className="mb-3">
                                    <Form.Label>Opis</Form.Label>
                                    <FormControl
                                        as="textarea"
                                        rows={3}
                                        value={editDescription}
                                        onChange={(e) => setEditDescription(e.target.value)}
                                    />
                                </Form.Group>
                                <Form.Group className="mb-3">
                                    <Form.Label>Hashtagovi (razdvojeni razmakom)</Form.Label>
                                    <FormControl
                                        type="text"
                                        value={editHashtags}
                                        onChange={(e) => setEditHashtags(e.target.value)}
                                        placeholder="npr. priroda sunce more"
                                    />
                                    <Form.Text className="text-muted">
                                        Unesite hashtagove razdvojene razmakom.
                                    </Form.Text>
                                </Form.Group>
                                <Form.Group className="mb-3">
                                    <Form.Check
                                        type="checkbox"
                                        label="Privatna fotografija"
                                        checked={editIsPrivate}
                                        onChange={(e) => setEditIsPrivate(e.target.checked)}
                                    />
                                    <Form.Text className="text-muted">
                                        Ako je označeno, fotografija neće biti javno vidljiva (osim na vašem profilu).
                                    </Form.Text>
                                </Form.Group>
                            </>
                        )}
                    </Modal.Body>
                    <Modal.Footer>
                        <Button variant="secondary" onClick={() => setShowEditModal(false)}>
                            Odustani
                        </Button>
                        <Button variant="primary" type="submit">
                            Spremi promjene
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>

            {/* --- NOVI MODAL ZA PRIKAZ VELIKE FOTOGRAFIJE --- */}
            <Modal show={showPhotoModal} onHide={() => setShowPhotoModal(false)} centered size="lg">
                <Modal.Header closeButton>
                    <Modal.Title>{selectedPhotoDescription || 'Pregled fotografije'}</Modal.Title>
                </Modal.Header>
                <Modal.Body className="text-center">
                    {selectedPhotoUrl && (
                        <img
                            src={selectedPhotoUrl}
                            alt={selectedPhotoDescription}
                            style={{ maxWidth: '100%', height: 'auto' }}
                        />
                    )}
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={() => setShowPhotoModal(false)}>
                        Zatvori
                    </Button>
                </Modal.Footer>
            </Modal>
            {/* ------------------------------------------------ */}
        </Container>
    );
}

export default HomePage;