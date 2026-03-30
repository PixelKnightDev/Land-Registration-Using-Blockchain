import { useState } from 'react';
import { mutateLand } from '../services/api';

const INITIAL_FORM = {
  parentUlpin: '',
  currentOwnerId: '',
  child1Ulpin: '',
  child1Gps: '',
  child2Ulpin: '',
  child2Gps: '',
  newDocumentHash: '',
};

export default function MutateLand() {
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
      const data = await mutateLand(form.parentUlpin, {
        currentOwnerId: form.currentOwnerId,
        newUlpin1: form.child1Ulpin,
        newDimensions1: form.child1Gps,
        newUlpin2: form.child2Ulpin,
        newDimensions2: form.child2Gps,
        newDocumentHash: form.newDocumentHash,
      });
      setResult(typeof data === 'string' ? data : JSON.stringify(data, null, 2));
      setForm(INITIAL_FORM);
    } catch (err) {
      setError(err.message || 'Mutation failed. Check Spring Boot logs for details.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Mutate / Split Parcel</h1>
        <p className="page-subtitle">
          Retire a parent parcel and create two child sub-parcels from it
        </p>
      </div>

      <div className="known-issue">
        <strong>Known Backend Issue:</strong> The backend service currently passes
        only 5 arguments to the chaincode <code>mutateLand</code> function, which
        requires 7 (parentUlpin, currentOwnerId, child1Ulpin, child1Gps, child2Ulpin,
        child2Gps, newDocumentHash). Update
        <code> LandRegistryService.mutateLand()</code> and the
        <code> MutateRequest</code> DTO to forward all 7 parameters before using this
        endpoint.
      </div>

      <div className="card">
        <div className="card-title">
          <span>⊗</span> Parcel Mutation Form
        </div>

        <form onSubmit={onSubmit} className="form-grid">
          <div className="form-grid form-grid-2">
            <div className="form-field">
              <label className="field-label">Parent ULPIN *</label>
              <input
                className="field-input mono"
                name="parentUlpin"
                value={form.parentUlpin}
                onChange={onChange}
                placeholder="MP-JBP-2026-003"
                required
              />
              <span className="field-hint">Parcel to be split — will be retired as RETIRED_MUTATED</span>
            </div>

            <div className="form-field">
              <label className="field-label">Current Owner Aadhaar *</label>
              <input
                className="field-input"
                name="currentOwnerId"
                value={form.currentOwnerId}
                onChange={onChange}
                placeholder="AADHAR-1122-3344"
                required
              />
            </div>
          </div>

          <div
            style={{
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius)',
              padding: '16px',
              display: 'grid',
              gap: 14,
            }}
          >
            <div
              style={{
                fontSize: 10,
                letterSpacing: '0.15em',
                textTransform: 'uppercase',
                color: 'var(--text-muted)',
                marginBottom: -4,
              }}
            >
              Child Parcel A
            </div>

            <div className="form-grid form-grid-2">
              <div className="form-field">
                <label className="field-label">Child 1 ULPIN *</label>
                <input
                  className="field-input mono"
                  name="child1Ulpin"
                  value={form.child1Ulpin}
                  onChange={onChange}
                  placeholder="MP-JBP-2026-003-A"
                  required
                />
              </div>

              <div className="form-field">
                <label className="field-label">Child 1 GPS *</label>
                <input
                  className="field-input mono"
                  name="child1Gps"
                  value={form.child1Gps}
                  onChange={onChange}
                  placeholder="23.1765,79.9559 to 23.1770,79.9560"
                  required
                />
              </div>
            </div>
          </div>

          <div
            style={{
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius)',
              padding: '16px',
              display: 'grid',
              gap: 14,
            }}
          >
            <div
              style={{
                fontSize: 10,
                letterSpacing: '0.15em',
                textTransform: 'uppercase',
                color: 'var(--text-muted)',
                marginBottom: -4,
              }}
            >
              Child Parcel B
            </div>

            <div className="form-grid form-grid-2">
              <div className="form-field">
                <label className="field-label">Child 2 ULPIN *</label>
                <input
                  className="field-input mono"
                  name="child2Ulpin"
                  value={form.child2Ulpin}
                  onChange={onChange}
                  placeholder="MP-JBP-2026-003-B"
                  required
                />
              </div>

              <div className="form-field">
                <label className="field-label">Child 2 GPS *</label>
                <input
                  className="field-input mono"
                  name="child2Gps"
                  value={form.child2Gps}
                  onChange={onChange}
                  placeholder="23.1770,79.9560 to 23.1775,79.9565"
                  required
                />
              </div>
            </div>
          </div>

          <div className="form-field">
            <label className="field-label">New Document Hash *</label>
            <input
              className="field-input mono"
              name="newDocumentHash"
              value={form.newDocumentHash}
              onChange={onChange}
              placeholder="QmMutationDeedHash..."
              required
            />
            <span className="field-hint">Updated survey deed reflecting the split</span>
          </div>

          <button
            className="btn btn-danger"
            type="submit"
            disabled={loading}
            style={{ marginTop: 4 }}
          >
            {loading ? (
              <><span className="spinner" /> Executing Mutation...</>
            ) : (
              'Execute Parcel Split'
            )}
          </button>
        </form>

        {result && (
          <div className="alert alert-success fade-in" style={{ marginTop: 16 }}>
            {result}
          </div>
        )}

        {error && (
          <div className="alert alert-error fade-in" style={{ marginTop: 16 }}>
            {error}
          </div>
        )}
      </div>
    </>
  );
}
