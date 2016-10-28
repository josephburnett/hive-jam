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
	checkChain(t, chain, chain.Params)
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
	checkChain(t, chain, expect)
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
	checkChain(t, chain, expect)
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
	checkChain(t, chain, expect)
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
	checkChain(t, chain, expect)
}

func TestLambdaLiteral(t *testing.T) {
	chain := &paramsChain{
		Params: &Params{
			"a": "\\ 1 ",
		},
	}
	expect := &Params{
		"a": "1",
	}
	checkChain(t, chain, expect)
}

func TestLambdaInheritance(t *testing.T) {
	chain := &paramsChain{
		Params: &Params{
			"a": "2",
		},
		Parent: &paramsChain{
			Params: &Params{
				"a": "\\ (* 2 val)",
			},
		},
	}
	expect := &Params{
		"a": "4",
	}
	checkChain(t, chain, expect)
}

func checkChain(t *testing.T, chain *paramsChain, expect *Params) {
	params, err := chain.Materialize(emptyContext)
	if err != nil {
		t.Errorf(err.Error())
	}
	if !reflect.DeepEqual(params, expect) {
		t.Errorf("Materialize %v is %v. Want %v.", chain, params, expect)
	}
}
