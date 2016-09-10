package state

import (
	"errors"
	"fmt"
	"reflect"
	
	"github.com/attic-labs/noms/go/types"
)

func NomNom(a interface{}) (types.Value, error) {
	t := reflect.TypeOf(a)
	v := reflect.ValueOf(a)
	k := t.Kind()
	switch k {
	case reflect.Bool:
		return types.Bool(v.Bool()), nil
	case reflect.Int:
		return types.Number(v.Int()), nil
	case reflect.Float64:
		return types.Number(v.Float()), nil
	case reflect.String:
		return types.String(v.String()), nil
	case reflect.Slice:
		nomsValues := make([]types.Value, v.Len())
		for i := 0; i < v.Len(); i++ {
			nomsValue, err := NomNom(v.Index(i))
			if err != nil {
				return nil, err
			}
			nomsValues[i] = nomsValue
		}
		return types.NewList(nomsValues...), nil
	case reflect.Map:
		nomsKeyValues := make([]types.Value, 0, v.Len()*2)
		ks := v.MapKeys()
		for _, key := range ks {
			nomsKey, err := NomNom(key)
			if err != nil {
				return nil, err
			}
			nomsValue, err := NomNom(v.MapIndex(key))
			if err != nil {
				return nil, err
			}
			nomsKeyValues = append(nomsKeyValues, nomsKey, nomsValue)
		}
		return types.NewMap(nomsKeyValues...), nil
	case reflect.Struct:
		nomsFields := make(types.StructData, v.NumField())
		for i := 0; i < v.NumField(); i++ {
			nomsKey := types.EscapeStructField(t.Field(i).Name)
			fmt.Printf("Field: %v\n", t.Field(i))
			if v.Field(i).CanInterface() {
				nomsValue, err := NomNom(v.Field(i).Interface())
				if err != nil {
					return nil, err
				}
				nomsFields[nomsKey] = nomsValue
			}
		}
		name := types.EscapeStructField(t.PkgPath() + "." + t.Name())
		return types.NewStruct(name, nomsFields), nil
	}
	return nil, errors.New(fmt.Sprintf("Unsupported kind: %v", k))
}
