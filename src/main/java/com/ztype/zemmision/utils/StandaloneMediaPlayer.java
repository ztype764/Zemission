package com.ztype.zemmision.utils;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandaloneMediaPlayer {
    private static final Logger logger = LoggerFactory.getLogger(StandaloneMediaPlayer.class);

    private Thread playbackThread;
    private SourceDataLine line;
    private AudioInputStream baseStream;
    private AudioInputStream decodedStream;
    
    private volatile boolean isPlaying = false;
    private volatile boolean isPaused = false;
    private float volume = 0.7f;
    private boolean isMuted = false;
    
    private final Object pauseLock = new Object();
    private File currentFile;
    private Runnable onEndOfMedia;

    private double durationSeconds = 0;
    private long currentDecodedBytesRead = 0;

    private final javafx.beans.property.ObjectProperty<javafx.util.Duration> currentTime = 
            new javafx.beans.property.SimpleObjectProperty<>(javafx.util.Duration.ZERO);
    private final javafx.beans.property.ObjectProperty<javafx.util.Duration> totalDuration = 
            new javafx.beans.property.SimpleObjectProperty<>(javafx.util.Duration.ZERO);

    public StandaloneMediaPlayer(File file) {
        this.currentFile = file;
        initializeDuration();
    }

    private void initializeDuration() {
        if (currentFile == null || !currentFile.exists()) return;
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(currentFile);
            Map<String, Object> properties = fileFormat.properties();
            if (properties != null && properties.containsKey("duration")) {
                Long durationMicroseconds = (Long) properties.get("duration");
                durationSeconds = durationMicroseconds / 1_000_000.0;
            } else {
                AudioFormat format = fileFormat.getFormat();
                long frames = fileFormat.getFrameLength();
                if (frames > 0 && format.getFrameRate() > 0) {
                    durationSeconds = (double) frames / format.getFrameRate();
                }
            }
            totalDuration.set(javafx.util.Duration.seconds(durationSeconds));
        } catch (Exception e) {
            logger.error("Failed to estimate duration for: " + currentFile.getName(), e);
            // Default fallback
            durationSeconds = 180.0; 
            totalDuration.set(javafx.util.Duration.seconds(durationSeconds));
        }
    }

    public javafx.beans.property.ObjectProperty<javafx.util.Duration> currentTimeProperty() {
        return currentTime;
    }

    public javafx.beans.property.ObjectProperty<javafx.util.Duration> totalDurationProperty() {
        return totalDuration;
    }

    public javafx.util.Duration getCurrentTime() {
        return currentTime.get();
    }

    public void setOnEndOfMedia(Runnable onEndOfMedia) {
        this.onEndOfMedia = onEndOfMedia;
    }

    public void setVolume(double vol) {
        this.volume = (float) Math.max(0.0, Math.min(1.0, vol));
        if (line != null && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float dB;
            float targetVol = isMuted ? 0.0f : this.volume;
            if (targetVol < 0.01f) {
                dB = gainControl.getMinimum();
            } else {
                dB = (float) (Math.log10(targetVol) * 20.0);
                if (dB < gainControl.getMinimum()) dB = gainControl.getMinimum();
                if (dB > gainControl.getMaximum()) dB = gainControl.getMaximum();
            }
            gainControl.setValue(dB);
        }
    }

    public void setMute(boolean mute) {
        this.isMuted = mute;
        setVolume(this.volume);
    }

    public synchronized void play() {
        synchronized (pauseLock) {
            if (isPaused) {
                isPaused = false;
                pauseLock.notifyAll();
                return;
            }
        }

        if (isPlaying) return;

        if (currentFile == null || !currentFile.exists()) {
            logger.error("Cannot play; file not found: " + (currentFile == null ? "null" : currentFile.getPath()));
            return;
        }

        isPlaying = true;
        isPaused = false;

        try {
            baseStream = AudioSystem.getAudioInputStream(currentFile);
            AudioFormat baseFormat = baseStream.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            decodedStream = AudioSystem.getAudioInputStream(decodedFormat, baseStream);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(decodedFormat);
            line.start();
            setVolume(volume);

            startPlaybackThread(decodedFormat);
        } catch (Exception e) {
            logger.error("Failed to start audio playback for: " + currentFile.getName(), e);
            isPlaying = false;
        }
    }

    private void startPlaybackThread(AudioFormat format) {
        playbackThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            int nBytesRead;
            try {
                while (isPlaying) {
                    synchronized (pauseLock) {
                        while (isPaused && isPlaying) {
                            line.stop();
                            pauseLock.wait();
                            if (isPlaying && !isPaused) {
                                line.start();
                            }
                        }
                    }

                    if (!isPlaying) break;

                    nBytesRead = decodedStream.read(buffer, 0, buffer.length);
                    if (nBytesRead == -1) {
                        break;
                    }

                    line.write(buffer, 0, nBytesRead);
                    currentDecodedBytesRead += nBytesRead;

                    double currentSec = (double) currentDecodedBytesRead / (format.getSampleRate() * format.getFrameSize());
                    javafx.application.Platform.runLater(() -> currentTime.set(javafx.util.Duration.seconds(currentSec)));
                }

                if (isPlaying) {
                    line.drain();
                    isPlaying = false;
                    if (onEndOfMedia != null) {
                        javafx.application.Platform.runLater(onEndOfMedia);
                    }
                }
            } catch (Exception e) {
                logger.error("Error during playback", e);
            } finally {
                cleanup();
            }
        }, "standalone-player-thread");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    public void pause() {
        synchronized (pauseLock) {
            isPaused = true;
        }
    }

    public synchronized void stop() {
        stopThread();
        if (line != null) {
            line.stop();
            line.flush();
        }
        currentTime.set(javafx.util.Duration.ZERO);
    }

    public synchronized void dispose() {
        stop();
        if (line != null) {
            line.close();
            line = null;
        }
    }

    private void stopThread() {
        isPlaying = false;
        synchronized (pauseLock) {
            isPaused = false;
            pauseLock.notifyAll();
        }
        if (playbackThread != null) {
            try {
                playbackThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            playbackThread = null;
        }
        cleanup();
    }

    private void cleanup() {
        try {
            if (decodedStream != null) decodedStream.close();
            if (baseStream != null) baseStream.close();
        } catch (IOException ignored) {}
        currentDecodedBytesRead = 0;
    }

    public synchronized void seek(javafx.util.Duration duration) {
        if (currentFile == null) return;
        double seconds = duration.toSeconds();
        boolean wasPlaying = isPlaying && !isPaused;

        stopThread();

        try {
            baseStream = AudioSystem.getAudioInputStream(currentFile);
            AudioFormat baseFormat = baseStream.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            decodedStream = AudioSystem.getAudioInputStream(decodedFormat, baseStream);

            long byteOffset = (long) (seconds * decodedFormat.getSampleRate() * decodedFormat.getFrameSize());
            byteOffset = (byteOffset / decodedFormat.getFrameSize()) * decodedFormat.getFrameSize();

            long skipped = 0;
            while (skipped < byteOffset) {
                long skipVal = decodedStream.skip(byteOffset - skipped);
                if (skipVal <= 0) break;
                skipped += skipVal;
            }

            currentDecodedBytesRead = skipped;
            double currentSec = (double) currentDecodedBytesRead / (decodedFormat.getSampleRate() * decodedFormat.getFrameSize());
            currentTime.set(javafx.util.Duration.seconds(currentSec));

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
            if (line == null) {
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(decodedFormat);
            }

            if (wasPlaying) {
                isPlaying = true;
                isPaused = false;
                line.start();
                setVolume(volume);
                startPlaybackThread(decodedFormat);
            } else {
                isPaused = true;
            }
        } catch (Exception e) {
            logger.error("Error seeking to " + seconds + "s", e);
        }
    }

    public boolean isPlaying() {
        return isPlaying && !isPaused;
    }
}
