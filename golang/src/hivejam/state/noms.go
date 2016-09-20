package state

import (
	"hivejam/config"

	"errors"

	"github.com/attic-labs/noms/go/marshal"
	"github.com/attic-labs/noms/go/spec"
	noms "github.com/attic-labs/noms/go/types"
)

func NomsCommit(grid Grid) error {
	ds, err := spec.GetDataset(*config.Flags.NomsDataset)
	if err != nil {
		return err
	}
	defer ds.Database().Close()

	var headValue noms.Value
	headValue, ok := ds.MaybeHeadValue()
	if !ok {
		headValue, err = marshal.Marshal(make(map[string]Grid))
		if err != nil {
			return err
		}
	}
	data, ok := headValue.(noms.Struct)
	if !ok {
		return errors.New("Head is not a struct")
	}

	nomsGrid, err := marshal.Marshal(grid)
	if err != nil {
		return err
	}
	data.Set(grid.Id, nomsGrid)

	_, err = ds.CommitValue(data)
	if err != nil {
		return err
	}
	return nil
}

func NomsReset() (map[string]Grid, error) {
	ds, err := spec.GetDataset(*config.Flags.NomsDataset)
	if err != nil {
		return nil, err
	}
	defer ds.Database().Close()

	if headValue, ok := ds.MaybeHeadValue(); !ok {
		return nil, errors.New("Head is empty")
	} else {
		grids := make(map[string]Grid)
		err := marshal.Unmarshal(headValue, &grids)
		if err != nil {
			return nil, err
		}
		return grids, nil
	}
}
