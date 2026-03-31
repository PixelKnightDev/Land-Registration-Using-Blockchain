import { useEffect, useRef, useState, useCallback } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({ iconUrl: markerIcon, iconRetinaUrl: markerIcon2x, shadowUrl: markerShadow });

// ── State code lookup ─────────────────────────────────────────────────────────
const STATE_CODES = {
  'Madhya Pradesh': 'MP', 'Uttar Pradesh': 'UP', 'Rajasthan': 'RJ',
  'Maharashtra': 'MH', 'Gujarat': 'GJ', 'Karnataka': 'KA',
  'Tamil Nadu': 'TN', 'Kerala': 'KL', 'Andhra Pradesh': 'AP',
  'Telangana': 'TG', 'West Bengal': 'WB', 'Bihar': 'BR',
  'Punjab': 'PB', 'Haryana': 'HR', 'Himachal Pradesh': 'HP',
  'Uttarakhand': 'UK', 'Jharkhand': 'JH', 'Chhattisgarh': 'CG',
  'Odisha': 'OD', 'Assam': 'AS', 'Delhi': 'DL', 'Goa': 'GA',
};

function toCode(name = '') {
  const cleaned = name.replace(/[^a-zA-Z ]/g, '').trim();
  const words = cleaned.split(' ').filter(Boolean);
  if (!words.length) return 'UNK';
  if (words.length === 1) return words[0].slice(0, 3).toUpperCase();
  return words.map(w => w[0]).join('').slice(0, 3).toUpperCase().padEnd(3, 'X');
}

// Serial range 000–999 (matches the placeholder format MP-JBP-2026-003)
function coordSerial(lat, lng) {
  return String(Math.abs(Math.round((lat * 1000 + lng * 100) * 7)) % 1000)
    .padStart(3, '0');
}

function generateUlpin(geo, lat, lng) {
  const state = STATE_CODES[geo.state] ?? toCode(geo.state);
  const district = toCode(geo.district || geo.city || geo.county);
  const year = new Date().getFullYear();
  const serial = coordSerial(lat, lng);
  return `${state}-${district}-${year}-${serial}`;
}

// Nominatim reverse geocode — called directly for demo/dev use.
// For production: proxy through the Spring Boot backend with caching and
// a proper User-Agent header (Nominatim policy requires identification).
async function reverseGeocode(lat, lng, signal) {
  const res = await fetch(
    `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lng}&format=json&addressdetails=1`,
    {
      signal,
      headers: {
        'Accept-Language': 'en',
        // User-Agent not settable from browsers (CORS restriction).
        // Set it server-side if you proxy this through Spring Boot.
      },
    }
  );
  if (!res.ok) throw new Error(`Geocoding failed (${res.status})`);
  const data = await res.json();
  const addr = data.address || {};
  return {
    state: addr.state || '',
    district: addr.county || addr.state_district || '',
    city: addr.city || addr.town || addr.village || '',
    displayName: data.display_name || '',
  };
}

