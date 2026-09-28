// Synthesized audio via the WebAudio API — no asset files. Provides short SFX
// for combat/UI and a light generative ambient bed. All volumes adjustable.

type SfxName =
  | "select"
  | "command"
  | "build"
  | "complete"
  | "sword"
  | "bow"
  | "arrowHit"
  | "siege"
  | "collapse"
  | "death"
  | "alert"
  | "ui"
  | "coin"
  | "tick"
  | "reveal"
  | "levelup";

export class Audio {
  ctx: AudioContext | null = null;
  master!: GainNode;
  sfxGain!: GainNode;
  musicGain!: GainNode;
  masterVol = 0.8;
  sfxVol = 0.7;
  musicVol = 0.35;
  muted = false;
  private musicTimer = 0;
  private started = false;

  /** Must be called from a user gesture (browser autoplay policy). */
  ensure() {
    if (this.ctx) return;
    const AC = (window.AudioContext || (window as any).webkitAudioContext) as typeof AudioContext;
    this.ctx = new AC();
    this.master = this.ctx.createGain();
    this.sfxGain = this.ctx.createGain();
    this.musicGain = this.ctx.createGain();
    this.sfxGain.connect(this.master);
    this.musicGain.connect(this.master);
    this.master.connect(this.ctx.destination);
    this.applyVolumes();
  }

  applyVolumes() {
    if (!this.ctx) return;
    this.master.gain.value = this.muted ? 0 : this.masterVol;
    this.sfxGain.gain.value = this.sfxVol;
    this.musicGain.gain.value = this.musicVol;
  }

  resume() {
    this.ensure();
    if (this.ctx && this.ctx.state === "suspended") this.ctx.resume();
  }

  private env(
    osc: OscillatorNode,
    gain: GainNode,
    t0: number,
    attack: number,
    decay: number,
    peak: number,
  ) {
    gain.gain.setValueAtTime(0.0001, t0);
    gain.gain.linearRampToValueAtTime(peak, t0 + attack);
    gain.gain.exponentialRampToValueAtTime(0.0001, t0 + attack + decay);
    osc.start(t0);
    osc.stop(t0 + attack + decay + 0.02);
  }

  /** Small random multiplier so repeated SFX don't sound mechanical. */
  private vary(amt = 0.06): number {
    return 1 + (Math.random() * 2 - 1) * amt;
  }

