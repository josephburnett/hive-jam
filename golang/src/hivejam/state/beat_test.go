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

func check(t *testing.T, given Given, expect Expect) {
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
	b1 := []Beat{Beat{1}}
	b3 := []Beat{Beat{1}, Beat{0}, Beat{1}}
	check(t, Given{Bpc: "1", Res: "1", Beats: b3, Tick: 0}, Expect{On: true, Bound: true, Index: 0})
	check(t, Given{Bpc: "1", Res: "1", Beats: b3, Tick: 1}, Expect{On: false, Bound: true, Index: 1})
	check(t, Given{Bpc: "1", Res: "1", Beats: b3, Tick: 2}, Expect{On: true, Bound: true, Index: 2})
	check(t, Given{Bpc: "1", Res: "1", Beats: b3, Tick: 3}, Expect{On: true, Bound: true, Index: 0})
	check(t, Given{Bpc: "1", Res: "1", Beats: b3, Tick: 4}, Expect{On: false, Bound: true, Index: 1})
	check(t, Given{Bpc: "1", Res: "1", Beats: b3, Tick: 5}, Expect{On: true, Bound: true, Index: 2})
	check(t, Given{Bpc: "1", Res: "1/2", Beats: b3, Tick: 0}, Expect{On: true, Bound: true, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/2", Beats: b3, Tick: 1}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/2", Beats: b3, Tick: 2}, Expect{On: false, Bound: true, Index: 1})
	check(t, Given{Bpc: "1", Res: "1/2", Beats: b3, Tick: 3}, Expect{On: false, Bound: false, Index: 1})
	check(t, Given{Bpc: "1", Res: "1/2", Beats: b3, Tick: 4}, Expect{On: true, Bound: true, Index: 2})
	check(t, Given{Bpc: "1", Res: "1/2", Beats: b3, Tick: 5}, Expect{On: true, Bound: false, Index: 2})
	check(t, Given{Bpc: "3", Res: "1", Beats: b3, Tick: 0}, Expect{On: true, Bound: true, Index: 0})
	check(t, Given{Bpc: "3", Res: "1", Beats: b3, Tick: 1}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "3", Res: "1", Beats: b3, Tick: 2}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "3", Res: "1", Beats: b3, Tick: 3}, Expect{On: false, Bound: true, Index: 1})
	check(t, Given{Bpc: "3", Res: "1", Beats: b3, Tick: 4}, Expect{On: false, Bound: false, Index: 1})
	check(t, Given{Bpc: "3", Res: "1", Beats: b3, Tick: 5}, Expect{On: false, Bound: false, Index: 1})
	check(t, Given{Bpc: "3", Res: "1", Beats: b3, Tick: 6}, Expect{On: true, Bound: true, Index: 2})
	check(t, Given{Bpc: "3", Res: "1", Beats: b3, Tick: 7}, Expect{On: true, Bound: false, Index: 2})
	check(t, Given{Bpc: "3", Res: "1", Beats: b3, Tick: 8}, Expect{On: true, Bound: false, Index: 2})
	check(t, Given{Bpc: "1", Res: "1/8", Beats: b1, Tick: 0}, Expect{On: true, Bound: true, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/8", Beats: b1, Tick: 1}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/8", Beats: b1, Tick: 2}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/8", Beats: b1, Tick: 3}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/8", Beats: b1, Tick: 4}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/8", Beats: b1, Tick: 5}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/8", Beats: b1, Tick: 6}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/8", Beats: b1, Tick: 7}, Expect{On: true, Bound: false, Index: 0})
	check(t, Given{Bpc: "1", Res: "1/8", Beats: b1, Tick: 8}, Expect{On: true, Bound: true, Index: 0})
}
