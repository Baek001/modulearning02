import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'

if (window.location.hostname === 'modulearning02.pages.dev') {
  const target = new URL(window.location.href)
  target.hostname = 'modulearning02-api.stevenbaek.workers.dev'
  window.location.replace(target.toString())
} else {
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}
