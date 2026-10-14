/**
 * Firebase client (browser) setup. This is the PUBLIC web config — safe to expose — and is
 * separate from the backend service-account key, which stays server-side. Values come from
 * NEXT_PUBLIC_FIREBASE_* environment variables (see .env.example).
 */
import { initializeApp, getApps, getApp, FirebaseOptions } from 'firebase/app';
import { Auth, getAuth } from 'firebase/auth';

const firebaseConfig: FirebaseOptions = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

/** True when the web config has been filled in. Lets the UI show a helpful setup message. */
export const firebaseConfigured = Boolean(
  firebaseConfig.apiKey && firebaseConfig.projectId && firebaseConfig.appId,
);

let cachedAuth: Auth | null = null;

/**
 * Returns the Firebase Auth instance, initializing the app on first use. Deferred (not created
 * at import time) so an unconfigured project renders a setup message instead of crashing — and
 * so it never initializes during server-side rendering.
 */
export function getFirebaseAuth(): Auth {
  if (!firebaseConfigured) {
    throw new Error(
      'Firebase is not configured. Add NEXT_PUBLIC_FIREBASE_* values to apps/frontend/.env.',
    );
  }
  if (!cachedAuth) {
    const app = getApps().length ? getApp() : initializeApp(firebaseConfig);
    cachedAuth = getAuth(app);
  }
  return cachedAuth;
}
