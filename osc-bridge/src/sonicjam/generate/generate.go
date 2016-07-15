package main

import "sonicjam/config"

import "flag"
import "io/ioutil"

var jsOutputFile = flag.String("js_output_file", "", "output file for JS config")

func main() {
	flag.Parse()
	jsConfig, err := config.JsConfig()
	if err != nil {
		panic(err)
	}
	err = ioutil.WriteFile(*jsOutputFile, jsConfig, 0644)
	if err != nil {
		panic(err)
	}
}

