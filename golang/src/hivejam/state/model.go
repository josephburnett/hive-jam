package state

import (
	"math/big"
)

type Grid struct {
	Name   string  `json:"name"`
	Id     string  `json:"id"`
	Bpc    big.Rat `json:"bpc"`
	Tracks []Track `json:"tracks"`
}

type Track struct {
	Type         string `json:"type"`
	Id           string `json:"id"`
	On           bool   `json:"on"`
	Beats        []Beat `json:"beats"`
	Fx           []Fx   `json:"fx"`
	Sample       string `json:"sample"`
	Synth        string `json:"synth"`
	SampleParams Params `json:"sample-params"`
	SynthParams  Params `json:"synth-params"`
}

type Beat []int32

type Fx struct {
	Fx     string `json:"fx"`
	Params Params `json:"params"`
}

type Params map[string]string
