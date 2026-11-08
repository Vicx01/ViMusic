playback-regression.m4a is a synthetic 50-second 440 Hz sine tone, encoded as
fragmented AAC/MP4 at 128 kbit/s. It contains no third-party recording.
Generated with FFmpeg 9.0.2:
ffmpeg -f lavfi -i 'sine=frequency=440:sample_rate=44100:duration=50' -c:a aac -b:a 128k -movflags +frag_keyframe+empty_moov+default_base_moof -f mp4 playback-regression.m4a
