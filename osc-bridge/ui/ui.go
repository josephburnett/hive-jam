package ui

import "github.com/josephburnett/sonic-jam/osc-bridge/data"
import _ "github.com/jteeuwen/go-bindata"

import "net/http"
import "log"

func jsHandler(w http.ResponseWriter, r *http.Request) {
	resource, err := data.Asset("sonic-jam/resources/public/js/compiled/sonic_jam.js")
	if err != nil {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "application/javascript; charset=utf-8")
	w.Write(resource)
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
	resource, err := data.Asset("sonic-jam/resources/public/css/style.css")
	if err != nil {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "text/css; charset=utf-8")
	w.Write(resource)
}

func Serve(done chan bool) {
	http.HandleFunc("/", rootHandler)
	http.HandleFunc("/js/compiled/sonic_jam.js", jsHandler)
	http.HandleFunc("/css/style.css", cssHandler)
	log.Print("Starting the UI webserver.")
	http.ListenAndServe(":8080", nil)
}

func Shutdown() {
	log.Print("Shutting down.")
}
