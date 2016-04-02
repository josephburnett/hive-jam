# TIME

bpm = 450

live_loop :beat_loop do
  cue :beat
  sleep 60.0/bpm
end