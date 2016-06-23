package bridge

import "golang.org/x/net/websocket"
import "log"
import "net/http"
import "encoding/json"
import "time"
import "math/rand"
import "github.com/scgolang/osc"
import "io"
import "github.com/josephburnett/sonic-jam/osc-bridge/common"

type Params []string

type Message struct {
	Address string
	Params  Params
}

func Serve(done chan bool) {
	go sendToClient()
	go sendToServer()
	log.Print("Starting OSC server.")
	go serveOSC()
	log.Print("Starting websocket server.")
	http.Handle("/oscbridge", websocket.Handler(websocketHandler))
	err := http.ListenAndServe("127.0.0.1:4550", nil)
	if err != nil {
		panic("ListenAndServe: " + err.Error())
	}
	done <- true
}

func Shutdown() {
	log.Print("Shutting down OSC bridge.")
}

func (m *Message) Clone() *Message {
	p := make(Params, len(m.Params))
	for i,v := range m.Params {
		p[i] = v
	}
	return &Message{
		Address: m.Address,
		Params: p,
	}
}

var toServer = make(chan *Message, 10)
var toClient = make(chan *Message, 10)

var clients = make(map[string]*websocket.Conn)

var oscClient = common.Connect("127.0.0.1", 4559)
var oscServer = common.Listen("127.0.0.1", 4560)


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
			log.Print("Error marshalling message to client.")
			log.Print(err)
			continue
		}
		if _, err = ws.Write([]byte(msgJson)); err != nil {
			log.Print("Error writing message to client websocket.")
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
			log.Print("Error create new OSC message to server.")
			log.Print(err)
			continue
		}
		for _, p := range msg.Params {
			oscMessage.WriteString(p)
		}
		err = oscClient.Send(oscMessage)
		if err != nil {
			log.Print("Error sending OSC message to server.")
			log.Print(err)
			continue
		}
	}
}

func websocketHandler(ws *websocket.Conn) {
	clientId := randStringRunes(16)
	log.Print("Connected client: " + clientId)
	clients[clientId] = ws
	for {
		msgJson := make([]byte, 2000)
		n, err := ws.Read(msgJson)
		if err != nil {
			if err == io.EOF {
				log.Print("Disconnected client: " + clientId)
				delete(clients, clientId)
				return
			}
			log.Print("Error reading from client websocket.")
			log.Print(err)
			continue
		}
		msg := &Message{}
		err = json.Unmarshal(msgJson[:n], msg)
		if err != nil {
			log.Print("Error unmarshalling message from client.")
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
	}
}

func oscHandler(oscMsg *osc.Message) error {
	msg := &Message{
		Address: oscMsg.Address(),
	}
	for i := 0; i < oscMsg.CountArguments(); i++ {
		paramsJson, err := oscMsg.ReadString()
		if err != nil {
			log.Print(err)
			return err
		}
		params := &Params{}
		err = json.Unmarshal([]byte(paramsJson), params)
		if err != nil {
			log.Print(err)
			return err
		}
		for _, param := range *params {
			msg.Params = append(msg.Params, param)
		}
	}
	if msg.Params[0] == "*" {
		for key := range clients {
			m := msg.Clone()
			m.Params[0] = key
			toClient <- m
		}
	} else {
		toClient <- msg
	}
	return nil
}

func serveOSC() {
	dispatcher := make(map[string]osc.Method)
	dispatcher["/pong"] = oscHandler
	dispatcher["/state"] = oscHandler
	dispatcher["/samples"] = oscHandler
	dispatcher["/synths"] = oscHandler
	dispatcher["/errors"] = oscHandler
	dispatcher["/cursors"] = oscHandler
	dispatcher["/console"] = oscHandler
	for {
		err := oscServer.Serve(dispatcher)
		if err != nil {
			panic(err)
		}
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
