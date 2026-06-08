package tw.wajay.aitestdownloader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

final class MediaRemuxer {
    private MediaRemuxer() {
    }

    static File remuxToMp4(File input, File output) throws IOException {
        if (input == null || !input.isFile() || input.length() <= 0L) {
            throw new IOException("input media is empty");
        }
        if (output.exists() && !output.delete()) {
            throw new IOException("could not replace existing mp4 output");
        }

        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        boolean success = false;
        try {
            extractor.setDataSource(input.getAbsolutePath());
            int trackCount = extractor.getTrackCount();
            int[] trackMap = new int[trackCount];
            int maxInputSize = 1024 * 1024;
            int selectedTracks = 0;
            for (int i = 0; i < trackCount; i++) {
                trackMap[i] = -1;
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.containsKey(MediaFormat.KEY_MIME)
                        ? format.getString(MediaFormat.KEY_MIME)
                        : "";
                if (!isMuxableTrack(mime)) {
                    continue;
                }
                if (muxer == null) {
                    muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                }
                trackMap[i] = muxer.addTrack(format);
                extractor.selectTrack(i);
                selectedTracks++;
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxInputSize = Math.max(maxInputSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                }
            }
            if (selectedTracks == 0 || muxer == null) {
                throw new IOException("no muxable audio or video tracks");
            }

            muxer.start();
            muxerStarted = true;
            ByteBuffer buffer = ByteBuffer.allocate(maxInputSize);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int trackIndex = extractor.getSampleTrackIndex();
                if (trackIndex < 0) {
                    break;
                }
                int muxerTrack = trackIndex < trackMap.length ? trackMap[trackIndex] : -1;
                if (muxerTrack < 0) {
                    extractor.advance();
                    continue;
                }
                buffer.clear();
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    break;
                }
                info.set(0, sampleSize, Math.max(0L, extractor.getSampleTime()), extractor.getSampleFlags());
                muxer.writeSampleData(muxerTrack, buffer, info);
                extractor.advance();
            }
            success = output.isFile() && output.length() > 0L;
            if (!success) {
                throw new IOException("mp4 output is empty");
            }
            return output;
        } finally {
            extractor.release();
            if (muxer != null) {
                try {
                    if (muxerStarted) {
                        muxer.stop();
                    }
                } catch (RuntimeException ignored) {
                    success = false;
                }
                muxer.release();
            }
            if (!success && output.exists()) {
                output.delete();
            }
        }
    }

    private static boolean isMuxableTrack(String mime) {
        return mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"));
    }
}
