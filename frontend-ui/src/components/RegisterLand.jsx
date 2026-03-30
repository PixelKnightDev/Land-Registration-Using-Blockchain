import { useState } from 'react';
import { registerLand } from '../services/api';
import LandRecord from './LandRecord';

const INITIAL_FORM = {
  ulpin: '',
  gpsCoordinates: '',
  parentUlpin: 'NONE',
  currentOwnerId: '',
  documentHash: '',
};

export default function RegisterLand() {
  const [form, setForm] = useState(INITIAL_FORM);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const onChange = (e) =>
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const onSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResult(null);
    setError(null);

    try {
      const data = await registerLand(form);
      setResult(data);
      setForm(INITIAL_FORM);
    } catch (err) {
      setError(err.message || 'Transaction failed. Check Spring Boot logs.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Register Land Parcel</h1>
        <p className="page-subtitle">
          Submit a new land asset to the Hyperledger Fabric ledger
        </p>
      </div>

      <div className="card">
        <div className="card-title">
          <span>⊕</span> Parcel Registration Form
        </div>

        <form onSubmit={onSubmit} className="form-grid">
          <div className="form-grid form-grid-2">
            <div className="form-field">
              <label className="field-label">ULPIN *</label>
              <input
                className="field-input mono"
                name="ulpin"
                value={form.ulpin}
                onChange={onChange}
                placeholder="MP-JBP-2026-003"
                required
              />
              <span className="field-hint">Unique Land Parcel ID — must not already exist on ledger</span>
            </div>

            <div className="form-field">
              <label className="field-label">Parent ULPIN</label>
              <input
                className="field-input mono"
                name="parentUlpin"
                value={form.parentUlpin}
                onChange={onChange}
                placeholder="NONE"
              />
              <span className="field-hint">Leave as NONE for a root (unsplit) parcel</span>
            </div>
          </div>

          <div className="form-field">
            <label className="field-label">GPS Coordinates *</label>
            <input
              className="field-input mono"
              name="gpsCoordinates"
              value={form.gpsCoordinates}
              onChange={onChange}
              placeholder="23.1765,79.9559"
              required
            />
          </div>

          <div className="form-field">
            <label className="field-label">Owner Aadhaar ID *</label>
            <input
              className="field-input"
              name="currentOwnerId"
              value={form.currentOwnerId}
              onChange={onChange}
              placeholder="AADHAR-1122-3344"
              required
            />
          </div>

          <div className="form-field">
            <label className="field-label">Document Hash (IPFS CID / SHA-256) *</label>
            <input
              className="field-input mono"
              name="documentHash"
              value={form.documentHash}
              onChange={onChange}
              placeholder="QmXoypizjW3WknFiJnKLwHCnL72vedxjQkDDP1mXWo6uco"
              required
            />
            <span className="field-hint">SHA-256 hash or IPFS CID of the physical deed document</span>
          </div>

          <button
            className="btn btn-primary"
            type="submit"
            disabled={loading}
            style={{ marginTop: 4 }}
          >
            {loading ? (
              <><span className="spinner" /> Submitting to Ledger...</>
            ) : (
              'Register on Ledger'
            )}
          </button>
        </form>

        {result && (
          <div className="alert alert-success fade-in" style={{ marginTop: 16 }}>
            Asset registered successfully.
          </div>
        )}

        {error && (
          <div className="alert alert-error fade-in" style={{ marginTop: 16 }}>
            {error}
          </div>
        )}
      </div>

      {result && <LandRecord data={result} />}
    </>
  );
}
