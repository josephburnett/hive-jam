package state

import (
	"reflect"
	"testing"
)

var emptyContext = &context{}

func TestSimpleParams(t *testing.T) {
	chain := &paramsChain{
		Params: &Params{
			"a": "1",
			"b": "2",
		},
	}
	params := chain.Materialize(emptyContext)
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
	params := chain.Materialize(emptyContext)
	if !reflect.DeepEqual(params, expect) {
		t.Errorf("Expected %v but got %v.", expect, params)
	}
}

func TestInheritanceBreak(t *testing.T) {
	chain := &paramsChain{
		Params: &Params{
			"a": "!foo",
		},
		Parent: &paramsChain{
			Params: &Params{
				"a": "bar",
			},
		},
	}
	expect := &Params{
		"a": "foo",
	}
	params := chain.Materialize(emptyContext)
	if !reflect.DeepEqual(params, expect) {
		t.Errorf("Expected %v but got %v.", expect, params)
	}
}

func TestChildAgnostic(t *testing.T) {
	chain := &paramsChain{
		Params: &Params{
			"a": "1",
		},
		Parent: &paramsChain{
			Params: &Params{},
		},
	}
	expect := &Params{
		"a": "1",
	}
	params := chain.Materialize(emptyContext)
	if !reflect.DeepEqual(params, expect) {
		t.Errorf("Expected %v but got %v.", expect, params)
	}
}

func TestChildPassthrough(t *testing.T) {
	chain := &paramsChain{
		Params: &Params{},
		Parent: &paramsChain{
			Params: &Params{
				"a": "1",
			},
		},
	}
	expect := &Params{
		"a": "1",
	}
	params := chain.Materialize(emptyContext)
	if !reflect.DeepEqual(params, expect) {
		t.Errorf("Expected %v but got %v.", expect, params)
	}
}

func TestLambda(t *testing.T) {
	chain := &paramsChain{
		Params: &Params{
			"a": "\\ 1 ",
		},
	}
	expect := &Params{
		"a": "1",
	}
	params := chain.Materialize(emptyContext)
	if !reflect.DeepEqual(params, expect) {
		t.Errorf("Expected %v but go %v.", expect, params)
	}
}
