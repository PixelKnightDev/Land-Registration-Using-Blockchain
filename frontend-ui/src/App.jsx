import { useState } from 'react';
import RegisterLand from './components/RegisterLand';
import SearchLand from './components/SearchLand';
import OwnerSearch from './components/OwnerSearch';
import TransferLand from './components/TransferLand';
import MutateLand from './components/MutateLand';
import AssetHistory from './components/AssetHistory';
import MapView from './components/Mapview';
import './index.css';

const NAV_ITEMS = [
  { id: 'register', label: 'Register Parcel', icon: '⊕' },
  { id: 'search', label: 'Search by ULPIN', icon: '◎' },
  { id: 'owner', label: 'Search by Owner', icon: '◈' },
  { id: 'map', label: 'Search by Map', icon: '⊞' },
  { id: 'transfer', label: 'Transfer Ownership', icon: '⇌' },
  { id: 'mutate', label: 'Mutate / Split', icon: '⊗' },
  { id: 'history', label: 'Audit Trail', icon: '◷' },
];

export default function App() {
  const [active, setActive] = useState('register');
  const [prefill, setPrefill] = useState(null);

  const navigateTo = (page, data = null) => {
    setPrefill(data);
    setActive(page);
  };

  const renderPage = () => {
    switch (active) {
      case 'register': return <RegisterLand prefill={prefill} onPrefillUsed={() => setPrefill(null)} />;
      case 'search': return <SearchLand />;
      case 'owner': return <OwnerSearch />;
      case 'map': return <MapView onNavigateRegister={(data) => navigateTo('register', data)} />;
      case 'transfer': return <TransferLand />;
      case 'mutate': return <MutateLand />;
      case 'history': return <AssetHistory />;
      default: return null;
    }
  };

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="tricolor-bar" />
        <div className="sidebar-logo">
          <div className="logo-emblem">⊛</div>
          <div className="logo-name">BHUMI REGISTRY</div>
          <div className="logo-dept">Ministry of Rural Development</div>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-section-label">Operations</div>
          {NAV_ITEMS.map(({ id, label, icon }) => (
            <div
              key={id}
              className={`nav-item${active === id ? ' active' : ''}`}
              onClick={() => navigateTo(id)}
            >
              <span className="nav-icon">{icon}</span>
              <span>{label}</span>
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="network-status">
            <div className="status-dot" />
            <span>Fabric · landchannel</span>
          </div>
        </div>
      </aside>

      <main className="main-content" key={active}>
        {renderPage()}
      </main>
    </div>
  );
}