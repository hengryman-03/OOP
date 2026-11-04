/** All dates are shown as dd/mm/yyyy, whatever the browser's locale is. */

const pad = (n: number) => String(n).padStart(2, '0');

/** '2026-11-18' -> '18/11/2026'. Plain string work, so no timezone shifts. */
export function formatDate(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-');
  return y && m && d ? `${d}/${m}/${y}` : iso;
}

/** An ISO instant such as '2026-11-04T06:22:39Z' -> '04/11/2026'. */
export function formatInstantDate(instant: string): string {
  const t = new Date(instant);
  return Number.isNaN(t.getTime())
    ? instant
    : `${pad(t.getDate())}/${pad(t.getMonth() + 1)}/${t.getFullYear()}`;
}

/** An ISO instant -> '04/11/2026, 14:05'. */
export function formatInstantDateTime(instant: string): string {
  const t = new Date(instant);
  return Number.isNaN(t.getTime())
    ? instant
    : `${formatInstantDate(instant)}, ${pad(t.getHours())}:${pad(t.getMinutes())}`;
}

/** 'dd/mm/yyyy' -> '2026-11-18', or null when it is not a real calendar date. */
export function parseDate(text: string): string | null {
  const match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(text.trim());
  if (!match) return null;
  const [d, m, y] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const check = new Date(y, m - 1, d);
  if (
    check.getFullYear() !== y ||
    check.getMonth() !== m - 1 ||
    check.getDate() !== d
  )
    return null;
  return `${y}-${pad(m)}-${pad(d)}`;
}
