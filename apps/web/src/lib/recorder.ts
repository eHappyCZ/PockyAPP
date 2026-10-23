export interface Recording {
  bytes: Uint8Array;
  mime: string;
  durationMs: number;
  levels: number[];
}

const types = ["audio/ogg;codecs=opus", "audio/webm;codecs=opus", "audio/mp4"];

export const canRecord = typeof MediaRecorder !== "undefined" && !!navigator.mediaDevices?.getUserMedia;

export class VoiceRecorder {
  private stream: MediaStream | null = null;
  private recorder: MediaRecorder | null = null;
  private context: AudioContext | null = null;
  private timer: ReturnType<typeof setInterval> | undefined;
  private chunks: Blob[] = [];
  private started = 0;

  levels: number[] = [];

  async start(onLevel: (level: number, elapsedMs: number) => void): Promise<void> {
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const type = types.find((value) => MediaRecorder.isTypeSupported(value));
    this.recorder = new MediaRecorder(this.stream, type ? { mimeType: type } : undefined);
    this.recorder.ondataavailable = (event) => event.data.size > 0 && this.chunks.push(event.data);

    this.context = new AudioContext();
    const analyser = this.context.createAnalyser();
    analyser.fftSize = 512;
    this.context.createMediaStreamSource(this.stream).connect(analyser);
    const samples = new Float32Array(analyser.fftSize);

    this.started = performance.now();
    this.recorder.start(250);
    this.timer = setInterval(() => {
      analyser.getFloatTimeDomainData(samples);
      let sum = 0;
      for (const sample of samples) sum += sample * sample;
      const level = Math.min(1, Math.sqrt(sum / samples.length) * 3);
      this.levels.push(level);
      onLevel(level, performance.now() - this.started);
    }, 60);
  }

  async stop(): Promise<Recording | null> {
    const recorder = this.recorder;
    if (!recorder) return null;
    const durationMs = performance.now() - this.started;
    const done = new Promise<void>((resolve) => (recorder.onstop = () => resolve()));
    if (recorder.state !== "inactive") recorder.stop();
    await done;
    const mime = (recorder.mimeType || "audio/webm").split(";")[0];
    const blob = new Blob(this.chunks, { type: mime });
    this.release();
    if (blob.size === 0) return null;
    return { bytes: new Uint8Array(await blob.arrayBuffer()), mime, durationMs: Math.round(durationMs), levels: this.levels };
  }

  cancel(): void {
    if (this.recorder && this.recorder.state !== "inactive") this.recorder.stop();
    this.release();
  }

  private release(): void {
    clearInterval(this.timer);
    this.stream?.getTracks().forEach((track) => track.stop());
    this.context?.close().catch(() => undefined);
    this.stream = null;
    this.recorder = null;
    this.context = null;
    this.chunks = [];
  }
}
