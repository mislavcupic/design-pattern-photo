import React, { useEffect, useState, useCallback } from 'react';
import { auth } from './Firebase';
import {
    Button,
    Container,
    Row,
    Col,
    Form,
    Spinner,
    Image as BootstrapImage,
    Card,
    ListGroup,
    Badge,
    InputGroup,
    FormControl,
    Alert,
    Modal
} from 'react-bootstrap';
import {
    FaDownload, FaTrashAlt, FaLock, FaGlobe,
    FaImage, FaCloudUploadAlt, FaExchangeAlt, FaUserCircle, FaInfoCircle, FaEdit
} from 'react-icons/fa';
import './css/ProfilePage.css';
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

const BASE_URL = 'http://localhost:8080';

const ProfilePage = () => {
    const [user, setUser] = useState(null);
    const [userPackage, setUserPackage] = useState(null);
    const [uploadsLeft, setUploadsLeft] = useState(0);
    const [nextEligibleChange, setNextEligibleChange] = useState(null);
    const [photos, setPhotos] = useState([]);
    const [isLoading, setIsLoading] = useState(true);

    const [file, setFile] = useState(null);
    const [description, setDescription] = useState('');
    // ISPRAVLJENO: hashtags inicijalizacija je sada ispravna
    const [hashtags, setHashtags] = useState('');
    const [isPrivate, setIsPrivate] = useState(false);

    const [isResizingEnabled, setIsResizingEnabled] = useState(false);
    const [maxWidth, setMaxWidth] = useState('');
    const [maxHeight, setMaxHeight] = useState('');
    const [outputFormat, setOutputFormat] = useState('');

    const [uploading, setUploading] = useState(false);
    const [uploadError, setUploadError] = useState(null);

    const [packageChangeError, setPackageChangeError] = useState(null);
    const [changingPackage, setChangingPackage] = useState(false);
    const [selectedPackage, setSelectedPackage] = useState('');

    const [showDownloadModal, setShowDownloadModal] = useState(false);
    const [selectedPhotoForDownload, setSelectedPhotoForDownload] = useState(null);
    const [downloadMaxWidth, setDownloadMaxWidth] = useState('');
    const [downloadMaxHeight, setDownloadMaxHeight] = useState('');
    const [downloadOutputFormat, setDownloadOutputFormat] = useState('');
    const [downloadApplySepia, setDownloadApplySepia] = useState(false);
    const [downloadApplyBlur, setDownloadApplyBlur] = useState(false);
    const [downloading, setDownloading] = useState(false);
    const [downloadError, setDownloadError] = useState(null);

    const [showEditModal, setShowEditModal] = useState(false);
    const [currentPhotoToEdit, setCurrentPhotoToEdit] = useState(null);
    const [editDescription, setEditDescription] = useState('');
    const [editHashtags, setEditHashtags] = useState('');
    const [editIsPrivate, setEditIsPrivate] = useState(false);

    const [showPhotoModal, setShowPhotoModal] = useState(false);
    const [selectedPhotoUrl, setSelectedPhotoUrl] = useState('');
    const [selectedPhotoDescription, setSelectedPhotoDescription] = useState('');

    const fetchWithAuth = useCallback(async (url, options = {}) => {
        try {
            let currentUser = auth.currentUser;
            if (!currentUser) {
                toast.error("Korisnik nije autentificiran. Molimo prijavite se.", { autoClose: 3000 });
                throw new Error("Korisnik nije autentificiran. Molimo prijavite se.");
            }
            const idToken = await currentUser.getIdToken(true);
            if (!idToken) {
                toast.error("Neispravan ID token. Molimo pokušajte ponovo.", { autoClose: 3000 });
                throw new Error("Neispravan ID token. Molimo pokušajte ponovo.");
            }

            const response = await fetch(url, {
                ...options,
                headers: {
                    ...options.headers,
                    Authorization: `Bearer ${idToken}`,
                },
            });

            if (!response.ok) {
                const errorText = await response.text();
                try {
                    const errorJson = JSON.parse(errorText);
                    throw new Error(errorJson.message || `Greška: ${response.status} - ${errorText}`);
                } catch (e) {
                    throw new Error(`Greška: ${response.status} - ${errorText}`);
                }
            }
            return response;
        } catch (error) {
            console.error("fetchWithAuth error:", error);
            if (!error.message.includes("Niste prijavljeni") && !error.message.includes("Neispravan ID token")) {
                toast.error(`Došlo je do greške: ${error.message}`, { autoClose: 5000 });
            }
            throw error;
        }
    }, []); // Prazan dependency array jer auth objekt i BASE_URL ne mijenjaju

    const fetchDataAndUserStatus = useCallback(async () => {
        try {
            setIsLoading(true);
            const currentUser = auth.currentUser;
            setUser(currentUser);

            if (!currentUser) {
                setIsLoading(false);
                setPhotos([]);
                return;
            }

            const [userPackageRes, remainingUploadsRes, photosRes, nextChangeRes] = await Promise.all([
                fetchWithAuth(`${BASE_URL}/user-package/user-package`),
                fetchWithAuth(`${BASE_URL}/user-package/remaining-uploads`),
                fetchWithAuth(`${BASE_URL}/api/photos/user/${currentUser.uid}`),
                fetchWithAuth(`${BASE_URL}/user-package/next-eligible-change`)
            ]);

            const userPackageData = await userPackageRes.json();
            setUserPackage(userPackageData);

            const remainingUploadsData = await remainingUploadsRes.json();
            setUploadsLeft(remainingUploadsData);

            const photosData = await photosRes.json();
            setPhotos(photosData || []);

            const nextChangeData = await nextChangeRes.json();
            setNextEligibleChange(nextChangeData ? new Date(nextChangeData) : null);

        } catch (error) {
            console.error('Greška pri dohvaćanju podataka profila:', error);
            // Uklonjena shake-animation
            toast.error(`Greška pri učitavanju profila: ${error.message}`, { autoClose: 5000 });
            setPhotos([]);
        } finally {
            setIsLoading(false);
        }
    }, [fetchWithAuth]); // Dodana fetchWithAuth kao ovisnost

    useEffect(() => {
        const unsubscribe = auth.onAuthStateChanged(user => {
            if (user) {
                setUser(user);
                fetchDataAndUserStatus();
            } else {
                setUser(null);
                setPhotos([]);
                setIsLoading(false);
            }
        });
        return () => unsubscribe();
    }, [fetchDataAndUserStatus]); // Dodana fetchDataAndUserStatus kao ovisnost

    const downloadPhoto = useCallback(async (
        photoId,
        options = {
            maxWidth: null,
            maxHeight: null,
            outputFormat: null,
            applySepia: false,
            applyBlur: false
        }
    ) => {
        setDownloading(true);
        setDownloadError(null);
        try {
            let currentUser = auth.currentUser;
            if (!currentUser) {
                toast.error("Korisnik nije autentificiran za preuzimanje fotografije.", { autoClose: 3000 });
                throw new Error("Korisnik nije autentificiran za preuzimanje fotografije. Molimo prijavite se.");
            }
            const idToken = await currentUser.getIdToken(true);
            if (!idToken) {
                toast.error("Neispravan ID token za preuzimanje.", { autoClose: 3000 });
                throw new Error("Neispravan ID token za preuzimanje. Molimo pokušajte ponovo.");
            }

            const headers = { Authorization: `Bearer ${idToken}` };
            const params = new URLSearchParams();
            if (options.maxWidth) params.append('maxWidth', options.maxWidth);
            if (options.maxHeight) params.append('maxHeight', options.maxHeight);
            if (options.outputFormat) params.append('outputFormat', options.outputFormat);
            if (options.applySepia) params.append('applySepia', options.applySepia);
            if (options.applyBlur) params.append('applyBlur', options.applyBlur);

            const response = await fetch(`${BASE_URL}/api/photos/${photoId}/download?${params.toString()}`, {
                method: 'GET',
                headers: headers,
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`HTTP error! status: ${response.status} - ${errorText}`);
            }

            const contentDisposition = response.headers.get('Content-Disposition');
            let filename = `processed_photo.jpeg`;
            if (contentDisposition) {
                const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?([^;]+)/);
                if (filenameMatch && filenameMatch[1]) {
                    try {
                        filename = decodeURIComponent(filenameMatch[1].replace(/%([0-9A-Fa-f]{2})/g, '%$1'));
                        filename = filename.replace(/^"|"$/g, '');
                    } catch (e) {
                        console.warn("Could not decode filename from Content-Disposition, using default.", e);
                    }
                }
            }

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', filename);
            document.body.appendChild(link);
            link.click();
            link.parentNode.removeChild(link);
            window.URL.revokeObjectURL(url);

            toast.success("Fotografija uspješno preuzeta!", { autoClose: 2000 });
            return true;
        } catch (error) {
            console.error('Error downloading photo:', error);
            toast.error(`Greška pri preuzimanju fotografije: ${error.message}`, { autoClose: 5000 });
            throw error;
        } finally {
            setDownloading(false);
        }
    }, []); // Prazan dependency array, auth objekt je konstantan

    const handleChangePackage = async () => {
        if (!selectedPackage) {
            setPackageChangeError("Molimo odaberite paket.");
            toast.warn("Molimo odaberite paket.", { autoClose: 2000 });
            return;
        }
        setPackageChangeError(null);
        setChangingPackage(true);

        try {
            await fetchWithAuth(`${BASE_URL}/user-package/change-package`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(selectedPackage),
            });

            toast.success(`Paket uspješno promijenjen u ${selectedPackage}!`, { autoClose: 2000 });
            await fetchDataAndUserStatus();
            setSelectedPackage('');

        } catch (error) {
            console.error("Greška prilikom promjene paketa:", error);
            // Uklonjena shake-animation
            setPackageChangeError(`Greška prilikom promjene paketa: ${error.message}`);
            toast.error(`Greška prilikom promjene paketa: ${error.message}`, { autoClose: 5000 });
        } finally {
            setChangingPackage(false);
        }
    };

    const handleUploadPhoto = async (e) => {
        e.preventDefault();
        setUploadError(null);
        setUploading(true);

        if (!file) {
            setUploadError('Molimo odaberite datoteku.');
            toast.warn('Molimo odaberite datoteku.', { autoClose: 2000 });
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
                toast.error('Maksimalna širina i visina moraju biti pozitivni brojevi.', { autoClose: 3000 });
                setUploading(false);
                return;
            }
        }

        const formData = new FormData();
        formData.append('file', file);
        formData.append('description', description);
        formData.append('hashtags', hashtags);
        formData.append('uid', user.uid);
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
                body: formData,
            });

            toast.success('Fotografija uspješno učitana!', { autoClose: 2000 });
            await fetchDataAndUserStatus();

            setFile(null);
            setDescription('');
            setHashtags('');
            setIsPrivate(false);
            setIsResizingEnabled(false);
            setMaxWidth('');
            setMaxHeight('');
            setOutputFormat('');

        } catch (error) {
            console.error('Upload error:', error);
            // Uklonjena shake-animation
            setUploadError(`Greška kod uploada: ${error.message}`);
            toast.error(`Greška kod uploada: ${error.message}`, { autoClose: 5000 });
        } finally {
            setUploading(false);
        }
    };

    const handleDeletePhoto = async (photoId) => {
        if (!window.confirm("Jeste li sigurni da želite obrisati ovu fotografiju?")) {
            return;
        }
        try {
            await fetchWithAuth(`${BASE_URL}/api/photos/${photoId}`, {
                method: 'DELETE',
            });
            toast.success('Fotografija uspješno obrisana!', { autoClose: 2000 });
            await fetchDataAndUserStatus();
        } catch (error) {
            console.error('Greška prilikom brisanja fotografije:', error);
            toast.error(`Greška prilikom brisanja fotografije: ${error.message}`, { autoClose: 5000 });
        }
    };

    const handleTogglePrivacy = async (photoId, currentIsPrivate) => {
        try {
            await fetchWithAuth(`${BASE_URL}/api/photos/${photoId}/toggle-privacy`, {
                method: 'PUT',
            });
            toast.info(`Fotografija je sada ${currentIsPrivate ? 'javna' : 'privatna'}!`, { autoClose: 2000 });
            await fetchDataAndUserStatus();
        } catch (error) {
            console.error('Greška prilikom promjene privatnosti fotografije:', error);
            toast.error(`Greška: ${error.message}`, { autoClose: 5000 });
        }
    };

    const getUserName = () => {
        return user?.displayName || "Anonimni Korisnik";
    };

    const handleDownloadClick = (photo) => {
        setSelectedPhotoForDownload(photo);
        setDownloadMaxWidth('');
        setDownloadMaxHeight('');
        setDownloadOutputFormat('');
        setDownloadApplySepia(false);
        setDownloadApplyBlur(false);
        setDownloadError(null);
        setShowDownloadModal(true);
    };

    const handleDownloadConfirm = async () => {
        if (!selectedPhotoForDownload) return;

        setDownloading(true);
        setDownloadError(null);

        const options = {
            maxWidth: downloadMaxWidth ? parseInt(downloadMaxWidth) : null,
            maxHeight: downloadMaxHeight ? parseInt(downloadMaxHeight) : null,
            outputFormat: downloadOutputFormat || null,
            applySepia: downloadApplySepia,
            applyBlur: downloadApplyBlur
        };

        try {
            await downloadPhoto(selectedPhotoForDownload.id, options);
            setShowDownloadModal(false);
        } catch (error) {
            console.error('Greška pri iniciranju preuzimanja:', error);
            // Uklonjena shake-animation
            setDownloadError(`Greška pri preuzimanja: ${error.message}`);
        } finally {
            setDownloading(false);
        }
    };

    const handleCloseDownloadModal = () => {
        setShowDownloadModal(false);
        setSelectedPhotoForDownload(null);
    };

    const formatHashtagsForDisplay = (hashtags) => {
        if (!hashtags) {
            return '';
        }

        let tags = [];

        if (Array.isArray(hashtags)) {
            tags = hashtags;
        } else if (typeof hashtags === 'string') {
            // ISPRAVLJENO: zatvoren regularni izraz
            let cleanedString = hashtags.replace(/^\[?#?|\]?$/g, '');
            tags = cleanedString.split(/[\s,;]+/);
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
            // ISPRAVLJENO: zatvoren regularni izraz
            let cleanedString = hashtags.replace(/^\[?#?|\]?$/g, '');
            tags = cleanedString.split(/[\s,;]+/);
        } else {
            return '';
        }

        return tags
            .map(tag => tag.trim().replace(/^#/, ''))
            .filter(tag => tag !== '')
            .join(' ');
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

            await fetchWithAuth(`${BASE_URL}/api/photos/${currentPhotoToEdit.id}?${queryParams}`, {
                method: 'PUT',
            });
            toast.success('Metapodaci fotografije uspješno ažurirani!', { autoClose: 2000 });
            setShowEditModal(false);
            await fetchDataAndUserStatus();
        } catch (error) {
            console.error('Greška pri ažuriranju fotografije:', error);
            toast.error(`Greška prilikom ažuriranja fotografije: ${error.message}`, { autoClose: 5000 });
        }
    };

    const handlePhotoClick = (photoUrl, description) => {
        setSelectedPhotoUrl(photoUrl);
        setSelectedPhotoDescription(description);
        setShowPhotoModal(true);
    };

    if (isLoading) {
        return (
            <Container className="my-5 text-center loading-container">
                <Spinner animation="border" role="status" className="loading-spinner" />
                <p className="mt-3 loading-text">Učitavanje korisničkih podataka...</p>
            </Container>
        );
    }

    if (!user) {
        return (
            <Container className="my-5 not-logged-in-container">
                <Alert variant="info" className="text-center shadow-sm">
                    <Alert.Heading className="alert-heading-custom"><FaInfoCircle className="me-2" />Niste prijavljeni!</Alert.Heading>
                    <p className="mb-0">Molimo prijavite se za pristup svom profilu i funkcionalnostima.</p>
                </Alert>
            </Container>
        );
    }

    return (
        <Container className="profile-container my-5">
            <ToastContainer position="top-right" autoClose={5000} hideProgressBar={false} newestOnTop={false} closeOnClick rtl={false} pauseOnFocusLoss draggable pauseOnHover />

            <h1 className="text-center mb-4 profile-title">
                <FaUserCircle className="me-2" /> Profil korisnika: {getUserName()}
            </h1>

            <Card className="user-card shadow-lg mb-5 border-0">
                <Card.Body className="p-4 p-md-5">
                    <div className="d-flex align-items-center mb-4">
                        <div className="profile-icon-large rounded-circle text-white d-flex align-items-center justify-content-center me-4">
                            <FaUserCircle className="profile-icon-svg" />
                        </div>
                        <div>
                            <Card.Title className="mb-1 profile-name-text fw-bold">{getUserName()}</Card.Title>
                            <Card.Subtitle className="text-muted profile-subtitle-text">{user?.email || 'Nema emaila'}</Card.Subtitle>
                        </div>
                    </div>
                    <ListGroup variant="flush" className="mb-4">
                        <ListGroup.Item className="package-info-item d-flex justify-content-between align-items-center">
                            <span>Paket:</span>
                            <Badge pill className="package-badge px-3 py-2">
                                {userPackage || 'N/A'}
                            </Badge>
                        </ListGroup.Item>
                        <ListGroup.Item className="upload-info-item d-flex justify-content-between align-items-center">
                            <span>Preostali uploadovi:</span>
                            <Badge pill className="uploads-badge px-3 py-2">{uploadsLeft}</Badge>
                        </ListGroup.Item>
                        {nextEligibleChange && (
                            <ListGroup.Item className="change-date-item d-flex justify-content-between align-items-center">
                                <span>Možete ponovno promijeniti paket:</span>
                                <Badge pill className="change-date-badge px-3 py-2">{nextEligibleChange.toLocaleString()}</Badge>
                            </ListGroup.Item>
                        )}
                    </ListGroup>
                    <Form.Group className="mt-4">
                        <Form.Label className="form-label-custom fw-semibold mb-2">
                            <FaExchangeAlt className="me-2 text-primary" /> Odaberite novi paket
                        </Form.Label>
                        <Form.Select
                            value={selectedPackage}
                            onChange={(e) => setSelectedPackage(e.target.value)}
                            className="form-select-custom"
                            disabled={changingPackage || (nextEligibleChange && new Date() < new Date(nextEligibleChange))}
                        >
                            <option value="">-- Odaberite --</option>
                            {['FREE', 'PRO', 'GOLD'].filter(pkg => pkg !== userPackage?.packageName).map(pkg => (
                                <option key={pkg} value={pkg}>{pkg}</option>
                            ))}
                        </Form.Select>
                        {packageChangeError && <Alert variant="danger" className="mt-3">{packageChangeError}</Alert>}
                        <Button
                            variant="outline-primary"
                            className="mt-3 w-100 package-change-btn"
                            disabled={!selectedPackage || changingPackage || (nextEligibleChange && new Date() < new Date(nextEligibleChange))}
                            onClick={handleChangePackage}
                        >
                            {changingPackage ? (
                                <>
                                    <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2" />
                                    Mijenjam paket...
                                </>
                            ) : (
                                <>
                                    <FaExchangeAlt className="me-2" /> Promijeni paket
                                </>
                            )}
                        </Button>
                    </Form.Group>
                </Card.Body>
            </Card>

            <Card className="upload-card shadow-lg mb-5 border-0">
                <Card.Body className="p-4 p-md-5">
                    <Card.Title className="mb-4 upload-card-title text-center fw-bold">
                        <FaCloudUploadAlt className="me-2 text-primary" /> Učitaj Novu Fotografiju
                    </Card.Title>
                    <Form onSubmit={handleUploadPhoto}>
                        {uploadError && <Alert variant="danger">{uploadError}</Alert>}
                        {uploading && (
                            <Alert variant="info" className="d-flex align-items-center justify-content-center upload-progress-alert">
                                <Spinner animation="border" size="sm" className="me-2" />
                                Učitavam fotografiju...
                            </Alert>
                        )}

                        <Form.Group controlId="formFile" className="mb-3">
                            <Form.Label className="form-label-custom fw-semibold">Odaberite fotografiju <span className="text-danger">*</span></Form.Label>
                            <Form.Control
                                type="file"
                                onChange={(e) => setFile(e.target.files[0])}
                                required
                                disabled={uploading}
                                className="form-control-file"
                            />
                        </Form.Group>

                        <Form.Group controlId="formDescription" className="mb-3">
                            <Form.Label className="form-label-custom fw-semibold">Opis</Form.Label>
                            <Form.Control
                                type="text"
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                placeholder="Dodajte opis fotografije (npr. 'Prekrasan zalazak sunca')"
                                disabled={uploading}
                            />
                        </Form.Group>

                        <Form.Group controlId="formHashtags" className="mb-3">
                            <Form.Label className="form-label-custom fw-semibold">Hashtagovi</Form.Label>
                            <Form.Control
                                type="text"
                                value={hashtags}
                                onChange={(e) => setHashtags(e.target.value)}
                                placeholder="Unesite hashtagove (odvojene razmakom, npr. #zalazaksunca #priroda)"
                                disabled={uploading}
                            />
                        </Form.Group>

                        <Form.Group className="mb-4">
                            <Form.Check
                                type="checkbox"
                                label={
                                    <>
                                        {isPrivate ? <FaLock className="me-1 text-danger" /> : <FaGlobe className="me-1 text-primary" />}
                                        Privatna fotografija (vidljiva samo vama)
                                    </>
                                }
                                checked={isPrivate}
                                onChange={(e) => setIsPrivate(e.target.checked)}
                                className="private-checkbox"
                                disabled={uploading}
                            />
                        </Form.Group>

                        <h4 className="mt-4 mb-3 text-center section-subtitle">Opcije obrade slike (prije uploada)</h4>

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
                            <Row className="mb-3 g-2">
                                <Col md={6}>
                                    <InputGroup className="mb-2 mb-md-0">
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
                                    <InputGroup>
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
                            <Form.Label className="fw-semibold">Izlazni format:</Form.Label>
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

                        <Button variant="outline-primary" type="submit" disabled={!file || uploading || uploadsLeft <= 0} className="w-100 upload-btn">
                            {uploading ? (
                                <>
                                    <Spinner as="span" animation="grow" size="sm" role="status" aria-hidden="true" className="me-2" />
                                    Učitavam...
                                </>
                            ) : (uploadsLeft <= 0 ? 'Nema preostalih uploadova' : (<><FaCloudUploadAlt className="me-2" /> Upload fotografije</>))}
                        </Button>
                        {uploadsLeft <= 0 && <Alert variant="warning" className="mt-3 text-center small-alert">Nadogradite paket za više uploadova!</Alert>}

                    </Form>
                </Card.Body>
            </Card>

            <Card className="gallery-card shadow-lg mb-5 border-0">
                <Card.Body className="p-4 p-md-5">
                    <Card.Title className="mb-4 gallery-card-title text-center fw-bold">
                        <FaImage className="me-2 text-primary" /> Vaše fotografije
                    </Card.Title>
                    <Row xs={1} md={2} lg={3} className="g-4">
                        {photos.length === 0 ? (
                            <Col xs={12}><p className="text-muted text-center py-4 fs-5">Još nema uploadanih fotografija. Budite prvi!</p></Col>
                        ) : (
                            photos.map((photoData) => (
                                <Col key={photoData.id}>
                                    <Card className="photo-item h-100 overflow-hidden shadow-sm">
                                        {photoData.fileUrl ? (
                                            <div className="photo-thumbnail-wrapper">
                                                <BootstrapImage
                                                    src={photoData.fileUrl}
                                                    alt={photoData.description}
                                                    className="card-img-top photo-thumbnail"
                                                    onClick={() => handlePhotoClick(photoData.fileUrl, photoData.description)}
                                                    style={{ cursor: 'pointer' }}
                                                />
                                                {photoData.isPrivate && (
                                                    <span className="private-overlay"><FaLock /> Privatno</span>
                                                )}
                                            </div>
                                        ) : (
                                            <div className="photo-placeholder d-flex align-items-center justify-content-center bg-light text-muted">
                                                <FaImage size={48} />
                                                <p className="m-0 ms-2">URL nedostupan</p>
                                            </div>
                                        )}
                                        <Card.Body className="d-flex flex-column justify-content-between p-3">
                                            <div>
                                                <Card.Text className="small text-muted mb-2 photo-description">{photoData.description || 'Bez opisa'}</Card.Text>
                                                <div className="mb-2 hashtags-container">
                                                    {formatHashtagsForDisplay(photoData.hashtags)}
                                                </div>
                                            </div>
                                            <div className="d-flex justify-content-between align-items-center mt-3 photo-actions">
                                                <Button
                                                    variant="link"
                                                    className="action-icon-button"
                                                    onClick={() => handleEditClick(photoData)}
                                                    title="Uredi metapodatke"
                                                >
                                                    <FaEdit className="text-info" size={18} />
                                                </Button>

                                                <Button
                                                    variant="link"
                                                    className="action-icon-button"
                                                    onClick={() => handleTogglePrivacy(photoData.id, photoData.isPrivate)}
                                                    title={photoData.isPrivate ? 'Privatna (klikni za javno)' : 'Javna (klikni za privatno)'}
                                                >
                                                    {photoData.isPrivate ? (
                                                        <FaLock className="text-danger" size={18} />
                                                    ) : (
                                                        <FaGlobe className="text-primary" size={18} />
                                                    )}
                                                </Button>
                                                <Button
                                                    variant="link"
                                                    className="action-icon-button"
                                                    onClick={() => handleDownloadClick(photoData)}
                                                    title="Preuzmi fotografiju"
                                                >
                                                    <FaDownload className="text-primary" size={18} />
                                                </Button>
                                                <Button
                                                    variant="link"
                                                    className="action-icon-button"
                                                    onClick={() => handleDeletePhoto(photoData.id)}
                                                    title="Obriši fotografiju"
                                                >
                                                    <FaTrashAlt className="text-danger" size={18} />
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

            <Modal show={showDownloadModal} onHide={handleCloseDownloadModal} centered contentClassName="modal-custom">
                <Modal.Header closeButton className="modal-header-custom">
                    <Modal.Title className="fw-bold">
                        Preuzmi fotografiju: <span className="text-primary">{selectedPhotoForDownload?.description || 'Bez opisa'}</span>
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body className="p-4">
                    {downloadError && <Alert variant="danger">{downloadError}</Alert>}
                    <Form>
                        <Form.Group controlId="downloadOutputFormat" className="mb-3">
                            <Form.Label className="fw-semibold">Izlazni format:</Form.Label>
                            <Form.Select
                                value={downloadOutputFormat}
                                onChange={(e) => setDownloadOutputFormat(e.target.value)}
                                disabled={downloading}
                            >
                                <option value="">Original</option>
                                <option value="png">PNG</option>
                                <option value="jpeg">JPG</option>
                                <option value="bmp">BMP</option>
                                <option value="gif">GIF</option>
                            </Form.Select>
                        </Form.Group>

                        <Row className="mb-3 g-2">
                            <Col>
                                <InputGroup>
                                    <InputGroup.Text>Maks. Širina (px)</InputGroup.Text>
                                    <FormControl
                                        type="number"
                                        value={downloadMaxWidth}
                                        onChange={(e) => setDownloadMaxWidth(e.target.value)}
                                        min="1"
                                        placeholder="npr. 800"
                                        disabled={downloading}
                                    />
                                </InputGroup>
                            </Col>
                            <Col>
                                <InputGroup>
                                    <InputGroup.Text>Maks. Visina (px)</InputGroup.Text>
                                    <FormControl
                                        type="number"
                                        value={downloadMaxHeight}
                                        onChange={(e) => setDownloadMaxHeight(e.target.value)}
                                        min="1"
                                        placeholder="npr. 600"
                                        disabled={downloading}
                                    />
                                </InputGroup>
                            </Col>
                        </Row>

                        <Form.Group className="mb-2">
                            <Form.Check
                                type="checkbox"
                                label="Primijeni Sepia filter"
                                checked={downloadApplySepia}
                                onChange={(e) => setDownloadApplySepia(e.target.checked)}
                                disabled={downloading}
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Check
                                type="checkbox"
                                label="Primijeni Blur filter"
                                checked={downloadApplyBlur}
                                onChange={(e) => setDownloadApplyBlur(e.target.checked)}
                                disabled={downloading}
                            />
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer className="modal-footer-custom">
                    <Button variant="outline-secondary" onClick={handleCloseDownloadModal} disabled={downloading}>
                        Odustani
                    </Button>
                    <Button variant="outline-primary" onClick={handleDownloadConfirm} disabled={downloading}>
                        {downloading ? (
                            <>
                                <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2" />
                                Preuzimam...
                            </>
                        ) : (
                            <>
                                <FaDownload className="me-2" /> Preuzmi
                            </>
                        )}
                    </Button>
                </Modal.Footer>
            </Modal>

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
                                        label={
                                            <>
                                                {editIsPrivate ? <FaLock className="me-1 text-danger" /> : <FaGlobe className="me-1 text-primary" />}
                                                Privatna fotografija
                                            </>
                                        }
                                        checked={editIsPrivate}
                                        onChange={(e) => setEditIsPrivate(e.target.checked)}
                                    />
                                    <Form.Text className="text-muted">
                                        Ako je označeno, fotografija neće biti javno vidljiva.
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
        </Container>
    );
};

export default ProfilePage;