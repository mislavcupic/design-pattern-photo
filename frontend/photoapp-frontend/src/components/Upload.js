import React, { useEffect, useState } from 'react';
import { auth } from "./Firebase";
import { Form, Button, Container,Image as BootstrapImage, Alert } from "react-bootstrap";

const BASE_URL = 'http://localhost:8080';

function Upload() {
    const [file, setFile] = useState(null);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [customName, setCustomName] = useState("");
    const [maxWidth, setMaxWidth] = useState(800);
    const [maxHeight, setMaxHeight] = useState(800);
    const [description, setDescription] = useState(""); // State za description
    const [hashtags, setHashtags] = useState(""); // State za hashtags
    const [uploading, setUploading] = useState(false);  // Za praćenje statusa učitavanja
    const [uploadError, setUploadError] = useState(null); // Za praćenje grešaka prilikom učitavanja
    const [uploadsLeft, setUploadsLeft] = useState(0);

     const fetchWithAuth = async (url, options = {}) => {
            // ... (isti fetchWithAuth kao u originalu)
            try {
                let currentUser = auth.currentUser;

                if (!currentUser) {
                    throw new Error("Korisnik nije autentificiran");
                }

                const idToken = await currentUser.getIdToken(true);

                if (!idToken) {
                    throw new Error("Neispravan ID token");
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
                    throw new Error(`Greška: ${response.status} - ${errorText}`);
                }

                return response;
            } catch (error) {
                console.error("fetchWithAuth error:", error);
                throw error;
            }
        };


    const handleFileChange = (e) => {
        const selectedFile = e.target.files[0];
        if (selectedFile) {
            setFile(selectedFile);
            setPreviewUrl(URL.createObjectURL(selectedFile));
            setCustomName(selectedFile.name.replace(/\.[^/.]+$/, "")); // ime bez ekstenzije
        }
    };

    const resizeImage = (file, maxWidth, maxHeight) => {
        return new Promise((resolve, reject) => {
            const img = new Image();
            img.onload = () => {
                let width = img.width;
                let height = img.height;

                const canvas = document.createElement("canvas");

                // Skaliranje slike
                if (width > maxWidth || height > maxHeight) {
                    const ratio = Math.min(maxWidth / width, maxHeight / height);
                    width = width * ratio;
                    height = height * ratio;
                }

                canvas.width = width;
                canvas.height = height;

                const ctx = canvas.getContext("2d");
                ctx.drawImage(img, 0, 0, width, height);

                canvas.toBlob((blob) => {
                    if (!blob) return reject("Neuspješno pretvaranje slike u blob");
                    resolve(blob);
                }, "image/jpeg", 0.8);
            };
            img.onerror = reject;
            img.src = URL.createObjectURL(file);
        });
    };

    const fetchRemainingUploads = async () => {
                   try {
                       const res = await fetchWithAuth(`${BASE_URL}/user-package/remaining-uploads`);
                       const data = await res.json();
                       setUploadsLeft(data);
                   } catch (err) {
                       console.error("Neuspješno dohvaćanje preostalih uploadova", err);
                   }
               };

   useEffect(() => {
       fetchRemainingUploads();
   }, []);

    const handleUpload = async () => {
        const user = auth.currentUser;
        console.log("hendlam upload")
        if (!user || !file || !description || !hashtags) return;  // Provjera postoji li user, description i hashtags

        setUploading(true);
        setUploadError(null);

        const idToken = await user.getIdToken();
        try {
            const resizedBlob = await resizeImage(file, maxWidth, maxHeight);
            const extension = "jpg"; // Pretvaramo sve u JPEG
            const filename = `${customName}.${extension}`;

            const formData = new FormData();
            formData.append("file", resizedBlob, filename);
            formData.append("description", description);  // Dodaj description u formu
            formData.append("hashtags", hashtags);  // Dodaj hashtags u formu

            const response = await fetch("http://localhost:8080/api/photos/upload", {
                method: "POST",
                headers: {
                    Authorization: `Bearer ${idToken}`,
                },
                body: formData,
            });
            console.log("hendlam upload")

            const text = await response.text();
            if (response.ok) {
                console.log("Upload uspješan:", text);
                alert("Slika uspješno poslana!");
                await fetchRemainingUploads();
            } else {
                console.error("Greška pri uploadu:", text);
                setUploadError("Došlo je do greške pri uploadu.");
            }
        } catch (error) {
            console.error("Greška pri uploadu:", error);
            setUploadError("Došlo je do greške pri uploadu.");
        } finally {
            setUploading(false);
        }
    };

    return (
        <Container className="my-5" style={{ maxWidth: "600px" }}>
            <h2 className="mb-4">Upload slike</h2>

            {uploadError && <Alert variant="danger">{uploadError}</Alert>} {/* Greška pri uploadu */}

            <Form>
                <Form.Group controlId="formFile" className="mb-3">
                    <Form.Label>Odaberite sliku</Form.Label>
                    <Form.Control type="file" accept="image/*" onChange={handleFileChange} />
                </Form.Group>

                {file && (
                    <div className="mb-3">
                        <h5>Pregled slike:</h5>
                        <BootstrapImage src={previewUrl} alt="Pregled" fluid rounded className="mb-2" />
                        <p><strong>Original:</strong> {file.name} ({(file.size / 1024).toFixed(2)} KB)</p>

                        <Form.Group controlId="formCustomName" className="mb-3">
                            <Form.Label>Naziv slike</Form.Label>
                            <Form.Control
                                type="text"
                                value={customName}
                                onChange={(e) => setCustomName(e.target.value)}
                            />
                        </Form.Group>

                        <div className="d-flex mb-3">
                            <Form.Group controlId="formMaxWidth" className="me-3">
                                <Form.Label>Širina (px)</Form.Label>
                                <Form.Control
                                    type="number"
                                    value={maxWidth}
                                    onChange={(e) => setMaxWidth(Number(e.target.value))}
                                />
                            </Form.Group>

                            <Form.Group controlId="formMaxHeight">
                                <Form.Label>Visina (px)</Form.Label>
                                <Form.Control
                                    type="number"
                                    value={maxHeight}
                                    onChange={(e) => setMaxHeight(Number(e.target.value))}
                                />
                            </Form.Group>
                        </div>

                        <Form.Group controlId="formDescription" className="mb-3">
                            <Form.Label>Opis slike</Form.Label>
                            <Form.Control
                                type="text"
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                placeholder="Unesite opis slike"
                            />
                        </Form.Group>

                        <Form.Group controlId="formHashtags" className="mb-3">
                            <Form.Label>Hashtags</Form.Label>
                            <Form.Control
                                type="text"
                                value={hashtags}
                                onChange={(e) => setHashtags(e.target.value)}
                                placeholder="Unesite hashtagove (npr. #nature, #sunset)"
                            />
                        </Form.Group>
                    </div>
                )}

                <Button
                    variant="primary"
                    onClick={handleUpload}
                    disabled={!file || !description || !hashtags || uploading}
                    className="w-100"
                >
                    {uploading ? "Učitavanje..." : "Pošaljite sliku"}
                </Button>
            </Form>
        </Container>
    );
}

export default Upload;