// ── Component ─────────────────────────────────────────────────────────────────
export default function MapView({ onNavigateRegister }) {
  const mapRef = useRef(null);
  const leafletRef = useRef(null);
  const markerRef = useRef(null);
  // Track latest request to discard out-of-order responses
  const reqIdRef = useRef(0);
  // AbortController for the in-flight geocode fetch
  const abortRef = useRef(null);

  const [clicked, setClicked] = useState(null);
  const [geocode, setGeocode] = useState(null);
  const [ulpin, setUlpin] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const pinIcon = useCallback(() => L.divIcon({
    className: '',
    html: `<div style="width:20px;height:20px;background:#0f2d5a;border:3px solid #fff;
            border-radius:50% 50% 50% 0;transform:rotate(-45deg);
            box-shadow:0 2px 8px rgba(0,0,0,.3)"></div>`,
    iconSize: [20, 20],
    iconAnchor: [10, 20],
  }), []);

  const initMap = useCallback(() => {
    if (leafletRef.current) return;

    const map = L.map(mapRef.current, { zoomControl: true }).setView([22.9, 78.6], 5);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(map);

    map.on('click', async ({ latlng: { lat, lng } }) => {
      // Cancel any in-flight request from a previous click
      if (abortRef.current) abortRef.current.abort();
      abortRef.current = new AbortController();

      // Stamp this request; only apply its result if it's still the latest
      const thisReq = ++reqIdRef.current;

      if (markerRef.current) markerRef.current.setLatLng([lat, lng]);
      else markerRef.current = L.marker([lat, lng], { icon: pinIcon() }).addTo(map);

      setClicked({ lat, lng });
      setGeocode(null);
      setUlpin('');
      setError(null);
      setLoading(true);

      try {
        const geo = await reverseGeocode(lat, lng, abortRef.current.signal);
        // Discard if a newer click already superseded this one
        if (thisReq !== reqIdRef.current) return;
        setGeocode(geo);
        setUlpin(generateUlpin(geo, lat, lng));
      } catch (err) {
        if (err.name === 'AbortError') return; // superseded — silently ignore
        if (thisReq !== reqIdRef.current) return;
        setError('Reverse geocoding failed. Check your connection.');
      } finally {
        if (thisReq === reqIdRef.current) setLoading(false);
      }
    });

    leafletRef.current = map;
  }, [pinIcon]);

  useEffect(() => {
    initMap();
    return () => {
      if (abortRef.current) abortRef.current.abort();
      if (leafletRef.current) {
        leafletRef.current.remove();
        leafletRef.current = null;
        markerRef.current = null;
      }
    };
  }, [initMap]);

  const handleFillRegister = () => {
    if (onNavigateRegister && clicked && ulpin) {
      onNavigateRegister({
        ulpin,
        gpsCoordinates: `${clicked.lat.toFixed(6)},${clicked.lng.toFixed(6)}`,
      });
    }
  };

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Search by Map</h1>
        <p className="page-subtitle">
          Click a location to generate its ULPIN and GPS coordinates
        </p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 300px', gap: 16, height: 520 }}>

        {/* MAP */}
        <div style={{
          borderRadius: 8,
          overflow: 'hidden',
          border: '1px solid var(--border-default)',
          boxShadow: '0 1px 5px rgba(15,45,90,.07)',
        }}>
          <div ref={mapRef} style={{ width: '100%', height: '100%' }} />
        </div>

        {/* PANEL */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>

          {!clicked && (
            <div className="card" style={{
              flex: 1, display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center',
              textAlign: 'center', gap: 14,
            }}>
              <div style={{ fontSize: 36, opacity: 0.18 }}>◎</div>
              <p style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.65 }}>
                Click anywhere on the map to identify a land parcel and generate its ULPIN
              </p>
              <p style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                ULPIN is derived from reverse-geocoded state and district codes
              </p>
            </div>
          )}

          {clicked && (
            <>
              <div className="card" style={{ padding: '16px 18px' }}>
                <div className="card-title" style={{ marginBottom: 13 }}>
                  <span>⌖</span> Selected Location
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 12 }}>
                  <div className="record-field">
                    <span className="record-label">Latitude</span>
                    <span className="record-value mono">{clicked.lat.toFixed(6)}</span>
                  </div>
                  <div className="record-field">
                    <span className="record-label">Longitude</span>
                    <span className="record-value mono">{clicked.lng.toFixed(6)}</span>
                  </div>
                </div>

                {loading && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--text-muted)' }}>
                    <span className="spinner" style={{ borderColor: 'rgba(15,45,90,.2)', borderTopColor: 'var(--navy)' }} />
                    Reverse geocoding...
                  </div>
                )}

                {error && (
                  <div className="alert alert-error fade-in" style={{ marginTop: 8 }}>{error}</div>
                )}

                {geocode && !loading && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <div className="record-field">
                      <span className="record-label">State</span>
                      <span className="record-value">{geocode.state || 'Unknown'}</span>
                    </div>
                    <div className="record-field">
                      <span className="record-label">District</span>
                      <span className="record-value">{geocode.district || geocode.city || 'Unknown'}</span>
                    </div>
                    {geocode.displayName && (
                      <div className="record-field">
                        <span className="record-label" style={{ fontSize: 9 }}>Full Address</span>
                        <span style={{ fontSize: 10.5, color: 'var(--text-muted)', lineHeight: 1.5 }}>
                          {geocode.displayName.length > 120
                            ? geocode.displayName.slice(0, 120) + '…'
                            : geocode.displayName}
                        </span>
                      </div>
                    )}
                  </div>
                )}
              </div>

              {ulpin && !loading && (
                <div className="card" style={{ padding: '16px 18px' }}>
                  <div className="card-title" style={{ marginBottom: 13 }}>
                    <span>⊞</span> Generated ULPIN
                  </div>

                  <div style={{
                    background: 'var(--navy-pale)',
                    border: '1.5px solid var(--border-active)',
                    borderRadius: 5,
                    padding: '11px 14px',
                    marginBottom: 14,
                  }}>
                    <span style={{
                      fontFamily: 'var(--font-mono)',
                      fontSize: 14,
                      fontWeight: 700,
                      color: 'var(--navy)',
                      letterSpacing: '0.05em',
                    }}>
                      {ulpin}
                    </span>
                  </div>

                  <p style={{ fontSize: 11.5, color: 'var(--text-muted)', lineHeight: 1.6, marginBottom: 14 }}>
                    Tap below to open the Register form with ULPIN and GPS pre-filled.
                  </p>

                  <button className="btn btn-primary" style={{ width: '100%' }} onClick={handleFillRegister}>
                    Fill Register Form
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <p style={{ marginTop: 10, fontSize: 11, color: 'var(--text-muted)' }}>
        Map data © OpenStreetMap contributors. Geocoding by Nominatim.
      </p>
    </>
  );
}