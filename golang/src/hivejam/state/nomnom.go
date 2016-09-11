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
	// TODO: encode integers are strings because Noms Number type is float64
	// case reflect.Int:
	// 	return types.Number(v.Int()), nil
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

func DeNom(v types.Value, a interface{}) error {
	t := reflect.TypeOf(a)
	vr := reflect.ValueOf(&a)
	k := t.Kind()
	switch k {
	case reflect.Bool:
		nomsBool, ok := v.(types.Bool)
		if !ok {
			return typeMatchError(v, a)
		}
		vr.Elem().SetBool(bool(nomsBool))
	case reflect.Float64:
		nomsNumber, ok := v.(types.Number)
		if !ok {
			return typeMatchError(v, a)
		}
		vr.Elem().SetFloat(float64(nomsNumber))
	case reflect.String:
		nomsString, ok := v.(types.String)
		if !ok {
			return typeMatchError(v, a)
		}
		vr.Elem().SetString(string(nomsString))
	case reflect.Slice:
		nomsList, ok := v.(types.List)
		if !ok {
			return typeMatchError(v, a)
		}
		for _, nomsValue := range nomsList.ChildValues() {
			elementType := reflect.TypeOf(vr.Elem()).Elem()
			elementValue := reflect.New(elementType)
			err := DeNom(nomsValue, elementValue)
			if err != nil {
				return err
			}
			vr.Elem().Set(reflect.Append(vr.Elem(), elementValue))
		}
	case reflect.Map:
		nomsMap, ok := v.(types.Map)
		if !ok {
			return typeMatchError(v, a)
		}
		outterErr := error(nil)
		nomsMap.Iter(func(k, v types.Value) (stop bool) {
			keyType := reflect.TypeOf(vr.Elem()).Key()
			keyValue := reflect.New(keyType)
			err := DeNom(k, keyValue)
			if err != nil {
				outterErr = err
				return false
			}
			valueType := reflect.TypeOf(vr.Elem()).Elem()
			valueValue := reflect.New(valueType)
			err = DeNom(v, valueValue)
			if err != nil {
				outterErr = err
				return false
			}
			vr.Elem().SetMapIndex(keyValue, valueValue)
			return true
		})
		return outterErr
	case reflect.Struct:
		nomsStruct, ok := v.(types.Struct)
		if !ok {
			return typeMatchError(v, a)
		}
		for i := 0; i < reflect.TypeOf(vr.Elem()).NumField(); i++ {
			if reflect.ValueOf(vr.Elem()).Field(i).CanInterface() {
				name := types.EscapeStructField(t.Field(i).Name)
				elementType := reflect.TypeOf(vr.Elem()).Elem()
				elementValue := reflect.New(elementType)
				nomsValue := nomsStruct.Get(name)
				err := DeNom(nomsValue, elementValue)
				if err != nil {
					return err
				}
				value := vr.Elem().Field(i)
				value.Set(elementValue)
			}
		}
		return nil
	}
	return errors.New(fmt.Sprintf("Unsupported kind: %v", k))
}

func typeMatchError(v, a interface{}) error {
	vt := reflect.TypeOf(v)
	at := reflect.TypeOf(a)
	return errors.New(fmt.Sprintf("Expected Noms type compatible with %v but found %v.", at, vt))
}
