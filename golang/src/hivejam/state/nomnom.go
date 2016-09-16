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
	if k == reflect.Ptr {
		v = v.Elem()
		k = v.Kind()
		t = v.Type()
	}
	fmt.Printf("NomNom %v (%v)\n", k, t)
	switch k {
	case reflect.Bool:
		return types.Bool(v.Bool()), nil
	case reflect.Int32:
		return types.Number(v.Int()), nil
	case reflect.Float64:
		return types.Number(v.Float()), nil
	case reflect.String:
		return types.String(v.String()), nil
	case reflect.Slice:
		nomsValues := make([]types.Value, v.Len())
		for i := 0; i < v.Len(); i++ {
			nomsValue, err := NomNom(v.Index(i).Interface())
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
		return types.NewMap(nomsKeyValues...), nil
	case reflect.Struct:
		nomsFields := make(types.StructData, v.NumField())
		for i := 0; i < v.NumField(); i++ {
			nomsKey := types.EscapeStructField(t.Field(i).Name)
			fmt.Printf("Saving with nomsKey: %v\n", nomsKey)
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
	return nil, errors.New(fmt.Sprintf("Unsupported kind (1): %v", k))
}

func DeNom(v types.Value, a interface{}) error {
	valueOf := reflect.ValueOf(a)
	typeOf := valueOf.Type()
	kindOf := valueOf.Kind()
	// if !valueOf.CanSet() {
	// 	panic(fmt.Sprintf("valueOf is not settable (1): %v (type: %v)", valueOf, typeOf))
	// }
	if kindOf == reflect.Ptr {
		valueOf = valueOf.Elem()
		typeOf = valueOf.Type()
		kindOf = valueOf.Kind()
	}
	fmt.Printf("Denom %v (%v)\n", kindOf, typeOf)
	if !valueOf.CanSet() {
		panic(fmt.Sprintf("valueOf is not settable (2): %v (type: %v)", valueOf, typeOf))
	}
	switch kindOf {
	case reflect.Bool:
		nomsBool, ok := v.(types.Bool)
		if !ok {
			return typeMatchError(v, a)
		}
		valueOf.SetBool(bool(nomsBool))
		return nil
	case reflect.Int32:
		nomsNumber, ok := v.(types.Number)
		if !ok {
			return typeMatchError(v, a)
		}
		valueOf.SetInt(int64(nomsNumber))
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
		for i, nomsValue := range nomsList.ChildValues() {
			elementType := typeOf.Elem()
			elementValue := reflect.New(elementType)
			// if elementValue.Kind() == reflect.Ptr {
			// 	elementValue = elementValue.Elem()
			// }
			fmt.Printf("elementValue.Kind(): %v\n", elementValue.Kind())
			// if !elementValue.CanSet() {
			// 	panic("About to recurse on an unsettable value")
			// } else {
			// 	fmt.Printf("Recursing on a settable value\n")
			// }
			err := DeNom(nomsValue, elementValue.Interface())
			if err != nil {
				return err
			}
			if !valueOf.CanSet() {
				panic("I won't be able to set a value on this slice")
			}
			fmt.Printf("Setting slice element %v\n", i)
			valueOf.Set(reflect.Append(valueOf, elementValue.Elem()))
		}
		return nil
	case reflect.Map:
		nomsMap, ok := v.(types.Map)
		if !ok {
			return typeMatchError(v, a)
		}
		var outterErr error = nil
		mapValue := reflect.MakeMap(typeOf)
		nomsMap.Iter(func(k, v types.Value) (stop bool) {
			fmt.Printf("My callback just got k: %v and v: %v\n", k.Type(), v.Type())
			keyType := typeOf.Key()
			keyValue := reflect.New(keyType)
			// if !keyValue.CanSet() {
			// 	panic("keyValue is not settable")
			// }
			fmt.Printf("Made a new %v (%v) as a key\n", keyValue.Kind(), keyValue.Type())
			fmt.Printf("Recursing on map key\n")
			err := DeNom(k, keyValue.Interface())
			if err != nil {
				outterErr = err
				return true
			}
			valueType := typeOf.Elem()
			valueValue := reflect.New(valueType)
			fmt.Printf("valueValue before: %v\n", valueValue.Elem().Interface())
			// if !valueValue.CanSet() {
			// 	panic("valueValue is not settable")
			// }
			fmt.Printf("Made a new %v (%v) as a value\n", valueValue.Kind(), valueValue.Type())
			fmt.Printf("Recursing on map value\n")
			err = DeNom(v, valueValue.Interface())
			if err != nil {
				outterErr = err
				return true
			}
			fmt.Printf("valueValue after: %v\n", valueValue.Elem().Interface())
			mapValue.SetMapIndex(keyValue.Elem(), valueValue.Elem())
			fmt.Printf("Successfully set something in a map!\n")
			return false
		})
		valueOf.Set(mapValue)
		return outterErr
	case reflect.Struct:
		nomsStruct, ok := v.(types.Struct)
		if !ok {
			return typeMatchError(v, a)
		}
		for i := 0; i < typeOf.NumField(); i++ {
			fmt.Printf("Field: %v\n", typeOf.Field(i).Name)
			if valueOf.Field(i).IsValid() {
				fmt.Printf("Field is valid\n")
				name := types.EscapeStructField(typeOf.Field(i).Name)
				elementType := typeOf.Field(i).Type
				elementValue := reflect.New(elementType)
				nomsValue, ok := nomsStruct.MaybeGet(name)
				if ok {
					fmt.Printf("Setting struct field %v\n", name)
					err := DeNom(nomsValue, elementValue.Interface())
					if err != nil {
						return err
					}
					value := valueOf.Field(i)
					if elementValue.Kind() == reflect.Ptr {
						elementValue = elementValue.Elem()
					}
					value.Set(elementValue)
				} else {
					fmt.Printf("Did not find in struct .. say what?\n")
				}
			} else {
				fmt.Printf("Field is not valid\n")
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
