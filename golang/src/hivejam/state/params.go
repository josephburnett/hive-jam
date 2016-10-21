package state

import (
	"math/big"

	zygo "github.com/glycerine/zygomys/repl"
)

type context struct {
	Beat *big.Rat
	Row  int64
	Col  int64
}

func (c *context) Bind(env *zygo.Glisp) {
	if c.Beat != nil {
		f, _ := c.Beat.Float64()
		env.AddGlobal("beat", &zygo.SexpFloat{Val: f})
	}
	if c.Row != 0 {
		env.AddGlobal("row", &zygo.SexpInt{Val: c.Row})
	}
	if c.Col != 0 {
		env.AddGlobal("col", &zygo.SexpInt{Val: c.Col})
	}
}

type lambda string

func (l lambda) Eval(env *zygo.Glisp) string {
	err := env.LoadString(string(l))
	if err != nil {
		env.Clear()
		return ""
	}
	expr, err := env.Run()
	if err != nil {
		env.Clear()
		return ""
	}
	return expr.SexpString(nil)
}

type paramsChain struct {
	Params *Params
	Parent *paramsChain
}

func (p *paramsChain) Materialize(c *context) *Params {
	finalizedKeys := make(map[string]bool)
	params := make(map[string]string)
	env := zygo.NewGlispSandbox()
	c.Bind(env)
	for p != nil {
		for key, value := range map[string]string(*(p.Params)) {
			if finalizedKeys[key] {
				continue
			}
			if value != "" && value[0] == '!' {
				params[key] = value[1:]
				finalizedKeys[key] = true
			} else {
				if value != "" && value[0] == '\\' {
					value = lambda(value[1:]).Eval(env)
				}
				params[key] = value
			}
		}
		p = p.Parent
	}
	materialParams := Params(params)
	return &materialParams
}
