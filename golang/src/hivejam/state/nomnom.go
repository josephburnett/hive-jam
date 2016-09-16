package state

import (
	"errors"
	"fmt"
	"reflect"

	noms "github.com/attic-labs/noms/go/types"
)

func NomNom(a interface{}) (noms.Value, error) {
	t := reflect.TypeOf(a)
	v := reflect.ValueOf(a)
	k := t.Kind()
	if k == reflect.Ptr {
		v = v.Elem()
		k = v.Kind()
		t = v.Type()
	}
	switch k {
	case reflect.Bool:
		return noms.Bool(v.Bool()), nil
	case reflect.Int32:
		return noms.Number(v.Int()), nil
	case reflect.Float64:
		return noms.Number(v.Float()), nil
	case reflect.String:
		return noms.String(v.String()), nil
	case reflect.Slice:
		nomsValues := make([]noms.Value, v.Len())
		for i := 0; i < v.Len(); i++ {
			nomsValue, err := NomNom(v.Index(i).Interface())
			if err != nil {
				return nil, err
			}
			nomsValues[i] = nomsValue
		}
		return noms.NewList(nomsValues...), nil
	case reflect.Map:
		nomsKeyValues := make([]noms.Value, 0, v.Len()*2)
		ks := v.MapKeys()
		for _, key := range ks {
			nomsKey, err := NomNom(key.Interface())
			if err != nil {
				return nil, err
			}
			nomsValue, err := NomNom(v.MapIndex(key).Interface())
			if err != nil {
				return nil, err
			}
			nomsKeyValues = append(nomsKeyValues, nomsKey, nomsValue)
		}
		return noms.NewMap(nomsKeyValues...), nil
	case reflect.Struct:
		nomsFields := make(noms.StructData, v.NumField())
		for i := 0; i < v.NumField(); i++ {
			nomsKey := noms.EscapeStructField(t.Field(i).Name)
			if v.Field(i).CanInterface() {
				nomsValue, err := NomNom(v.Field(i).Interface())
				if err != nil {
					return nil, err
				}
				nomsFields[nomsKey] = nomsValue
			}
		}
		name := noms.EscapeStructField(t.PkgPath() + "." + t.Name())
		return noms.NewStruct(name, nomsFields), nil
	}
	return nil, errors.New(fmt.Sprintf("Unsupported kind (1): %v", k))
}

func DeNom(v noms.Value, a interface{}) error {
	valueOf := reflect.ValueOf(a)
	typeOf := valueOf.Type()
	kindOf := valueOf.Kind()
	if kindOf == reflect.Ptr {
		valueOf = valueOf.Elem()
		typeOf = valueOf.Type()
		kindOf = valueOf.Kind()
	}
	switch kindOf {
	case reflect.Bool:
		nomsBool, ok := v.(noms.Bool)
		if !ok {
			return typeMatchError(v, a)
		}
		valueOf.SetBool(bool(nomsBool))
		return nil
	case reflect.Int32:
		nomsNumber, ok := v.(noms.Number)
		if !ok {
			return typeMatchError(v, a)
		}
		valueOf.SetInt(int64(nomsNumber))
		return nil
	case reflect.Float64:
		nomsNumber, ok := v.(noms.Number)
		if !ok {
			return typeMatchError(v, a)
		}
		valueOf.SetFloat(float64(nomsNumber))
		return nil
	case reflect.String:
		nomsString, ok := v.(noms.String)
		if !ok {
			return typeMatchError(v, a)
		}
		valueOf.SetString(string(nomsString))
		return nil
	case reflect.Slice:
		nomsList, ok := v.(noms.List)
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
			valueOf.Set(reflect.Append(valueOf, elementValue.Elem()))
		}
		return nil
	case reflect.Map:
		nomsMap, ok := v.(noms.Map)
		if !ok {
			return typeMatchError(v, a)
		}
		var outterErr error = nil
		mapValue := reflect.MakeMap(typeOf)
		nomsMap.Iter(func(k, v noms.Value) (stop bool) {
			keyType := typeOf.Key()
			keyValue := reflect.New(keyType)
			err := DeNom(k, keyValue.Interface())
			if err != nil {
				outterErr = err
				return true
			}
			valueType := typeOf.Elem()
			valueValue := reflect.New(valueType)
			err = DeNom(v, valueValue.Interface())
			if err != nil {
				outterErr = err
				return true
			}
			mapValue.SetMapIndex(keyValue.Elem(), valueValue.Elem())
			return false
		})
		if mapValue.Len() > 0 {
			valueOf.Set(mapValue)
		}
		return outterErr
	case reflect.Struct:
		nomsStruct, ok := v.(noms.Struct)
		if !ok {
			return typeMatchError(v, a)
		}
		for i := 0; i < typeOf.NumField(); i++ {
			if valueOf.Field(i).IsValid() {
				name := noms.EscapeStructField(typeOf.Field(i).Name)
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
	return errors.New(fmt.Sprintf("Unsupported kind (2): %v", kindOf))
}

func typeMatchError(v, a interface{}) error {
	vt := reflect.TypeOf(v)
	at := reflect.TypeOf(a)
	return errors.New(fmt.Sprintf("Expected Noms type compatible with %v but found %v.", at, vt))
}
