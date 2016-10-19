package state

type paramsChain struct {
	Params *Params
	Parent *paramsChain
}

func (p *paramsChain) Materialize() *Params {
	finalizedKeys := make(map[string]bool)
	params := make(map[string]string)
	for p != nil {
		for key, value := range map[string]string(*(p.Params)) {
			if finalizedKeys[key] {
				continue
			}
			if value != "" && value[0] == '!' {
				params[key] = value[1:]
				finalizedKeys[key] = true
			} else {
				params[key] = value
			}
		}
		p = p.Parent
	}
	materialParams := Params(params)
	return &materialParams
}
