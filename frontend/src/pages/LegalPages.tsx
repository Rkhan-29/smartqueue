import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

/**
 * Plain-language Terms and Privacy pages describing what SmartQueue actually does.
 * The organization running a deployment must review these with its own counsel and set
 * VITE_OPERATOR_NAME / VITE_CONTACT_EMAIL so people know who to contact.
 */
const operator = import.meta.env.VITE_OPERATOR_NAME || 'the organization that runs this queue';
const contact = import.meta.env.VITE_CONTACT_EMAIL || '';
const updated = '13 October 2026';

function Page({ title, children }: { title: string; children: ReactNode }) {
  return (
    <article className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="text-5xl">{title}</h1>
      <p className="mt-2 text-ink-500">Last updated {updated}</p>
      <div className="mt-6 space-y-6 text-[17px] leading-relaxed [&_h2]:mb-2 [&_h2]:text-3xl [&_ul]:ml-5 [&_ul]:list-disc [&_ul]:space-y-1">
        {children}
      </div>
      <p className="mt-10 text-ink-500">
        <Link to="/privacy" className="underline underline-offset-4">
          Privacy
        </Link>
        {' · '}
        <Link to="/terms" className="underline underline-offset-4">
          Terms
        </Link>
      </p>
    </article>
  );
}

function Contact() {
  return contact ? (
    <>
      Write to{' '}
      <a href={`mailto:${contact}`} className="underline underline-offset-4">
        {contact}
      </a>
      .
    </>
  ) : (
    <>Ask the staff at the venue where you joined the queue.</>
  );
}

export function PrivacyPage() {
  return (
    <Page title="Privacy">
      <p>
        SmartQueue is run by {operator}. This page says what the service stores about you and why. It collects only what is
        needed to hold your place in line and to keep the service secure.
      </p>
      <section>
        <h2>What we store</h2>
        <ul>
          <li>Your token: its number, queue, status and the times it was issued, called and finished.</li>
          <li>A name, only if you choose to enter one when joining.</li>
          <li>For guests, a random session identifier. It is not linked to a real identity.</li>
          <li>For accounts, your name, email address and a hashed password. Passwords are never stored in readable form.</li>
          <li>Your IP address, used for rate limiting and kept in audit records of staff and admin actions.</li>
        </ul>
      </section>
      <section>
        <h2>What is kept in your browser</h2>
        <p>
          Your browser stores your sign-in tokens, a short list of your recent tokens so you can find them again, and the counter a
          staff member last chose. There are no advertising or tracking cookies.
        </p>
      </section>
      <section>
        <h2>How it is used</h2>
        <p>
          To run the queue, show you your position and an estimated wait, tell you when you are called, and produce anonymous
          wait-time statistics so the venue can plan staffing. Wait times are estimates. Your data is not sold.
        </p>
      </section>
      <section>
        <h2>Who can see it</h2>
        <p>
          Anyone who has your token link can view that token, so do not share it publicly. Staff see the tokens of queues they
          are assigned to. Administrators of the organization see tokens, accounts and audit records for their organization.
        </p>
      </section>
      <section>
        <h2>Retention and your choices</h2>
        <p>
          The operator decides how long token and audit records are kept. You can ask to see, correct or delete your account data.
          <br />
          <Contact />
        </p>
      </section>
    </Page>
  );
}

export function TermsPage() {
  return (
    <Page title="Terms of use">
      <p>
        By using SmartQueue you agree to these terms. The service is provided by {operator} to manage waiting lines at its
        locations.
      </p>
      <section>
        <h2>Using the queue</h2>
        <ul>
          <li>A token holds a place in line. It is not a booking, an appointment or a guarantee of service by a certain time.</li>
          <li>Estimated wait times are estimates and change as the queue moves.</li>
          <li>Be at the venue when you are called. Tokens that are not answered can be skipped or marked as no-show.</li>
          <li>Staff may issue priority tokens, pause a queue or move tokens as the venue&rsquo;s rules require.</li>
          <li>Join once per queue and do not create tokens for people who are not present.</li>
        </ul>
      </section>
      <section>
        <h2>Accounts</h2>
        <p>
          Keep your password private. Staff and administrator accounts are for the named person only. Repeated failed sign-ins
          lock an account for a short time.
        </p>
      </section>
      <section>
        <h2>Acceptable use</h2>
        <p>
          Do not try to disrupt the service, bypass its limits, access another person&rsquo;s data or automate joining queues.
          Access may be restricted if you do.
        </p>
      </section>
      <section>
        <h2>Availability and liability</h2>
        <p>
          The service is provided as it is. Outages or delays can happen, and the venue&rsquo;s staff decide who is served. To the
          extent the law allows, the operator is not liable for losses caused by waiting time or service interruptions.
        </p>
      </section>
      <section>
        <h2>Questions</h2>
        <p>
          <Contact /> See also the <Link to="/privacy" className="underline underline-offset-4">Privacy</Link> page.
        </p>
      </section>
    </Page>
  );
}
