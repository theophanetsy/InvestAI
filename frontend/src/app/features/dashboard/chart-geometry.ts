/**
 * Geometry of the base-100 curve, drawn in SVG without a chart library. The values of the backend
 * are decimal strings; they become numbers here only to place pixels, never for a figure shown to
 * the user (legend, description and data table keep the strings). The graduations are generated
 * round numbers, not data.
 */

/** Drawing area, in SVG user units (the SVG scales to its container). */
export const FRAME = { width: 640, height: 280, left: 52, right: 12, top: 12, bottom: 28 } as const;

/** A value per session, `undefined` on a gap (no close: never interpolated). */
export type SeriesValues = readonly (string | undefined)[];

export interface Scale {
  readonly min: number;
  readonly max: number;
}

/** Numbers of a series, for the geometry only; an unreadable value is a gap. */
export function toNumbers(values: SeriesValues): (number | undefined)[] {
  return values.map((value) => {
    const number = value === undefined ? Number.NaN : Number(value);
    return Number.isFinite(number) ? number : undefined;
  });
}

/** Vertical range of every series, with some margin, always including the base 100. */
export function scaleOf(series: readonly (number | undefined)[][]): Scale {
  const known = series.flat().filter((value): value is number => value !== undefined);
  const min = Math.min(100, ...known);
  const max = Math.max(100, ...known);
  const margin = Math.max((max - min) * 0.05, 1);
  return { min: min - margin, max: max + margin };
}

/** About five round graduations (step 1, 2, 2.5 or 5 × 10ⁿ) within the scale. */
export function ticksOf(scale: Scale, count = 5): number[] {
  const raw = (scale.max - scale.min) / count;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step =
    [1, 2, 2.5, 5, 10].map((factor) => factor * magnitude).find((candidate) => candidate >= raw) ??
    10 * magnitude;
  const ticks: number[] = [];
  for (let tick = Math.ceil(scale.min / step) * step; tick <= scale.max; tick += step) {
    ticks.push(Number(tick.toFixed(6)));
  }
  return ticks;
}

/** Horizontal position of the session `index` among `count` (sessions evenly spaced). */
export function xOf(index: number, count: number): number {
  const width = FRAME.width - FRAME.left - FRAME.right;
  return FRAME.left + (count <= 1 ? 0 : (index * width) / (count - 1));
}

/** Vertical position of a value. */
export function yOf(value: number, scale: Scale): number {
  const height = FRAME.height - FRAME.top - FRAME.bottom;
  return FRAME.top + ((scale.max - value) * height) / (scale.max - scale.min);
}

/**
 * SVG path of a series: a new segment starts after each gap. An isolated point (between two gaps,
 * or a single session) gets a zero-length segment, drawn as a dot by the round line caps: a lone
 * `M` would not be drawn at all.
 */
export function pathOf(values: readonly (number | undefined)[], scale: Scale): string {
  const parts: string[] = [];
  values.forEach((value, index) => {
    if (value === undefined) {
      return;
    }
    const point = `${xOf(index, values.length).toFixed(1)} ${yOf(value, scale).toFixed(1)}`;
    const starts = values[index - 1] === undefined;
    const isolated = starts && values[index + 1] === undefined;
    parts.push(`${starts ? 'M' : 'L'} ${point}${isolated ? ' h 0' : ''}`);
  });
  return parts.join(' ');
}

/** Text anchor of a date label: the first and last stay inside the drawing. */
export function anchorOf(index: number, count: number): 'start' | 'middle' | 'end' {
  if (index === 0) {
    return 'start';
  }
  return index === count - 1 ? 'end' : 'middle';
}

/** Graduation label in French: `102,5`. */
export function tickLabel(tick: number): string {
  return String(tick).replace('.', ',');
}
