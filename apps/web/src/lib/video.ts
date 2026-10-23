export interface VideoMeta {
  width: number;
  height: number;
  durationMs: number;
  thumbnail?: Uint8Array;
}

export function videoMeta(file: File): Promise<VideoMeta> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const video = document.createElement("video");
    video.muted = true;
    video.preload = "metadata";
    video.playsInline = true;

    const finish = (meta: VideoMeta) => {
      URL.revokeObjectURL(url);
      resolve(meta);
    };

    video.onloadedmetadata = () => {
      video.currentTime = Math.min(0.5, (video.duration || 0) / 2);
    };
    video.onseeked = () => {
      const meta = {
        width: video.videoWidth,
        height: video.videoHeight,
        durationMs: Math.round((video.duration || 0) * 1000),
      };
      const canvas = document.createElement("canvas");
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      canvas.getContext("2d")?.drawImage(video, 0, 0);
      canvas.toBlob(
        async (blob) => finish({ ...meta, thumbnail: blob ? new Uint8Array(await blob.arrayBuffer()) : undefined }),
        "image/jpeg",
        0.8,
      );
    };
    video.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("video"));
    };
    video.src = url;
  });
}
