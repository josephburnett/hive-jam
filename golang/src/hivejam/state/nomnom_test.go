package state

import (
	"testing"
)

func TestNomNom(t *testing.T) {
	g := Grid{
		Name: "root",
		Id:   "root",
		Bpc:  "1/4",
		Tracks: []Track{
			Track{
				Type: "synth",
				Id:   "xyz",
				On:   true,
				Beats: []Beat{
					Beat{1},
					Beat{0},
					Beat{0},
					Beat{1},
				},
				Fx: []Fx{
					Fx{
						Fx: "distortion",
						Params: map[string]string{
							"amp":     "2.0",
							"distort": ".95",
						},
					},
				},
				Synth: "fm",
				SynthParams: map[string]string{
					"amp":   "1.5",
					"pitch": "12",
				},
			},
		},
	}
	n, err := NomNom(g)
	if err != nil {
		t.Error(err, g, n)
	}
}
