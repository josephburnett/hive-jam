# SANDBOX

drop_all_state

set_state :beat,
[
  { beats: [1, 0, 1], sample: :drum_bass_hard, pitch: 12, amp: 1.2 },
  { beats: [0, 1, 1, 1], sample: :drum_cymbal_closed, rpitch: 12 },
  { beats: [0, 0, 1, 0, 0, 0, 0, 1], sample: :drum_snare_soft, amp: 1.4, rate: 3 },
  { beats: [0, 0, 0, 0, 0, 0, 0, 1], play: 27, amp: 5.0, release: 0.1, attack: 0.01, sustain: 0.4 },
]

X = [0].cycle(7).to_a + [1]
O = [0].cycle(8).to_a

set_state :errie,
[
  { beats: X + X + X + O, sample: :ambi_piano, rpitch: 0, amp: 2.0 },
  { beats: O + O + O + X, sample: :ambi_piano, rpitch: -2, amp: 2.0 },
]