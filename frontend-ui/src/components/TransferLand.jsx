import { useState } from 'react';
import { transferOwnership } from '../services/api';
import LandRecord from './LandRecord';

const INITIAL_FORM = {
  ulpin: '',
  sellerId: '',
  newOwnerId: '',
  newDocumentHash: '',
};

export default function TransferLand() {
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
      const data = await transferOwnership(form.ulpin, {
        newOwnerId: form.newOwnerId,
        sellerId: form.sellerId,
        newDocumentHash: form.newDocumentHash,
      });
      setResult(data);
      setForm(INITIAL_FORM);
    } catch (err) {
      setError(err.message || 'Transfer failed. Check Spring Boot logs for details.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Transfer Ownership</h1>
        <p className="page-subtitle">
          Execute an ownership transfer transaction on the blockchain
        </p>
      </div>

      <div className="known-issue">
        <strong>Known Backend Issue:</strong> The current backend service passes
        empty strings for <code>sellerId</code> and <code>newDocumentHash</code> to
        the chaincode, which validates both as required fields and rejects the
        transaction. Update <code>LandRegistryService.transferOwnership()</code> to
        forward these fields. This form sends them ready for when that fix lands.
      </div>

      <div className="card">
        <div className="card-title">
          <span>⇌</span> Ownership Transfer Form
        </div>

        <form onSubmit={onSubmit} className="form-grid">
          <div className="form-field">
            <label className="field-label">Target ULPIN *</label>
            <input
              className="field-input mono"
              name="ulpin"
              value={form.ulpin}
              onChange={onChange}
              placeholder="MP-JBP-2026-003"
              required
            />
            <span className="field-hint">The parcel being transferred — must be ACTIVE</span>
          </div>

          <div className="form-field">
            <label className="field-label">Seller (Current Owner) Aadhaar *</label>
            <input
              className="field-input"
              name="sellerId"
              value={form.sellerId}
              onChange={onChange}
              placeholder="AADHAR-1122-3344"
              required
            />
            <span className="field-hint">Must match the currentOwnerId on the ledger</span>
          </div>

          <div className="form-field">
            <label className="field-label">New Owner Aadhaar *</label>
            <input
              className="field-input"
              name="newOwnerId"
              value={form.newOwnerId}
              onChange={onChange}
              placeholder="AADHAR-5566-7788"
              required
            />
          </div>

          <div className="form-field">
            <label className="field-label">New Document Hash *</label>
            <input
              className="field-input mono"
              name="newDocumentHash"
              value={form.newDocumentHash}
              onChange={onChange}
              placeholder="QmNewHashAfterTransfer..."
              required
            />
            <span className="field-hint">Updated deed document hash after ownership change</span>
          </div>

          <button
            className="btn btn-danger"
            type="submit"
            disabled={loading}
            style={{ marginTop: 4 }}
          >
            {loading ? (
              <><span className="spinner" /> Executing Transfer...</>
            ) : (
              'Execute Transfer on Ledger'
            )}
          </button>
        </form>

        {result && (
          <div className="alert alert-success fade-in" style={{ marginTop: 16 }}>
            Ownership transferred successfully.
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
