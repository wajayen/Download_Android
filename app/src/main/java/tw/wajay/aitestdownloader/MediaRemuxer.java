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

    static File muxVideoAudioToMp4(File videoInput, File audioInput, File output) throws IOException {
        if (videoInput == null || !videoInput.isFile() || videoInput.length() <= 0L) {
            throw new IOException("video input media is empty");
        }
        if (audioInput == null || !audioInput.isFile() || audioInput.length() <= 0L) {
            throw new IOException("audio input media is empty");
        }
        if (output.exists() && !output.delete()) {
            throw new IOException("could not replace existing muxed output");
        }

        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        boolean success = false;
        try {
            videoExtractor.setDataSource(videoInput.getAbsolutePath());
            audioExtractor.setDataSource(audioInput.getAbsolutePath());
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int[] videoTrackMap = addTracks(videoExtractor, muxer, "video/");
            int[] audioTrackMap = addTracks(audioExtractor, muxer, "audio/");
            if (!hasMappedTrack(videoTrackMap)) {
                throw new IOException("video input has no muxable video track");
            }
            if (!hasMappedTrack(audioTrackMap)) {
                throw new IOException("audio input has no muxable audio track");
            }

            muxer.start();
            muxerStarted = true;
            writeExtractorSamples(videoExtractor, muxer, videoTrackMap);
            writeExtractorSamples(audioExtractor, muxer, audioTrackMap);
            success = output.isFile() && output.length() > 0L;
            if (!success) {
                throw new IOException("muxed mp4 output is empty");
            }
            return output;
        } finally {
            videoExtractor.release();
            audioExtractor.release();
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

    private static int[] addTracks(MediaExtractor extractor, MediaMuxer muxer, String mimePrefix) {
        int trackCount = extractor.getTrackCount();
        int[] trackMap = new int[trackCount];
        for (int i = 0; i < trackCount; i++) {
            trackMap[i] = -1;
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.containsKey(MediaFormat.KEY_MIME)
                    ? format.getString(MediaFormat.KEY_MIME)
                    : "";
            if (mime == null || !mime.startsWith(mimePrefix)) {
                continue;
            }
            trackMap[i] = muxer.addTrack(format);
            extractor.selectTrack(i);
        }
        return trackMap;
    }

    private static boolean hasMappedTrack(int[] trackMap) {
        for (int track : trackMap) {
            if (track >= 0) {
                return true;
            }
        }
        return false;
    }

    private static void writeExtractorSamples(MediaExtractor extractor, MediaMuxer muxer, int[] trackMap) {
        ByteBuffer buffer = ByteBuffer.allocate(2 * 1024 * 1024);
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
    }

    private static boolean isMuxableTrack(String mime) {
        return mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"));
    }
}
