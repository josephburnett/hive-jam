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

func (g *Grid) Dispatch(tick int, resolution string, parent *Track) []Dispatch {
	return nil
}

func OnTheBeat(bpc, resolution *big.Rat, beats []Beat, tick int64) (boundary, on bool, index int64) {
	tpc := quo(bpc, resolution)
	boundary = mod(big.NewInt(tick), ceil(tpc)).Cmp(big.NewInt(0)) == 0
	i := floor(quo(big.NewRat(tick, 1), tpc))
	index = mod(i, big.NewInt(int64(len(beats)))).Int64()
	on = beats[index][0] > 0
	return boundary, on, index
}

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
	b = ip(sub(a, big.NewRat(1, 2)))
	return
}
func ceil(a *big.Rat) (b *big.Int) {
	b = ip(add(a, big.NewRat(1, 2)))
	return
}

func mod(a, b *big.Int) (c *big.Int) {
	c = big.NewInt(0)
	c.Mod(a, b)
	return
}
