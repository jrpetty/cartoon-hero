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

  private tone(
    freq: number,
    type: OscillatorType,
    attack: number,
    decay: number,
    peak: number,
    freqEnd?: number,
  ) {
    if (!this.ctx) return;
    const t0 = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t0);
    if (freqEnd !== undefined) osc.frequency.exponentialRampToValueAtTime(Math.max(20, freqEnd), t0 + attack + decay);
    osc.connect(g);
    g.connect(this.sfxGain);
    this.env(osc, g, t0, attack, decay, peak);
  }

  private noise(duration: number, peak: number, filterFreq: number, type: BiquadFilterType = "bandpass") {
    if (!this.ctx) return;
    const t0 = this.ctx.currentTime;
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
    switch (name) {
      case "select": this.tone(620, "triangle", 0.005, 0.07, 0.25); break;
      case "command": this.tone(420, "triangle", 0.005, 0.08, 0.22, 520); break;
      case "build": this.noise(0.18, 0.18, 900, "lowpass"); break;
      case "complete": this.tone(523, "sine", 0.01, 0.18, 0.3, 784); break;
      case "sword": this.noise(0.12, 0.25, 2600, "bandpass"); break;
      case "bow": this.tone(880, "sawtooth", 0.005, 0.08, 0.12, 320); break;
      case "arrowHit": this.noise(0.08, 0.2, 1800); break;
      case "siege": this.tone(90, "square", 0.005, 0.3, 0.35, 40); this.noise(0.25, 0.2, 500, "lowpass"); break;
      case "collapse": this.noise(0.5, 0.35, 300, "lowpass"); break;
      case "alert": this.tone(300, "square", 0.01, 0.18, 0.2, 360); break;
      case "ui": this.tone(700, "sine", 0.004, 0.05, 0.18); break;
      case "coin": this.tone(1180, "triangle", 0.004, 0.09, 0.2, 1568); break;
      case "tick": this.tone(1000, "square", 0.002, 0.025, 0.08); break;
      case "reveal": this.tone(440, "sine", 0.01, 0.4, 0.3, 1320); break;
      case "levelup": this.tone(523, "sine", 0.01, 0.25, 0.3, 1047); this.tone(659, "sine", 0.02, 0.3, 0.2, 1318); break;
    }
  }

  // --- Generative music: a calm bed that swells into combat ----------------
  private scale = [0, 3, 5, 7, 10]; // minor pentatonic
  private root = 196; // G3
  private drumTimer = 0;

  /**
   * `intensity` (0..1) is the battle heat: it speeds the melody, brightens the
   * notes, and brings in a war-drum pulse so a big fight sounds like one.
   */
  updateMusic(dt: number, playing: boolean, intensity = 0) {
    if (!this.ctx || this.muted || this.musicVol <= 0 || !playing) return;
    const heat = Math.max(0, Math.min(1, intensity));

    // Melody — notes come faster and a touch louder as the battle heats up.
    this.musicTimer -= dt;
    if (this.musicTimer <= 0) {
      this.musicTimer = (1.1 + Math.random() * 0.9) * (1 - heat * 0.55);
      const t0 = this.ctx.currentTime;
      const step = this.scale[Math.floor(Math.random() * this.scale.length)];
      const oct = Math.random() < 0.4 + heat * 0.2 ? 2 : 1;
      const freq = this.root * Math.pow(2, step / 12) * oct;
      const osc = this.ctx.createOscillator();
      const g = this.ctx.createGain();
      osc.type = "sine";
      osc.frequency.value = freq;
      osc.connect(g);
      g.connect(this.musicGain);
      g.gain.setValueAtTime(0.0001, t0);
      g.gain.linearRampToValueAtTime(0.18 + heat * 0.08, t0 + 0.4);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + 2.2);
      osc.start(t0);
      osc.stop(t0 + 2.3);
      // soft fifth pad underneath
      const osc2 = this.ctx.createOscillator();
      const g2 = this.ctx.createGain();
      osc2.type = "triangle";
      osc2.frequency.value = freq * 0.5;
      osc2.connect(g2);
      g2.connect(this.musicGain);
      g2.gain.setValueAtTime(0.0001, t0);
      g2.gain.linearRampToValueAtTime(0.08, t0 + 0.8);
      g2.gain.exponentialRampToValueAtTime(0.0001, t0 + 3);
      osc2.start(t0);
      osc2.stop(t0 + 3.1);
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
