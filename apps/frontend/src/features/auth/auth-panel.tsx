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

type Mode = 'login' | 'register' | 'admin';

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

/**
 * Authentication for the two kinds of users:
 *  - Students log in / register with Firebase email + password (the main login).
 *  - The system administrator signs in with a separate configured username + password.
 */
export function AuthPanel({ onAuthed }: { onAuthed: (account: Account) => void }) {
  const [mode, setMode] = useState<Mode>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [adminUser, setAdminUser] = useState('');
  const [adminPass, setAdminPass] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  function goTo(next: Mode) {
    setMode(next);
    setError('');
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      if (mode === 'admin') {
        // Administrator login does not use Firebase — credentials go straight to the backend.
        const account = await api<Account>('/session/admin-login', 'POST', {
          username: adminUser,
          password: adminPass,
        });
        onAuthed(account);
        return;
      }

      // Student login/register: authenticate with Firebase, then exchange the ID token.
      const auth = getFirebaseAuth();
      const credential =
        mode === 'register'
          ? await createUserWithEmailAndPassword(auth, email, password)
          : await signInWithEmailAndPassword(auth, email, password);
      const idToken = await credential.user.getIdToken();
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

  // Administrator sign-in form.
  if (mode === 'admin') {
    return (
      <form className="auth-form" onSubmit={submit}>
        <div className="auth-heading">
          <strong>Administrator sign-in</strong>
          <span>For system administrators only.</span>
        </div>
        <Field label="Username">
          <input
            type="text"
            required
            autoComplete="username"
            value={adminUser}
            onChange={(e) => setAdminUser(e.target.value)}
          />
        </Field>
        <Field label="Password">
          <input
            type="password"
            required
            autoComplete="current-password"
            value={adminPass}
            onChange={(e) => setAdminPass(e.target.value)}
          />
        </Field>
        <button className="button wide" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in as administrator'}
        </button>
        {error && (
          <div role="alert" className="notice error">
            {error}
          </div>
        )}
        <button
          type="button"
          className="auth-switch"
          onClick={() => goTo('login')}
        >
          ← Back to student login
        </button>
      </form>
    );
  }

  // Student login / register form.
  const register = mode === 'register';
  return (
    <form className="auth-form" onSubmit={submit}>
      <div className="auth-tabs" role="tablist" aria-label="Login or register">
        <button
          type="button"
          role="tab"
          aria-selected={!register}
          className={!register ? 'selected' : ''}
          onClick={() => goTo('login')}
        >
          Log in
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={register}
          className={register ? 'selected' : ''}
          onClick={() => goTo('register')}
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

      <button
        type="button"
        className="auth-switch"
        onClick={() => goTo('admin')}
      >
        System administrator sign-in →
      </button>
    </form>
  );
}
