import React from 'react';
import { Card, Form, Button, Alert, Spinner, Row, Col, InputGroup, FormControl } from 'react-bootstrap';
import { FaCloudUploadAlt, FaLock, FaGlobe } from 'react-icons/fa';

const PhotoUploadCard = ({
                             handleUploadPhoto,
                             uploadError,
                             uploading,
                             file,
                             setFile,
                             description,
                             setDescription,
                             hashtags,
                             setHashtags,
                             isPrivate,
                             setIsPrivate,
                             isResizingEnabled,
                             setIsResizingEnabled,
                             maxWidth,
                             setMaxWidth,
                             maxHeight,
                             setMaxHeight,
                             outputFormat,
                             setOutputFormat,
                             uploadsLeft
                         }) => {
    return (
        <Card className="upload-card shadow-lg mb-5 border-0">
            <Card.Body className="p-4 p-md-5">
                <Card.Title className="mb-4 upload-card-title text-center fw-bold">
                    <FaCloudUploadAlt className="me-2 text-primary" /> Učitaj Novu Fotografiju
                </Card.Title>
                <Form onSubmit={handleUploadPhoto}>
                    {uploadError && <Alert variant="danger" className="shake-animation">{uploadError}</Alert>}
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
    );
};

export default PhotoUploadCard;