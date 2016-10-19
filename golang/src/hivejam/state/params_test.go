package state

import (
	"reflect"
	"testing"
)

func TestSimpleParams(t *testing.T) {
	chain := &paramsChain{
		Params: &Params{
			"a": "1",
			"b": "2",
		},
	}
	params := chain.Materialize()
	if !reflect.DeepEqual(params, chain.Params) {
		t.Errorf("Expected %v but got %v.", chain.Params, params)
	}
}

func TestInheritScalar(t *testing.T) {
	chain := &paramsChain{
		Params: &Params{
			"a": "1",
			"b": "1",
		},
		Parent: &paramsChain{
			Params: &Params{
				"a": "2",
			},
		},
	}
	expect := &Params{
		"a": "2",
		"b": "1",
	}
	params := chain.Materialize()
	if !reflect.DeepEqual(params, expect) {
		t.Errorf("Expected %v but got %v.", expect, params)
	}
}
