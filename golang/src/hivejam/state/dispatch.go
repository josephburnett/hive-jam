package state

import (
	"math/big"
)

type Dispatch struct {
	Synth        string `json:"synth"`
	SynthParams  Params `json:"synth-params"`
	Sample       string `json:"sample"`
	SampleParams Params `json:"sample-params"`
	Fx           []Fx   `json:"fx"`
}

type Cursor struct {
	BeatIndex  int64    `json:"beat-index"`
	SubCursors []Cursor `json:"sub-cursors"`
}

func (g *Grid) Dispatch(state map[string]Grid, tick int64, resolution *big.Rat, parent *Track) ([]Dispatch, []Cursor) {
	dispatches := make([]Dispatch, 0)
	cursors := make([]Cursor, 0)
	for _, track := range g.Tracks {
		on := track.On
		boundary, within, index := OnTheBeat(&g.Bpc, resolution, track.Beats, tick)
		// Inherit track type from parent track
		trackType := track.Type
		if trackType == "none" && parent.GridType != "" {
			trackType = parent.GridType
		}

		// TODO: implement parameter chain

		switch trackType {
		case "grid":
			if !on || !within || track.GridId == "" {
				cursors = append(cursors, Cursor{BeatIndex: index})
				continue
			}
			subGrid := state[track.GridId]
			subGridDispatches, subGridCursors := subGrid.Dispatch(state, tick, resolution, &track)
			dispatches = append(dispatches, subGridDispatches...)
			cursors = append(cursors, subGridCursors...)
		case "synth":
			if boundary {
				// do nothing yet
			}
		case "sample":
			if boundary {
				// do nothing yet
			}
		}
	}
	return dispatches, cursors
}
