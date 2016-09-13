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
	valueOf := reflect.ValueOf(a)
	typeOf := valueOf.Type()
	kindOf := valueOf.Kind()
	if kindOf == reflect.Ptr {
		valueOf = valueOf.Elem()
		typeOf = valueOf.Type()
		kindOf = valueOf.Kind()
	}
	if !valueOf.CanSet() {
		panic(fmt.Sprintf("valueOf is not settable: %v (type: %v)", valueOf, typeOf))
	}
	switch kindOf {
	case reflect.Bool:
		nomsBool, ok := v.(types.Bool)
		if !ok {
			return typeMatchError(v, a)
		}
		valueOf.SetBool(bool(nomsBool))
		return nil
	case reflect.Float64:
		nomsNumber, ok := v.(types.Number)
		if !ok {
			return typeMatchError(v, a)
		}
		valueOf.SetFloat(float64(nomsNumber))
		return nil
	case reflect.String:
		nomsString, ok := v.(types.String)
		if !ok {
			return typeMatchError(v, a)
		}
		valueOf.SetString(string(nomsString))
		return nil
	case reflect.Slice:
		nomsList, ok := v.(types.List)
		if !ok {
			return typeMatchError(v, a)
		}
		for _, nomsValue := range nomsList.ChildValues() {
			elementType := typeOf.Elem()
			elementValue := reflect.New(elementType)
			err := DeNom(nomsValue, elementValue.Interface())
			if err != nil {
				return err
			}
			if elementValue.Kind() == reflect.Ptr {
				elementValue = elementValue.Elem()
			}
			valueOf.Set(reflect.Append(valueOf, elementValue))
		}
		return nil
	case reflect.Map:
		nomsMap, ok := v.(types.Map)
		if !ok {
			return typeMatchError(v, a)
		}
		var outterErr error = nil
		nomsMap.Iter(func(k, v types.Value) (stop bool) {
			keyType := typeOf.Key()
			keyValue := reflect.New(keyType)
			err := DeNom(k, keyValue.Interface())
			if err != nil {
				outterErr = err
				return false
			}
			valueType := typeOf.Elem()
			valueValue := reflect.New(valueType)
			if valueValue.Kind() == reflect.Ptr {
				valueValue = valueValue.Elem()
			}
			err = DeNom(v, valueValue.Interface())
			if err != nil {
				outterErr = err
				return false
			}
			valueOf.SetMapIndex(keyValue, valueValue)
			return true
		})
		return outterErr
	case reflect.Struct:
		nomsStruct, ok := v.(types.Struct)
		if !ok {
			return typeMatchError(v, a)
		}
		for i := 0; i < typeOf.NumField(); i++ {
			if valueOf.Field(i).IsValid() {
				name := types.EscapeStructField(typeOf.Field(i).Name)
				elementType := typeOf.Field(i).Type
				elementValue := reflect.New(elementType)
				nomsValue, ok := nomsStruct.MaybeGet(name)
				if ok { 
					err := DeNom(nomsValue, elementValue.Interface())
					if err != nil {
						return err
					}
					value := valueOf.Field(i)
					if elementValue.Kind() == reflect.Ptr {
						elementValue = elementValue.Elem()
					}
					value.Set(elementValue)
				}
			}
		}
		return nil
	}
	return errors.New(fmt.Sprintf("Unsupported kind: %v", kindOf))
}

func typeMatchError(v, a interface{}) error {
	vt := reflect.TypeOf(v)
	at := reflect.TypeOf(a)
	return errors.New(fmt.Sprintf("Expected Noms type compatible with %v but found %v.", at, vt))
}
