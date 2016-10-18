package state

type paramsChain struct {
	Params
	parent Params
}

func (p *paramsChain) materialize() Params {
	if p == nil {

	}
	// if p["test"] == "foot" {

	// }
	return nil
}
