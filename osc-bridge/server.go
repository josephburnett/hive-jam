package main

import "golang.org/x/net/websocket"
import "log"
import "net/http"
import "encoding/json"
import "time"
import "math/rand"
import "github.com/scgolang/osc"
import "net"

// Use a simpler data model. Just addresses and messages.

type Message struct {
	Address string
	Params  []string
}

var toServer = make(chan *Message, 10)
var toClient = make(chan *Message, 10)

var clients = make(map[string]*websocket.Conn)

var oscClient = connect("127.0.0.1", 4559)
var oscServer = listen("127.0.0.1", 4560)

func connect(address string, port int) *osc.UDPConn {
	ip := net.ParseIP(address)
	if ip == nil {
		panic("Unable to parse IP.")
	}
	addr := &net.UDPAddr{ip, port, ""}
	conn, err := osc.DialUDP("udp", nil, addr)
	if err != nil {
		panic(err)
	}
	return conn
}

func listen(address string, port int) *osc.UDPConn {
	ip := net.ParseIP(address)
	if ip == nil {
		panic("Unable to parse IP.")
	}
	addr := &net.UDPAddr{ip, port, ""}
	conn, err := osc.ListenUDP("udp", addr)
	if err != nil {
		panic(err)
	}
	return conn
}

func sendToClient() {
	for {
		msg := <-toClient
		if msg.Address == "" {
			log.Print("Dropping message. No address.")
			continue
		}
		if len(msg.Params) == 0 {
			log.Print("Dropping message. No client id.")
			continue
		}
		clientId := msg.Params[0]
		ws, ok := clients[clientId]
		if !ok {
			log.Print("Dropping message. Unknown client id: " + clientId)
			continue
		}
		msg.Params = msg.Params[1:]
		msgJson, err := json.Marshal(msg)
		if err != nil {
			log.Print(err)
			continue
		}
		if _, err = ws.Write([]byte(msgJson)); err != nil {
			log.Print(err)
			continue
		}
	}
}

func sendToServer() {
	for {
		msg := <-toServer
		if msg.Address == "" {
			log.Print("Dropping message. No address.")
			continue
		}
		oscMessage, err := osc.NewMessage(msg.Address)
		if err != nil {
			log.Print(err)
			continue
		}
		for _, p := range msg.Params {
			oscMessage.WriteString(p)
		}
		err = oscClient.Send(oscMessage)
		if err != nil {
			log.Print(err)
			continue
		}
	}
}

func websocketHandler(ws *websocket.Conn) {
	clientId := randStringRunes(16)
	clients[clientId] = ws
	for {
		msgJson := make([]byte, 512)
		n, err := ws.Read(msgJson)
		if err != nil {
			log.Print(err)
			continue
		}
		log.Print("Received from client: ", string(msgJson[:n]))
		msg := &Message{}
		err = json.Unmarshal(msgJson[:n], msg)
		if err != nil {
			log.Print(err)
			continue
		}
		params := make([]string, 1)
		params[0] = clientId
		for _, p := range msg.Params {
			params = append(params, p)
		}
		msg.Params = params
		toServer <- msg
		log.Print("Forwarded to server: ", msg)
	}
}

func oscHandler(oscMsg *osc.Message) error {
	log.Print("Received from server: ", oscMsg)
	msg := &Message{
		Address: oscMsg.Address(),
	}
	for i := 0; i < oscMsg.CountArguments(); i++ {
		param, err := oscMsg.ReadString()
		if err != nil {
			return err
		}
		msg.Params = append(msg.Params, param)
	}
	toClient <- msg
	log.Print("Forwarded to client: ", msg)
	return nil
}

func serveOSC() {
	dispatcher := make(map[string]osc.Method)
	dispatcher["/pong"] = oscHandler
	for {
		err := oscServer.Serve(dispatcher)
		if err != nil {
			panic(err)
		}
		log.Print("OSC server stopped. Restarting. I think something's wrong.")
	}
}

func main() {
	go sendToClient()
	go sendToServer()
	go serveOSC()
	http.Handle("/oscbridge", websocket.Handler(websocketHandler))
	err := http.ListenAndServe("127.0.0.1:4550", nil)
	if err != nil {
		panic("ListenAndServe: " + err.Error())
	}
}

// http://stackoverflow.com/questions/22892120

func init() {
	rand.Seed(time.Now().UnixNano())
}

var letterRunes = []rune("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")

func randStringRunes(n int) string {
	b := make([]rune, n)
	for i := range b {
		b[i] = letterRunes[rand.Intn(len(letterRunes))]
	}
	return string(b)
}
