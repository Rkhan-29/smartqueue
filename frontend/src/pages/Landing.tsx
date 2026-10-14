import { Link } from 'react-router-dom';
import { Button } from '../components/ui';
import { Ticket } from '../components/Ticket';

const steps = [
  { title: 'Pick your service', text: 'Choose the organization, branch and service, or scan the QR code at the entrance.' },
  { title: 'Get a token', text: 'You receive a numbered token on your phone straight away. No account needed.' },
  { title: 'Wait wherever you like', text: 'See who is being served, how many people are ahead and roughly how long it will take.' },
  { title: 'Walk up when called', text: 'Your screen tells you the counter to go to the moment your number comes up.' },
];

const benefits = [
  { title: 'No standing in line', text: 'Sit down, step outside or finish an errand. Your place in line is held.' },
  { title: 'Honest estimates', text: 'We show a range based on how long recent visits took, and say so when it is a rough guess.' },
  { title: 'Live updates', text: 'Your page updates the moment staff call a number. No refreshing.' },
  { title: 'Priority when needed', text: 'Staff can issue priority tokens for urgent cases, following rules each queue sets.' },
];

export default function Landing() {
  return (
    <>
      <section className="border-b border-paper-300/70">
        <div className="mx-auto grid max-w-6xl items-center gap-10 px-4 py-12 md:grid-cols-[1.1fr_0.9fr] md:py-20">
          <div>
            <h1 className="text-[48px] sm:text-[68px]">Join the line from your phone.</h1>
            <p className="mt-5 max-w-lg text-xl text-ink-500">
              SmartQueue gives hospitals, banks, offices and colleges a digital token line. Join from your phone, see your turn
              approach, and walk up when you are called.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link to="/join">
                <Button size="lg" variant="call">
                  Join a queue
                </Button>
              </Link>
              <Link to="/token">
                <Button size="lg" variant="secondary">
                  Check my token
                </Button>
              </Link>
            </div>
            <p className="mt-4 text-ink-500">
              At the venue already?{' '}
              <Link to="/scan" className="font-semibold text-ink underline underline-offset-4">
                Scan the queue QR code
              </Link>
            </p>
          </div>

          <figure className="mx-auto w-full max-w-[360px] animate-slidein" aria-label="Example of a SmartQueue token">
            <Ticket
              tokenNumber="A023"
              caption="Example token"
              stub={
                <dl className="grid grid-cols-2 gap-3 text-left">
                  <div>
                    <dt className="text-sm text-ink-500">People ahead</dt>
                    <dd className="font-display text-3xl font-bold">4</dd>
                  </div>
                  <div>
                    <dt className="text-sm text-ink-500">Estimated wait</dt>
                    <dd className="font-display text-3xl font-bold">~12 min</dd>
                  </div>
                </dl>
              }
            />
            <figcaption className="mt-3 text-center text-sm text-ink-500">Your token page looks like this, and updates by itself.</figcaption>
          </figure>
        </div>
      </section>

      <section className="mx-auto grid max-w-6xl gap-8 px-4 py-14 md:grid-cols-[1fr_2fr]">
        <h2 className="text-5xl">What happens when you join</h2>
        <ol className="divide-y divide-paper-300">
          {steps.map((s, i) => (
            <li key={s.title} className="flex gap-5 py-4 first:pt-0">
              <span className="w-8 shrink-0 font-display text-3xl font-bold text-ink-400">{i + 1}.</span>
              <p>
                <span className="font-semibold">{s.title}.</span> <span className="text-ink-500">{s.text}</span>
              </p>
            </li>
          ))}
        </ol>
      </section>

      <section className="bg-ink text-white">
        <div className="mx-auto grid max-w-6xl gap-10 px-4 py-14 md:grid-cols-2">
          <div>
            <h2 className="text-5xl">Scan once, you are in line</h2>
            <p className="mt-4 max-w-md text-lg text-ink-300">
              Every queue has its own QR code at the entrance or on the counter. Point your camera at it and the join page opens
              for that exact queue. The code holds a public link only, nothing about you or the system.
            </p>
            <Link to="/scan" className="mt-6 inline-block">
              <Button variant="call" size="lg">
                Open the scanner
              </Button>
            </Link>
          </div>
          <dl className="content-center divide-y divide-ink-700">
            {benefits.map((b) => (
              <div key={b.title} className="py-3 first:pt-0">
                <dt className="text-xl font-semibold">{b.title}</dt>
                <dd className="text-ink-300">{b.text}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 py-10">
        <p className="text-lg text-ink-500">
          Work at a service desk? Staff call and complete tokens from one screen, and admins set up branches, counters and priority
          rules.{' '}
          <Link to="/login" className="font-semibold text-ink underline underline-offset-4">
            Staff and admin sign in
          </Link>
        </p>
      </section>
    </>
  );
}
