'use client';

import { FormEvent, useState } from 'react';
import {
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signOut,
} from 'firebase/auth';
import { getFirebaseAuth, firebaseConfigured } from '@/lib/firebase';
import { api } from '@/lib/study-buddy-api';
import { Account } from '@/lib/study-buddy-types';
import { Field } from '@/components/ui';

type Mode = 'login' | 'register';

/** Turns Firebase's error codes into messages a student can act on. */
function friendlyMessage(error: unknown): string {
  const code =
    typeof error === 'object' && error && 'code' in error
      ? String((error as { code: unknown }).code)
      : '';
  switch (code) {
    case 'auth/invalid-email':
      return 'That email address looks invalid.';
    case 'auth/missing-password':
      return 'Please enter your password.';
    case 'auth/weak-password':
      return 'Choose a password with at least 6 characters.';
    case 'auth/email-already-in-use':
      return 'An account with this email already exists. Try logging in instead.';
    case 'auth/invalid-credential':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
      return 'Incorrect email or password.';
    case 'auth/network-request-failed':
      return 'Network error reaching Firebase. Check your connection.';
    case 'auth/invalid-api-key':
    case 'auth/api-key-not-valid.-please-pass-a-valid-api-key.':
      return 'Firebase is not configured correctly. Check the NEXT_PUBLIC_FIREBASE_* values.';
    case 'auth/operation-not-allowed':
      return 'Email/password sign-in is disabled in the Firebase console. Enable it under Authentication → Sign-in method.';
    default:
      return error instanceof Error
        ? error.message
        : 'Something went wrong. Please try again.';
  }
}

/** Email/password login and registration backed by Firebase Auth + the backend session. */
export function AuthPanel({ onAuthed }: { onAuthed: (account: Account) => void }) {
  const [mode, setMode] = useState<Mode>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const auth = getFirebaseAuth();
      // 1. Authenticate with Firebase (in the browser) to obtain an ID token.
      const credential =
        mode === 'register'
          ? await createUserWithEmailAndPassword(auth, email, password)
          : await signInWithEmailAndPassword(auth, email, password);
      const idToken = await credential.user.getIdToken();

      // 2. Hand the token to the backend, which verifies it and creates a server session.
      try {
        const account =
          mode === 'register'
            ? await api<Account>('/session/signup', 'POST', {
                idToken,
                name: name.trim(),
              })
            : await api<Account>('/session/login', 'POST', { idToken });
        onAuthed(account);
      } catch (backendError) {
        // The Firebase user exists but the backend rejected the session; don't leave a
        // half-signed-in client that can never reach the workspace.
        await signOut(auth).catch(() => undefined);
        throw backendError;
      }
    } catch (e) {
      setError(friendlyMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const register = mode === 'register';

  return (
    <form className="auth-form" onSubmit={submit}>
      <div className="auth-tabs" role="tablist" aria-label="Login or register">
        <button
          type="button"
          role="tab"
          aria-selected={!register}
          className={!register ? 'selected' : ''}
          onClick={() => {
            setMode('login');
            setError('');
          }}
        >
          Log in
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={register}
          className={register ? 'selected' : ''}
          onClick={() => {
            setMode('register');
            setError('');
          }}
        >
          Register
        </button>
      </div>

      {register && (
        <Field label="Your name">
          <input
            type="text"
            required
            autoComplete="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Avery Tan"
          />
        </Field>
      )}
      <Field label="Email">
        <input
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
        />
      </Field>
      <Field
        label="Password"
        hint={register ? 'At least 6 characters.' : undefined}
      >
        <input
          type="password"
          required
          autoComplete={register ? 'new-password' : 'current-password'}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </Field>

      <button className="button wide" disabled={busy || !firebaseConfigured}>
        {busy
          ? register
            ? 'Creating your account…'
            : 'Logging you in…'
          : register
            ? 'Create account'
            : 'Log in'}
      </button>

      {!firebaseConfigured && (
        <div role="alert" className="notice error">
          Firebase is not configured yet. Add your web app keys to{' '}
          <code>apps/frontend/.env</code> and restart the frontend.
        </div>
      )}
      {error && (
        <div role="alert" className="notice error">
          {error}
        </div>
      )}
    </form>
  );
}