  private tone(
    freq: number,
    type: OscillatorType,
    attack: number,
    decay: number,
    peak: number,
    freqEnd?: number,
    delay = 0,
  ) {
    if (!this.ctx) return;
    const t0 = this.ctx.currentTime + delay;
    const osc = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t0);
    if (freqEnd !== undefined) osc.frequency.exponentialRampToValueAtTime(Math.max(20, freqEnd), t0 + attack + decay);
    osc.connect(g);
    g.connect(this.sfxGain);
    this.env(osc, g, t0, attack, decay, peak);
  }

  private noise(duration: number, peak: number, filterFreq: number, type: BiquadFilterType = "bandpass", delay = 0) {
    if (!this.ctx) return;
    const t0 = this.ctx.currentTime + delay;
    const len = Math.floor(this.ctx.sampleRate * duration);
    const buf = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
    const src = this.ctx.createBufferSource();
    src.buffer = buf;
    const filt = this.ctx.createBiquadFilter();
    filt.type = type;
    filt.frequency.value = filterFreq;
    const g = this.ctx.createGain();
    g.gain.setValueAtTime(peak, t0);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + duration);
    src.connect(filt);
    filt.connect(g);
    g.connect(this.sfxGain);
    src.start(t0);
    src.stop(t0 + duration);
  }

  play(name: SfxName) {
    if (!this.ctx || this.muted) return;
    const v = this.vary();
    switch (name) {
      case "select": this.tone(620 * v, "triangle", 0.005, 0.07, 0.25); break;
      case "command": this.tone(420 * v, "triangle", 0.005, 0.08, 0.22, 520 * v); break;
      case "build":
        // Two quick wooden knocks.
        this.noise(0.08, 0.16, 800, "lowpass");
        this.noise(0.1, 0.14, 700, "lowpass", 0.09);
        break;
      case "complete": this.tone(523, "sine", 0.01, 0.18, 0.3, 784); this.tone(784, "sine", 0.02, 0.22, 0.16, 1046, 0.06); break;
      case "sword": {
        // Metallic clash. The 'metal' comes from INHARMONIC partials (ratios
        // 1 : 1.51 : 2.19, not whole numbers) ringing out over a bright noise
        // transient, with a low thunk underneath for weight.
        const b = this.vary(0.09);
        this.noise(0.05, 0.26, 5200 * b, "highpass");          // bright 'shing' transient
        this.noise(0.07, 0.18, 3000 * b, "bandpass", 0.004);   // blade scrape body
        this.tone(2100 * b, "triangle", 0.001, 0.17, 0.11, 1500 * b, 0.004); // clang partial 1
        this.tone(3170 * b, "triangle", 0.001, 0.14, 0.07, 2300 * b, 0.004); // partial 2 (inharmonic)
        this.tone(4600 * b, "sine", 0.001, 0.11, 0.05, 3600 * b, 0.006);     // partial 3 (shimmer)
        this.tone(170 * b, "triangle", 0.002, 0.05, 0.11, 85, 0.001);        // low thunk (weight)
        break;
      }
      case "bow":
        // String twang: quick down-sweep plus a tiny pluck of noise.
        this.tone(900 * v, "sawtooth", 0.004, 0.09, 0.12, 300);
        this.noise(0.04, 0.08, 2400, "bandpass");
        break;
      case "arrowHit": this.noise(0.08, 0.2, 1800 * v); this.noise(0.05, 0.1, 600, "lowpass", 0.01); break;
      case "siege":
        // Deep cannon boom + thrown debris (a second crack tail for body).
        this.tone(92 * v, "square", 0.005, 0.32, 0.36, 40);
        this.noise(0.28, 0.22, 520, "lowpass");
        this.noise(0.22, 0.12, 1600, "bandpass", 0.05);
        break;
      case "collapse":
        this.tone(70, "square", 0.01, 0.5, 0.22, 32);
        this.noise(0.5, 0.34, 300, "lowpass");
        this.noise(0.4, 0.2, 900, "bandpass", 0.12);
        break;
      case "death":
        // A short downward grunt-thud — a fallen soldier.
        this.tone(220 * v, "triangle", 0.005, 0.16, 0.18, 90);
        this.noise(0.12, 0.12, 500, "lowpass", 0.01);
        break;
      case "alert":
        // Two-note war horn.
        this.tone(300, "square", 0.02, 0.22, 0.22, 300);
        this.tone(400, "square", 0.02, 0.28, 0.2, 400, 0.18);
        break;
      case "ui": this.tone(700 * v, "sine", 0.004, 0.05, 0.18); break;
      case "coin": this.tone(1180 * v, "triangle", 0.004, 0.09, 0.2, 1568); break;
      case "tick": this.tone(1000, "square", 0.002, 0.025, 0.08); break;
      case "reveal": this.tone(440, "sine", 0.01, 0.4, 0.3, 1320); break;
      case "levelup":
        this.tone(523, "sine", 0.01, 0.25, 0.3, 1047);
        this.tone(659, "sine", 0.02, 0.3, 0.2, 1318, 0.08);
        this.tone(784, "sine", 0.02, 0.32, 0.16, 1568, 0.16);
        break;
    }
  }

  // --- Generative music: a calm bed that swells into combat ----------------
  private scale = [0, 3, 5, 7, 10]; // minor pentatonic
  private root = 196; // G3
  // A slow minor progression (i – VI – III – VII) gives the bed direction
  // instead of meandering; the melody and bass follow the current chord root.
  private chords = [0, 8, 3, 10];
  private chordIdx = 0;
  private barTimer = 0;
  private lastStep = 0; // last melody scale index, for stepwise motion
  private drumTimer = 0;

  /**
   * `intensity` (0..1) is the battle heat: it speeds the melody, brightens the
   * notes, brings in a bass pulse and a war-drum beat so a big fight sounds
   * like one.
   */
  updateMusic(dt: number, playing: boolean, intensity = 0) {
    if (!this.ctx || this.muted || this.musicVol <= 0 || !playing) return;
    const heat = Math.max(0, Math.min(1, intensity));

    // Bars — advance the chord and lay a sustained pad + bass under it.
    this.barTimer -= dt;
    if (this.barTimer <= 0) {
      this.barTimer = 4.4 * (1 - heat * 0.3);
      this.chordIdx = (this.chordIdx + 1) % this.chords.length;
      this.layChord(this.chords[this.chordIdx], heat, this.barTimer);
    }

    // Melody — picks chord-friendly notes, drifts stepwise, rests now and then.
    this.musicTimer -= dt;
    if (this.musicTimer <= 0) {
      this.musicTimer = (0.95 + Math.random() * 0.75) * (1 - heat * 0.5);
      if (Math.random() > 0.16) this.playMelodyNote(heat);
    }

    // War drums — only roll in during a real fight, faster as it intensifies.
    if (heat > 0.18) {
      this.drumTimer -= dt;
      if (this.drumTimer <= 0) {
        this.drumTimer = 0.62 - heat * 0.26; // ~0.6s down to ~0.36s per beat
        this.drumHit(0.10 + heat * 0.14);
      }
    } else {
      this.drumTimer = 0;
    }
  }

  /** Sustained chord pad (root + fifth + octave) with a low bass pulse. */
  private layChord(semis: number, heat: number, dur: number) {
    if (!this.ctx) return;
    const t0 = this.ctx.currentTime;
    const base = this.root * Math.pow(2, semis / 12);
    const voices: [number, OscillatorType, number][] = [
      [base, "triangle", 0.07 + heat * 0.03],
      [base * 1.5, "sine", 0.05], // fifth
      [base * 2, "sine", 0.03], // octave
    ];
    for (const [freq, type, peak] of voices) {
      const osc = this.ctx.createOscillator();
      const g = this.ctx.createGain();
      osc.type = type;
      osc.frequency.value = freq;
      osc.connect(g);
      g.connect(this.musicGain);
      g.gain.setValueAtTime(0.0001, t0);
      g.gain.linearRampToValueAtTime(peak, t0 + dur * 0.35);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur + 0.6);
      osc.start(t0);
      osc.stop(t0 + dur + 0.7);
    }
    // Bass: the chord root an octave down, a slow swelling pulse.
    const bass = this.ctx.createOscillator();
    const bg = this.ctx.createGain();
    bass.type = "sine";
    bass.frequency.value = base * 0.5;
    bass.connect(bg);
    bg.connect(this.musicGain);
    bg.gain.setValueAtTime(0.0001, t0);
    bg.gain.linearRampToValueAtTime(0.12 + heat * 0.06, t0 + 0.3);
    bg.gain.exponentialRampToValueAtTime(0.0001, t0 + dur * 0.9);
    bass.start(t0);
    bass.stop(t0 + dur + 0.1);
  }

  /** A single melody note over the current chord, biased to stepwise motion. */
  private playMelodyNote(heat: number) {
    if (!this.ctx) return;
    // Drift one scale-step from the last note (occasionally leap), wrap in range.
    const move = Math.random() < 0.7 ? (Math.random() < 0.5 ? -1 : 1) : (Math.random() < 0.5 ? -2 : 2);
    this.lastStep = Math.max(0, Math.min(this.scale.length - 1, this.lastStep + move));
    const semis = this.chords[this.chordIdx] + this.scale[this.lastStep];
    const oct = Math.random() < 0.35 + heat * 0.2 ? 2 : 1;
    const freq = this.root * Math.pow(2, semis / 12) * oct;
    const t0 = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    osc.type = "sine";
    osc.frequency.value = freq;
    osc.connect(g);
    g.connect(this.musicGain);
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.linearRampToValueAtTime(0.16 + heat * 0.08, t0 + 0.35);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + 2.0);
    osc.start(t0);
    osc.stop(t0 + 2.1);
  }

  /** A low war-drum thump (sine kick + filtered noise) on the music bus. */
  private drumHit(peak: number) {
    if (!this.ctx) return;
    const t0 = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    osc.type = "sine";
    osc.frequency.setValueAtTime(120, t0);
    osc.frequency.exponentialRampToValueAtTime(48, t0 + 0.18);
    osc.connect(g);
    g.connect(this.musicGain);
    g.gain.setValueAtTime(peak, t0);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.26);
    osc.start(t0);
    osc.stop(t0 + 0.28);
    // a little skin/snap
    const len = Math.floor(this.ctx.sampleRate * 0.12);
    const buf = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
    const src = this.ctx.createBufferSource();
    src.buffer = buf;
    const filt = this.ctx.createBiquadFilter();
    filt.type = "lowpass";
    filt.frequency.value = 220;
    const ng = this.ctx.createGain();
    ng.gain.setValueAtTime(peak * 0.5, t0);
    ng.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.14);
    src.connect(filt);
    filt.connect(ng);
    ng.connect(this.musicGain);
    src.start(t0);
    src.stop(t0 + 0.12);
  }
}

export const audio = new Audio();
