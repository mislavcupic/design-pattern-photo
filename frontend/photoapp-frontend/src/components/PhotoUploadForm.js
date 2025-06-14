import React, { useState } from 'react';
// Axios više ne treba, pa ga brišemo
import { useAuth } from '../context/AuthContext'; // Uvezi useAuth hook iz TVOG AuthContexta

const PhotoUploadForm = ({ onUploadSuccess }) => {
    const { user, idToken } = useAuth();

    // Stanje za polja forme
    const [file, setFile] = useState(null);
    const [description, setDescription] = useState('');
    const [hashtags, setHashtags] = useState('');
    const [isPrivate, setIsPrivate] = useState(false);

    // Stanje za opcije obrade slike
    const [isResizingEnabled, setIsResizingEnabled] = useState(false);
    const [maxWidth, setMaxWidth] = useState('');
    const [maxHeight, setMaxHeight] = useState('');
    const [outputFormat, setOutputFormat] = useState('');

    // Dodatno stanje za loandig/error poruke unutar komponente
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState(null);

    // Provjeri je li korisnik prijavljen i ima li token
    if (!user || !idToken) {
        return (
            <div style={{ padding: '20px', textAlign: 'center', color: '#888' }}>
                Molimo prijavite se za učitavanje fotografija.
            </div>
        );
    }

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null); // Resetiraj prethodne greške
        setUploading(true); // Postavi stanje na "uploading"

        if (!file) {
            setError('Molimo odaberite datoteku.');
            setUploading(false);
            return;
        }

        const formData = new FormData();
        formData.append('file', file);
        formData.append('description', description);
        formData.append('hashtags', hashtags);
        // Koristi UID direktno iz Firebase User objekta
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
            // Postavite URL svog backend API-ja
            const response = await fetch('http://localhost:8080/api/photos/upload', {
                method: 'POST', // HTTP metoda
                headers: {
                    // Kada šalješ FormData, preglednik automatski postavlja
                    // 'Content-Type': 'multipart/form-data; boundary=----WebKitFormBoundary...'
                    // Ne trebaš ga ručno postavljati ovdje!
                    'Authorization': `Bearer ${idToken}`, // Firebase ID Token
                },
                body: formData, // FormData objekt ide direktno kao body
            });

            // Fetch ne baca grešku za HTTP status kodove 4xx/5xx,
            // već samo postavi response.ok na false. Moramo sami provjeriti.
            if (!response.ok) {
                let errorMessage = 'Greška prilikom učitavanja fotografije.';
                try {
                    // Pokušaj parsirati odgovor kao JSON za detaljniju grešku
                    const errorData = await response.json();
                    errorMessage = errorData.message || errorMessage;
                } catch (jsonError) {
                    // Ako odgovor nije JSON, uzmi cijeli tekst odgovora
                    const errorText = await response.text();
                    errorMessage = errorText || errorMessage;
                }
                throw new Error(errorMessage);
            }

            const data = await response.json(); // Parsiraj odgovor kao JSON
            console.log('Upload successful:', data);
            alert('Fotografija uspješno učitana!');
            // Očisti formu nakon uspješnog uploada
            setFile(null);
            setDescription('');
            setHashtags('');
            setIsPrivate(false);
            setIsResizingEnabled(false);
            setMaxWidth('');
            setMaxHeight('');
            setOutputFormat('');

            if (onUploadSuccess) {
                onUploadSuccess();
            }

        } catch (err) {
            console.error('Error during upload:', err.message);
            setError('Greška prilikom učitavanja fotografije: ' + err.message);
        } finally {
            setUploading(false); // Završi "uploading" stanje
        }
    };

    return (
        <form onSubmit={handleSubmit} style={{ margin: '20px', padding: '20px', border: '1px solid #ccc', borderRadius: '8px' }}>
            <h2>Učitaj Novu Fotografiju</h2>

            {error && <p style={{ color: 'red' }}>{error}</p>}
            {uploading && <p style={{ color: 'blue' }}>Učitavam fotografiju...</p>}

            <div>
                <label htmlFor="fileInput">Odaberite datoteku:</label>
                <input
                    type="file"
                    id="fileInput"
                    onChange={(e) => setFile(e.target.files[0])}
                    required
                    disabled={uploading}
                />
            </div>

            <div style={{ marginTop: '10px' }}>
                <label htmlFor="descriptionInput">Opis:</label>
                <textarea
                    id="descriptionInput"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    rows="3"
                    placeholder="Unesite opis fotografije"
                    disabled={uploading}
                />
            </div>

            <div style={{ marginTop: '10px' }}>
                <label htmlFor="hashtagsInput">Hashtagovi (razdvojeni zarezom):</label>
                <input
                    type="text"
                    id="hashtagsInput"
                    value={hashtags}
                    onChange={(e) => setHashtags(e.target.value)}
                    placeholder="#priroda, #ljeto, #putovanja"
                    disabled={uploading}
                />
            </div>

            <div style={{ marginTop: '10px' }}>
                <input
                    type="checkbox"
                    id="isPrivateCheckbox"
                    checked={isPrivate}
                    onChange={(e) => setIsPrivate(e.target.checked)}
                    disabled={uploading}
                />
                <label htmlFor="isPrivateCheckbox">Privatna fotografija</label>
            </div>

            {/* --- OPCIJE OBRADE SLIKE --- */}
            <h3 style={{ marginTop: '25px', marginBottom: '15px' }}>Opcije obrade slike (prije uploada):</h3>

            <div style={{ marginBottom: '10px' }}>
                <input
                    type="checkbox"
                    id="resizeImage"
                    checked={isResizingEnabled}
                    onChange={(e) => setIsResizingEnabled(e.target.checked)}
                    disabled={uploading}
                />
                <label htmlFor="resizeImage">Promijeni veličinu (Resize)</label>

                {isResizingEnabled && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '5px', marginLeft: '20px', marginTop: '10px' }}>
                        <div>
                            <label htmlFor="maxWidth">Maks. Širina (px):</label>
                            <input
                                type="number"
                                id="maxWidth"
                                value={maxWidth}
                                onChange={(e) => setMaxWidth(e.target.value)}
                                min="1"
                                placeholder="npr. 800"
                                disabled={uploading}
                            />
                        </div>
                        <div>
                            <label htmlFor="maxHeight">Maks. Visina (px):</label>
                            <input
                                type="number"
                                id="maxHeight"
                                value={maxHeight}
                                onChange={(e) => setMaxHeight(e.target.value)}
                                min="1"
                                placeholder="npr. 600"
                                disabled={uploading}
                            />
                        </div>
                    </div>
                )}
            </div>

            <div style={{ marginBottom: '20px' }}>
                <label htmlFor="outputFormat">Izlazni format:</label>
                <select
                    id="outputFormat"
                    value={outputFormat}
                    onChange={(e) => setOutputFormat(e.target.value)}
                    disabled={uploading}
                >
                    <option value="">Original</option>
                    <option value="png">PNG</option>
                    <option value="jpeg">JPG</option>
                    <option value="bmp">BMP</option>
                </select>
            </div>

            <button type="submit" disabled={uploading} style={{ padding: '10px 20px', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', opacity: uploading ? 0.6 : 1 }}>
                {uploading ? 'Učitavam...' : 'Učitaj Fotografiju'}
            </button>
        </form>
    );
};

export default PhotoUploadForm;