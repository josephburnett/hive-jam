package state

import (
	"reflect"
	"testing"
)

func TestNomNom(t *testing.T) {
	original := Grid{
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
	nomValue, err := NomNom(original)
	if err != nil {
		t.Error(err, original, nomValue)
		return
	}
	copy := Grid{}
	err = DeNom(nomValue, &copy)
	if err != nil {
		t.Error(err, original, nomValue, copy)
		return
	}
	ok := reflect.DeepEqual(original, copy)
	if !ok {
		t.Errorf("Original does not equal copy\n%v\n%v\n", original, copy)
	}
}
