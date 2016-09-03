package ui

import "sonicjam/config"
import "sonicjam/data"

import "log"
import "net/http"
import "strconv"
import "strings"

import _ "github.com/jteeuwen/go-bindata"

func configHandler(w http.ResponseWriter, r *http.Request) {
	jsConfig, err := config.JsConfig()
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	w.Header().Set("Content-Type", "application/javascript; charset=utf-8")
	w.Write(jsConfig)
}

func jsHandler(w http.ResponseWriter, r *http.Request) {
	data, err := data.Asset("sonic-jam/resources/public/js/compiled/sonic_jam.js")
	if err != nil {
		http.NotFound(w, r)
		return
	}
	dataString := string(data)
	dataString = strings.Replace(dataString, "UI_BRIDGE_PORT", strconv.Itoa(*config.Flags.UiBridgePort), -1)
	w.Header().Set("Content-Type", "application/javascript; charset=utf-8")
	w.Write([]byte(dataString))
}

func rootHandler(w http.ResponseWriter, r *http.Request) {
	resource, err := data.Asset("sonic-jam/resources/public/index.html")
	if err != nil {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Write(resource)
}

func cssHandler(w http.ResponseWriter, r *http.Request) {
	resourceId := r.URL.Path[len("/css/"):]
	resource, err := data.Asset("sonic-jam/resources/public/css/"+resourceId)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "text/css; charset=utf-8")
	w.Write(resource)
}

func Serve(done chan bool) {
	http.HandleFunc("/", rootHandler)
	http.HandleFunc("/config/config.js", configHandler)
	http.HandleFunc("/js/compiled/sonic_jam.js", jsHandler)
	http.HandleFunc("/css/", cssHandler)
	endpoint := *config.Flags.UiIp+":"+strconv.Itoa(*config.Flags.UiPort)
	externalEndpoint := *config.Flags.UiExternalIp+":"+strconv.Itoa(*config.Flags.UiPort)
	log.Print("Starting the UI webserver on "+externalEndpoint)
	http.ListenAndServe(endpoint, nil)
}

func Shutdown() {
	log.Print("Shutting down.")
}
