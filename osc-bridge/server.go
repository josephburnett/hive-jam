package main

import "golang.org/x/net/websocket"
import "log"
import "fmt"
import "net/http"

type Command string

const (
	GET         Command = "GET"
	PUT         Command = "PUT"
	SUBSCRIBE   Command = "SUBSCRIBE"
	UNSUBSCRIBE Command = "UNSUBSCRIBE"
)

type Outcome string

const (
	ACK  Outcome = "ACK"
	NACK Outcome = "NACK"
)

type GridId string

type ComponentId string

type Grid string

type Error string

type Request struct {
	Command     Command
	ComponentId ComponentId
	GridId      GridId
	Grid        Grid
}

type Response struct {
	Outcome     Outcome
	Command     Command
	ComponentId ComponentId
	Gridid      GridId
	Grid        Grid
	Error       Error
}

func echoHandler(ws *websocket.Conn) {
	msg := make([]byte, 512)
	n, err := ws.Read(msg)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Receive: %s\n", msg[:n])

	m, err := ws.Write(msg[:n])
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Send: %s\n", msg[:m])
}

func main() {
	http.Handle("/grid", websocket.Handler(echoHandler))
	err := http.ListenAndServe("127.0.0.1:4550", nil)
	if err != nil {
		panic("ListenAndServe: " + err.Error())
	}
}
