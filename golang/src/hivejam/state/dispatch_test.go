package state

import (
	"math/big"
	"testing"
)

type Given struct {
	Bpc   string
	Res   string
	Beats []Beat
	Tick  int64
}

type Expect struct {
	Bound bool
	On    bool
	Index int64
}

func check(t *testing.T, given *Given, expect *Expect) {
	bpc := big.NewRat(0, 1)
	bpc.UnmarshalText([]byte(given.Bpc))
	res := big.NewRat(0, 1)
	res.UnmarshalText([]byte(given.Res))
	bound, on, index := OnTheBeat(bpc, res, given.Beats, given.Tick)
	if bound != expect.Bound || on != expect.On || index != expect.Index {
		t.Errorf("Given %+v Expected %+v but got bound: %v, on %v, index %v",
			given, expect, bound, on, index)
	}
}

func TestOnTheBeat(t *testing.T) {
	b := []Beat{Beat{1}, Beat{0}, Beat{1}}
	check(t, &Given{Bpc: "1", Res: "1", Beats: b, Tick: 0}, &Expect{On: true, Bound: true, Index: 0})
}
