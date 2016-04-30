# SANDBOX

drop_all_state

set_state :root,
{
  id: "guid",
  bpc: "1/4",
  tracks:
  [
    { beats: [1, 0, 1], type: "sample", sample: :drum_bass_hard, params: {pitch: 12, amp: 1.2 }},
    { beats: [0, 1, 1, 1], type: "sample", sample: :drum_cymbal_closed, params: {rpitch: 12 }},
    { beats: [0, 0, 1, 0, 0, 0, 0, 1], type: "sample", sample: :drum_snare_soft, params: {amp: 1.4, rate: 3 }},
    { beats: [0, 0, 0, 0, 0, 0, 0, 1], type: "play", note: 27, params: {amp: 10.0, release: 0.1, attack: 0.01, sustain: 0.8 }},
  ]
}

##| X = [0].cycle(7).to_a + [1]
##| O = [0].cycle(8).to_a

##| set_state :errie,
##| [
##|   { beats: X + X + X + O, sample: :ambi_piano, rpitch: 0, amp: 2.0, fx: :distortion },
##|   { beats: O + O + O + X, sample: :ambi_piano, rpitch: -2, amp: 2.0, fx: :distortion },
##| ]