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
	BeatIndex  int      `json:"beat-index"`
	SubCursors []Cursor `json:"sub-cursors"`
}

func (g *Grid) Dispatch(state map[string]Grid, tick int, resolution string, parent *Track) ([]Dispatch, []Cursor) {
	dispatches := make([]Dispatch)
	cursors := make([]Cursor)
	for _, track := range g.Tracks {
		on := track.On
		boundary, within, index := OnTheBeat(g.Bpc, g.Resolution, track.Beats, tick)
		// Inherit track type from parent track
		trackType := track.Type
		if trackType == "none" && parent.GridType != "" {
			trackType = parent.GridType
		}

		// TODO: implement parameter chain

		switch trackType {
		case "grid":
			if !on || !within || track.GridId == "" {
				append(cursors, Cursor{BeatIndex: index})
				continue
			}
			subGrid := state[track.GridId]
			subGridDispatches, subGridCursors := subGrid.Dispatch(state, tick, resolution, track)
			append(dispatches, subGridDispatches...)
			append(cursors, subGridCursors...)
		case "synth":
			// do nothing yet
		case "sample":
			// do nothing yet
		}
	}

}

func OnTheBeat(bpc, resolution *big.Rat, beats []Beat, tick int64) (boundary, on bool, index int64) {
	tpc := quo(bpc, resolution)
	boundary = mod(big.NewInt(tick), ceil(tpc)).Cmp(big.NewInt(0)) == 0
	i := floor(quo(big.NewRat(tick, 1), tpc))
	index = i.Int64() % int64(len(beats))
	on = beats[index][0] > 0
	return boundary, on, index
}

var zero *big.Rat = big.NewRat(0, 1)

func quo(a, b *big.Rat) (c *big.Rat) {
	c = big.NewRat(0, 1)
	c.Quo(a, b)
	return
}

func sub(a, b *big.Rat) (c *big.Rat) {
	c = big.NewRat(0, 1)
	c.Sub(a, b)
	return
}

func add(a, b *big.Rat) (c *big.Rat) {
	c = big.NewRat(0, 1)
	c.Add(a, b)
	return
}

func ip(a *big.Rat) (b *big.Int) {
	c := big.NewRat(0, 1)
	c.SetString(a.FloatString(0))
	if !c.IsInt() {
		panic("c must be an int")
	}
	b = c.Num()
	return
}

func floor(a *big.Rat) (b *big.Int) {
	if a.Cmp(zero) == 0 {
		return big.NewInt(0)
	}
	b = ip(sub(a, big.NewRat(1, 2)))
	return
}
func ceil(a *big.Rat) (b *big.Int) {
	if a.IsInt() {
		b = big.NewInt(0)
		b.Quo(a.Num(), a.Denom())
		return
	}
	b = ip(add(a, big.NewRat(1, 2)))
	return
}

func mod(a, b *big.Int) (c *big.Int) {
	c = big.NewInt(0)
	c.Mod(a, b)
	return
}
