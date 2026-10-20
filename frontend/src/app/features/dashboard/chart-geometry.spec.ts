import {
  FRAME,
  anchorOf,
  pathOf,
  scaleOf,
  tickLabel,
  ticksOf,
  toNumbers,
  xOf,
  yOf,
} from './chart-geometry';

describe('chart geometry', () => {
  it('reads the decimal strings only to place points, gaps kept', () => {
    expect(toNumbers(['100.00', undefined, '104.35'])).toEqual([100, undefined, 104.35]);
    expect(toNumbers(['abc', 'Infinity'])).toEqual([undefined, undefined]);
  });

  it('scales every series around the base 100 with a margin', () => {
    expect(scaleOf([[110, 120], [undefined]])).toEqual({ min: 99, max: 121 });
    expect(scaleOf([[]])).toEqual({ min: 99, max: 101 });
  });

  it('chooses round graduations', () => {
    expect(ticksOf({ min: 90, max: 130 })).toEqual([90, 100, 110, 120, 130]);
    expect(ticksOf({ min: 99, max: 106 })).toEqual([100, 102, 104, 106]);
    expect(ticksOf({ min: 99, max: 101 })).toEqual([99, 99.5, 100, 100.5, 101]);
    expect(tickLabel(102.5)).toBe('102,5');
  });

  it('places sessions evenly from left to right and values from top to bottom', () => {
    expect(xOf(0, 3)).toBe(FRAME.left);
    expect(xOf(2, 3)).toBe(FRAME.width - FRAME.right);
    expect(xOf(0, 1)).toBe(FRAME.left);
    const scale = { min: 90, max: 110 };
    expect(yOf(110, scale)).toBe(FRAME.top);
    expect(yOf(90, scale)).toBe(FRAME.height - FRAME.bottom);
  });

  it('breaks the line on a gap instead of joining across it', () => {
    const scale = { min: 90, max: 110 };

    const path = pathOf([100, 110, undefined, 90], scale);

    expect(path).toBe('M 52.0 132.0 L 244.0 12.0 M 628.0 252.0 h 0');
  });

  it('draws an isolated point and a single session as dots', () => {
    const scale = { min: 90, max: 110 };

    expect(pathOf([undefined, 100, undefined], scale)).toBe('M 340.0 132.0 h 0');
    expect(pathOf([100], scale)).toBe('M 52.0 132.0 h 0');
  });

  it('keeps the first and last dates inside the drawing', () => {
    expect([0, 1].map((index) => anchorOf(index, 2))).toEqual(['start', 'end']);
    expect([0, 126, 252].map((index) => anchorOf(index, 253))).toEqual(['start', 'middle', 'end']);
  });
});
